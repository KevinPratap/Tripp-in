import { IsEmail, MaxLength } from 'class-validator';

/** Body for POST /api/v1/auth/request-link */
export class RequestMagicLinkDto {
  @IsEmail({}, { message: 'A valid email address is required.' })
  @MaxLength(254)
  email!: string;
}
