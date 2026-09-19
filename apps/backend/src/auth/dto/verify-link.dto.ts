import { IsEmail, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

/**
 * Body for POST /api/v1/auth/verify.
 *
 * guestSessionId is the X-Guest-Session value the browser was already using. When it
 * is supplied, every trip created under that guest identity is handed to the account
 * that just signed in, so nothing planned before signing up is lost.
 */
export class VerifyMagicLinkDto {
  @IsString()
  @MinLength(16, { message: 'That link is not valid. Request a new one.' })
  @MaxLength(512)
  token!: string;

  @IsOptional()
  @IsEmail({}, { message: 'If you send an email it must be a valid address.' })
  @MaxLength(254)
  email?: string;

  @IsOptional()
  @IsString()
  @MaxLength(64, { message: 'Guest session ids are at most 64 characters.' })
  guestSessionId?: string;
}
