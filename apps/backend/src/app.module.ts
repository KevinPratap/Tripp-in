import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TripsModule } from './trips/trips.module';
import { PlacesModule } from './places/places.module';
import { RoutesModule } from './routes/routes.module';
import { WeatherModule } from './weather/weather.module';
import { EngineModule } from './engine/engine.module';
import { AIModule } from './ai/ai.module';
import { ItinerariesModule } from './itineraries/itineraries.module';
import { DestinationsModule } from './destinations/destinations.module';
import { SDUIModule } from './sdui/sdui.module';
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
    SDUIModule
  ],
  providers: [PrismaService, RedisService]
})
export class AppModule {}
