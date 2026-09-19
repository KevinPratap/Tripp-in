import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';
import { RateLimitGuard } from './common/guards/rate-limit.guard';
import { TripsModule } from './trips/trips.module';
import { PlacesModule } from './places/places.module';
import { RoutesModule } from './routes/routes.module';
import { WeatherModule } from './weather/weather.module';
import { EngineModule } from './engine/engine.module';
import { AIModule } from './ai/ai.module';
import { ItinerariesModule } from './itineraries/itineraries.module';
import { DestinationsModule } from './destinations/destinations.module';
import { SDUIModule } from './sdui/sdui.module';
import { HealthModule } from './health/health.module';
import { AuthModule } from './auth/auth.module';
import { PrismaService } from './common/prisma/prisma.service';
import { RedisService } from './common/redis/redis.service';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env', '../../infrastructure/environments/.env.example']
    }),
    TripsModule,
    PlacesModule,
    RoutesModule,
    WeatherModule,
    EngineModule,
    AIModule,
    ItinerariesModule,
    DestinationsModule,
    SDUIModule,
    HealthModule,
    AuthModule
  ],
  providers: [
    PrismaService,
    RedisService,
    // Global sliding window limiter. AI routes carry a tighter budget set with @RateLimit.
    { provide: APP_GUARD, useClass: RateLimitGuard }
  ]
})
export class AppModule {}
