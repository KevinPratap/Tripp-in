import { Module } from '@nestjs/common';
import { DestinationsService } from './destinations.service';
import { PrismaService } from '../common/prisma/prisma.service';

@Module({
  providers: [DestinationsService, PrismaService],
  exports: [DestinationsService]
})
export class DestinationsModule {}
