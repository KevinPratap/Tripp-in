import { Module } from '@nestjs/common';
import { HealthController } from './health.controller';
import { HealthService } from './health.service';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';

@Module({
  controllers: [HealthController],
  providers: [HealthService, PrismaService, RedisService],
  exports: [HealthService]
})
export class HealthModule {}
