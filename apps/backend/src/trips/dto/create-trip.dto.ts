import { Type } from 'class-transformer';
import {
  IsArray,
  IsInt,
  IsISO8601,
  IsNumber,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  MinLength
} from 'class-validator';
import { CreateTripRequestDto as CreateTripRequest } from '@trippin/api-contracts';

/**
 * Declared as a class with decorators rather than relying on the shared interface,
 * because the global ValidationPipe only validates classes. Without this, an
 * incomplete body reached Prisma and the API answered 500 instead of 400.
 */
export class CreateTripRequestDto implements CreateTripRequest {
  @IsString()
  @MinLength(2)
  @MaxLength(120)
  destination!: string;

  @IsISO8601()
  startDate!: string;

  @IsISO8601()
  endDate!: string;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(24)
  travelersCount!: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(0)
  @Max(1000000)
  budgetTotal?: number;

  @IsOptional()
  @IsString()
  @MaxLength(8)
  currency?: string;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  travelStyles?: string[];

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  interests?: string[];

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  foodPreferences?: string[];

  @IsOptional()
  @IsString()
  @MaxLength(40)
  transportPreference?: string;

  @IsOptional()
  @IsString()
  @MaxLength(20)
  pace?: string;

  @IsOptional()
  @IsString()
  @MaxLength(600)
  notes?: string;
}
