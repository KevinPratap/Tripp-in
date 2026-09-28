import { Module } from '@nestjs/common';
import { RedisService } from '../common/redis/redis.service';
import { ExploreController } from './explore.controller';
import { ExploreService } from './explore.service';

@Module({
  controllers: [ExploreController],
  providers: [ExploreService, RedisService]
})
export class ExploreModule {}
