import { Type } from 'class-transformer';
import { IsInt, IsNumber, IsOptional, IsString, Matches, Max, Min } from 'class-validator';

/**
 * Where to look for things to do. Decorated because the global ValidationPipe runs with
 * whitelist: true; registered in common/validation/dto-decorators.spec.ts.
 */
export class NearbyQueryDto {
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  lat!: number;

  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  lng!: number;

  /** Metres. Bounded to walking distance so one request cannot ask Overpass for a whole city. */
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(100)
  @Max(2000)
  radius?: number;
}

/** A day's stops in visiting order, as "lat,lng;lat,lng;...". Two to twelve stops. */
export class RouteQueryDto {
  @IsString()
  @Matches(/^-?\d+(\.\d+)?,-?\d+(\.\d+)?(;-?\d+(\.\d+)?,-?\d+(\.\d+)?){1,11}$/)
  points!: string;
}
