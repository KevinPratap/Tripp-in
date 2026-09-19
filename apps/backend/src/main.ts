import { NestFactory } from '@nestjs/core';
import { ValidationPipe, Logger } from '@nestjs/common';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import { AppModule } from './app.module';
import { LoggingInterceptor } from './common/interceptors/logging.interceptor';
import { AllExceptionsFilter } from './common/filters/http-exception.filter';

async function bootstrap() {
  const logger = new Logger('Bootstrap');
  const app = await NestFactory.create(AppModule);

  // Global prefix
  app.setGlobalPrefix('api/v1');

  // CORS. Credentialed requests only make sense against an explicit origin list,
  // and the combination of a wildcard origin with credentials is invalid anyway.
  const corsOrigins = (process.env.CORS_ORIGINS || '')
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);

  app.enableCors(
    corsOrigins.length > 0
      ? { origin: corsOrigins, credentials: true }
      : { origin: '*', credentials: false }
  );

  // Baseline response hardening. Swagger needs inline styles, so the CSP stays
  // permissive for the docs route but still blocks framing and sniffing.
  app.getHttpAdapter().getInstance().disable('x-powered-by');
  app.use((_req: any, res: any, next: any) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Permissions-Policy', 'geolocation=(self), camera=(), microphone=()');
    res.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
    if (process.env.NODE_ENV === 'production') {
      res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
    }
    next();
  });

  // Global Interceptors & Filters
  app.useGlobalInterceptors(new LoggingInterceptor());
  app.useGlobalFilters(new AllExceptionsFilter());

  // Request validation
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      forbidNonWhitelisted: false
    })
  );

  // Swagger OpenAPI Documentation
  const swaggerEnabled = process.env.SWAGGER_ENABLED !== 'false';
  const config = new DocumentBuilder()
    .setTitle("Trippin' AI API")
    .setDescription("Production-ready API for Trippin' AI — Travel Planning with Deterministic Validation")
    .setVersion('1.0.0')
    .addBearerAuth()
    .build();

  if (swaggerEnabled) {
    const document = SwaggerModule.createDocument(app, config);
    SwaggerModule.setup('api/docs', app, document);
  } else {
    logger.log('Swagger docs disabled (SWAGGER_ENABLED=false)');
  }

  const port = process.env.PORT || 4000;
  await app.listen(port, '0.0.0.0');
  logger.log(`🚀 Trippin' AI Backend running on http://0.0.0.0:${port}/api/v1`);
  logger.log(`📖 Swagger API documentation available at http://0.0.0.0:${port}/api/docs`);
}

bootstrap();
