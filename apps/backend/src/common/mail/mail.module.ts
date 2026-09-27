import { Global, Module } from '@nestjs/common';
import { Mailer, createMailer } from './mailer';

/**
 * Global so any feature can send mail without re-declaring the provider. The transport is chosen once
 * at startup from the environment; see createMailer.
 */
@Global()
@Module({
  providers: [{ provide: Mailer, useFactory: () => createMailer() }],
  exports: [Mailer]
})
export class MailModule {}
