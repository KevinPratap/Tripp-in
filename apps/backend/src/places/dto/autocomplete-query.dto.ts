import { Type } from 'class-transformer';
import { IsInt, IsOptional, IsString, Max, MaxLength, Min, MinLength } from 'class-validator';

/**
 * Query for destination autocomplete.
 *
 * Declared as a class with decorators because the global ValidationPipe runs with whitelist: true,
 * which strips every property of an undecorated class and leaves the handler with an empty object.
 * Registered in common/validation/dto-decorators.spec.ts so that cannot regress silently.
 */
export class AutocompleteQueryDto {
  /**
   * What the traveller has typed. Two characters is the floor: one character matches most of the
   * planet and wastes a request on a result nobody can use.
   */
  @IsString()
  @MinLength(2)
  @MaxLength(120)
  q!: string;

  /** How many suggestions to return. Bounded so one caller cannot ask Photon for a thousand rows. */
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(10)
  limit?: number;
}
