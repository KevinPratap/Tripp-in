import { IsString, MinLength, IsOptional, IsNumber, IsArray, IsIn } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { TravellerPace } from '@trippin/shared-types';

export const VALID_INTERESTS = [
  'culture',
  'food',
  'nightlife',
  'nature',
  'adventure',
  'shopping',
  'museums',
  'history',
  'photography',
  'wellness',
  'relaxation',
  'landmark'
] as const;

export class CreateTravellerDto {
  @ApiProperty({ description: 'Full or display name of the traveller', example: 'Alex' })
  @IsString()
  @MinLength(1)
  name!: string;

  @ApiPropertyOptional({ description: 'Budget cap in trip currency', example: 500, nullable: true })
  @IsOptional()
  @IsNumber()
  budgetCap?: number | null;

  @ApiPropertyOptional({
    description: 'Interests matching frozen vocabulary',
    example: ['food', 'museums'],
    type: [String]
  })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  interests?: string[];

  @ApiPropertyOptional({
    description: 'Dislikes matching frozen vocabulary',
    example: ['nightlife'],
    type: [String]
  })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  dislikes?: string[];

  @ApiPropertyOptional({
    description: 'Preferred travel pace',
    enum: ['relaxed', 'balanced', 'packed'],
    nullable: true
  })
  @IsOptional()
  @IsIn(['relaxed', 'balanced', 'packed'])
  pace?: TravellerPace | null;
}

export class UpdateTravellerDto {
  @ApiPropertyOptional({ description: 'Full or display name of the traveller' })
  @IsOptional()
  @IsString()
  @MinLength(1)
  name?: string;

  @ApiPropertyOptional({ description: 'Budget cap in trip currency', nullable: true })
  @IsOptional()
  @IsNumber()
  budgetCap?: number | null;

  @ApiPropertyOptional({ description: 'Interests matching frozen vocabulary', type: [String] })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  interests?: string[];

  @ApiPropertyOptional({ description: 'Dislikes matching frozen vocabulary', type: [String] })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  dislikes?: string[];

  @ApiPropertyOptional({ enum: ['relaxed', 'balanced', 'packed'], nullable: true })
  @IsOptional()
  @IsIn(['relaxed', 'balanced', 'packed'])
  pace?: TravellerPace | null;
}

export class JoinTripDto {
  @ApiProperty({ description: 'Share or invite token for the trip', example: 'abc123xyz' })
  @IsString()
  @MinLength(1)
  token!: string;

  @ApiProperty({ description: 'Full or display name of the joining traveller', example: 'Jordan' })
  @IsString()
  @MinLength(1)
  name!: string;

  @ApiPropertyOptional({ description: 'Budget cap in trip currency', nullable: true })
  @IsOptional()
  @IsNumber()
  budgetCap?: number | null;

  @ApiPropertyOptional({ description: 'Interests matching frozen vocabulary', type: [String] })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  interests?: string[];

  @ApiPropertyOptional({ description: 'Dislikes matching frozen vocabulary', type: [String] })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  dislikes?: string[];

  @ApiPropertyOptional({ enum: ['relaxed', 'balanced', 'packed'], nullable: true })
  @IsOptional()
  @IsIn(['relaxed', 'balanced', 'packed'])
  pace?: TravellerPace | null;
}
