import { Module } from '@nestjs/common';
import { AuthController, MeController } from './auth.controller';
import { AuthService } from './auth.service';
import { PrismaService } from '../common/prisma/prisma.service';
import { TripsModule } from '../trips/trips.module';
import { PublicShareController, TripSharesController } from '../trips/shares.controller';
import { SharesService } from '../trips/shares.service';

/**
 * Accounts, sessions and public shares.
 *
 * Imports TripsModule for TripsService, because resolving a share returns the same trip
 * envelope the trips controller returns and there should only be one place that builds it.
 */
@Module({
  imports: [TripsModule],
  controllers: [AuthController, MeController, TripSharesController, PublicShareController],
  providers: [AuthService, SharesService, PrismaService],
  exports: [AuthService]
})
export class AuthModule {}
