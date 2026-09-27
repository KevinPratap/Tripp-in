import { CanActivate, ExecutionContext, Injectable, Logger } from '@nestjs/common';
import { Reflector } from '@nestjs/core';

export const RATE_LIMIT_KEY = 'rate_limit';

export interface RateLimitOptions {
  /** Requests allowed inside the window. */
  limit: number;
  /** Window length in milliseconds. */
  windowMs: number;
}

/**
 * Mark a route (or controller) with a tighter budget. AI backed routes cost
 * real money per call, so they get a much smaller allowance than reads.
 */
export const RateLimit = (options: RateLimitOptions) =>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (target: any, key?: string, descriptor?: any) => {
    if (descriptor) {
      Reflect.defineMetadata(RATE_LIMIT_KEY, options, descriptor.value);
    } else {
      Reflect.defineMetadata(RATE_LIMIT_KEY, options, target);
    }
    return descriptor || target;
  };

interface Bucket {
  hits: number[];
}

/**
 * Minimal in-process sliding window limiter.
 *
 * Deliberately dependency free: the deployed image installs with a frozen style
 * install on a Windows-created lockfile, and a guard is cheaper than another
 * package to keep in sync. Railway runs a single instance, so per-process state
 * is the same as per-deployment state.
 */
@Injectable()
export class RateLimitGuard implements CanActivate {
  private readonly logger = new Logger(RateLimitGuard.name);
  private readonly buckets = new Map<string, Bucket>();
  private readonly defaultLimit = Number(process.env.RATE_LIMIT_PER_MINUTE || 60);
  private readonly defaultWindowMs = Number(process.env.RATE_LIMIT_WINDOW_MS || 60000);
  private lastSweep = Date.now();

  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest();
    const response = context.switchToHttp().getResponse();

    const routeOptions =
      this.reflector.get<RateLimitOptions>(RATE_LIMIT_KEY, context.getHandler()) ||
      this.reflector.get<RateLimitOptions>(RATE_LIMIT_KEY, context.getClass()) ||
      undefined;

    const options: RateLimitOptions = routeOptions || {
      limit: this.defaultLimit,
      windowMs: this.defaultWindowMs
    };

    // Health checks must never be throttled: Railway uses them for readiness.
    const path: string = request.route?.path || request.url || '';
    if (path.includes('/health')) return true;

    const identifier = this.clientKey(request);
    const bucketKey = `${identifier}:${context.getClass().name}.${context.getHandler().name}`;
    const now = Date.now();
    const bucket = this.buckets.get(bucketKey) || { hits: [] };
    bucket.hits = bucket.hits.filter((t) => now - t < options.windowMs);

    if (bucket.hits.length >= options.limit) {
      const retryAfterSeconds = Math.max(
        1,
        Math.ceil((options.windowMs - (now - bucket.hits[0])) / 1000)
      );
      this.buckets.set(bucketKey, bucket);
      response?.setHeader?.('Retry-After', String(retryAfterSeconds));
      this.logger.warn(
        `Rate limit hit for ${identifier} on ${bucketKey} (${bucket.hits.length}/${options.limit})`
      );
      response?.status?.(429);
      response?.json?.({
        statusCode: 429,
        error: 'Too Many Requests',
        message: `Rate limit exceeded. Try again in ${retryAfterSeconds} second(s).`
      });
      return false;
    }

    bucket.hits.push(now);
    this.buckets.set(bucketKey, bucket);
    this.sweep(now);
    return true;
  }

  private clientKey(request: any): string {
    const forwarded = String(request.headers?.['x-forwarded-for'] || '')
      .split(',')[0]
      .trim();
    return forwarded || request.ip || request.socket?.remoteAddress || 'unknown';
  }

  /** Housekeeping so the map cannot grow without bound. */
  private sweep(now: number): void {
    if (now - this.lastSweep < 300000) return;
    this.lastSweep = now;
    const cutoff = now - this.defaultWindowMs * 10;
    for (const [key, bucket] of this.buckets.entries()) {
      bucket.hits = bucket.hits.filter((t) => t > cutoff);
      if (bucket.hits.length === 0) this.buckets.delete(key);
    }
  }
}
