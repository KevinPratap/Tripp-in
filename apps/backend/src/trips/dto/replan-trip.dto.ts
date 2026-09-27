import { IsString, IsOptional, IsIn, IsNumber } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class ReplanTripDto {
  @ApiProperty({
    description: 'One-tap replan intent trigger code',
    enum: ['running-late', 'rain', 'tired', 'swap-activity', 'add-stop', 'budget-cut', 'custom'],
    example: 'rain'
  })
  @IsString()
  @IsIn(['running-late', 'rain', 'tired', 'swap-activity', 'add-stop', 'budget-cut', 'custom'])
  intent: string;

  @ApiPropertyOptional({
    description: 'Optional free-text instructions or context from the traveler',
    example: 'Prefer covered historic passages and museums in the city centre'
  })
  @IsOptional()
  @IsString()
  freeText?: string;

  @ApiPropertyOptional({
    description: 'Target activity ID to replace when using swap-activity intent',
    example: '3bec0523-7182-4058-9e5d-8adc7387048a'
  })
  @IsOptional()
  @IsString()
  targetActivityId?: string;

  @ApiPropertyOptional({
    description: 'Target day index to replan (1-based), or omit to adapt the entire circuit',
    example: 1
  })
  @IsOptional()
  @IsNumber()
  dayIndex?: number;
}
