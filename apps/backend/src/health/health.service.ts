import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';

export interface HealthCheckResult {
  status: 'ok' | 'degraded' | 'error';
  timestamp: string;
  uptimeSeconds: number;
  environment: string;
  services: {
    database: {
      status: 'up' | 'down' | 'unreachable';
      latencyMs?: number;
      message?: string;
    };
    redis: {
      status: 'up' | 'down' | 'offline_fallback';
      latencyMs?: number;
    };
  };
  system: {
    nodeVersion: string;
    memoryUsageMB: number;
  };
}

@Injectable()
export class HealthService {
  private readonly logger = new Logger(HealthService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly redis: RedisService
  ) {}

  async checkHealth(): Promise<HealthCheckResult> {
    const startDb = Date.now();
    let dbStatus: 'up' | 'down' | 'unreachable' = 'down';
    let dbLatency: number | undefined;
    let dbMessage: string | undefined;

    try {
      await this.prisma.$queryRaw`SELECT 1`;
      dbStatus = 'up';
      dbLatency = Date.now() - startDb;
    } catch (err: any) {
      dbStatus = 'unreachable';
      dbMessage = err.message || 'Database connection offline';
    }

    const startRedis = Date.now();
    let redisStatus: 'up' | 'down' | 'offline_fallback' = 'offline_fallback';
    let redisLatency: number | undefined;

    try {
      const client = this.redis.getClient();
      if (client && client.status === 'ready') {
        await client.ping();
        redisStatus = 'up';
        redisLatency = Date.now() - startRedis;
      }
    } catch {
      redisStatus = 'offline_fallback';
    }

    const overallStatus: 'ok' | 'degraded' | 'error' =
      dbStatus === 'up' && redisStatus === 'up'
        ? 'ok'
        : dbStatus === 'up' || redisStatus === 'up'
          ? 'degraded'
          : 'error';

    return {
      status: overallStatus,
      timestamp: new Date().toISOString(),
      uptimeSeconds: Math.floor(process.uptime()),
      environment: process.env.NODE_ENV || 'development',
      services: {
        database: {
          status: dbStatus,
          latencyMs: dbLatency,
          message: dbMessage
        },
        redis: {
          status: redisStatus,
          latencyMs: redisLatency
        }
      },
      system: {
        nodeVersion: process.version,
        memoryUsageMB: Math.round(process.memoryUsage().rss / (1024 * 1024))
      }
    };
  }
}
