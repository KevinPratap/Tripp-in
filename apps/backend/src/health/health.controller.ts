import { Controller, Get, HttpCode, HttpStatus } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { HealthService, HealthCheckResult } from './health.service';

@ApiTags('Health')
@Controller('health')
export class HealthController {
  constructor(private readonly healthService: HealthService) {}

  @Get()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Overall system health check' })
  @ApiResponse({ status: 200, description: 'System health report' })
  async getHealth(): Promise<HealthCheckResult> {
    return this.healthService.checkHealth();
  }

  @Get('liveness')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Kubernetes / Cloud Run liveness probe' })
  getLiveness(): { status: string } {
    return { status: 'alive' };
  }

  @Get('readiness')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Kubernetes / Cloud Run readiness probe' })
  async getReadiness(): Promise<{ status: string; healthy: boolean }> {
    const report = await this.healthService.checkHealth();
    return {
      status: report.status,
      healthy: report.status !== 'error'
    };
  }
}
