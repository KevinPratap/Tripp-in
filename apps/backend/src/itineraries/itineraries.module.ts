import { Module } from '@nestjs/common';
import { ItinerariesService } from './itineraries.service';
import { ItinerariesController } from './itineraries.controller';
import { PrismaService } from '../common/prisma/prisma.service';
import { AIModule } from '../ai/ai.module';
import { EngineModule } from '../engine/engine.module';
import { ConfigService } from '@nestjs/config';

@Module({
  imports: [AIModule, EngineModule],
  controllers: [ItinerariesController],
  providers: [ItinerariesService, PrismaService, ConfigService],
  exports: [ItinerariesService]
})
export class ItinerariesModule {}
