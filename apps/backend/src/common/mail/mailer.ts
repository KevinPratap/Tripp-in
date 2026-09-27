import { Injectable, Logger } from '@nestjs/common';

/**
 * Outbound email, behind one interface so the transport is a deployment choice rather than something
 * the auth code knows about.
 *
 * Two implementations: [ResendMailer] when a key is configured, [ConsoleMailer] otherwise. The auth
 * service asks the mailer whether it can actually deliver ([Mailer.canDeliver]) and tells the caller
 * the truth either way, instead of saying "check your email" when nothing was sent.
 */

export interface MailMessage {
  to: string;
  subject: string;
  /** Plain text body. Every message sends text, so a client that refuses HTML still reads it. */
  text: string;
  html?: string;
}

export abstract class Mailer {
  /** True when this transport reaches a real inbox. False for the console fallback. */
  abstract readonly canDeliver: boolean;
  /** A short name for logs and for the delivery field on an API response. */
  abstract readonly name: 'resend' | 'console';
  /** Throws when the message could not be handed to the provider. */
  abstract send(message: MailMessage): Promise<void>;
}

/**
 * The development fallback: writes the message to the log.
 *
 * It reports `canDeliver: false` so nothing built on top of it ever claims an email was sent.
 */
@Injectable()
export class ConsoleMailer extends Mailer {
  readonly canDeliver = false;
  readonly name = 'console' as const;
  private readonly logger = new Logger(ConsoleMailer.name);

  async send(message: MailMessage): Promise<void> {
    this.logger.log(
      `No mail provider configured. Message for ${message.to} not sent. Subject: ${message.subject}\n${message.text}`
    );
  }
}

/**
 * Resend (resend.com), on its free tier.
 *
 * The key never leaves this class and is never logged. A failure throws, so the caller can tell the
 * traveller to try again rather than leaving them waiting for mail that is not coming.
 */
@Injectable()
export class ResendMailer extends Mailer {
  readonly canDeliver = true;
  readonly name = 'resend' as const;
  private readonly logger = new Logger(ResendMailer.name);

  constructor(
    private readonly apiKey: string,
    private readonly from: string
  ) {
    super();
  }

  async send(message: MailMessage): Promise<void> {
    const res = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${this.apiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        from: this.from,
        to: [message.to],
        subject: message.subject,
        text: message.text,
        ...(message.html ? { html: message.html } : {})
      }),
      signal: AbortSignal.timeout(10000)
    });

    if (!res.ok) {
      // Resend puts a reason in the body. It is read for the log but kept out of the thrown message,
      // which reaches the traveller.
      const detail = await res.text().catch(() => '');
      this.logger.error(`Resend refused the message for ${message.to}: HTTP ${res.status} ${detail.slice(0, 300)}`);
      throw new Error(`Mail provider answered HTTP ${res.status}`);
    }
  }
}

/**
 * Picks the transport from the environment.
 *
 * RESEND_API_KEY and MAIL_FROM together turn on real delivery. Either one missing falls back to the
 * console, because a half configured mailer that silently drops sign in links is worse than one that
 * says out loud it is not delivering.
 */
export function createMailer(env: NodeJS.ProcessEnv = process.env): Mailer {
  const apiKey = env.RESEND_API_KEY?.trim();
  const from = env.MAIL_FROM?.trim();

  if (apiKey && from) {
    return new ResendMailer(apiKey, from);
  }

  const logger = new Logger('createMailer');
  if (apiKey && !from) {
    logger.warn('RESEND_API_KEY is set but MAIL_FROM is not, so email is not being sent.');
  }
  return new ConsoleMailer();
}
