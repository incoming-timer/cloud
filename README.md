# Incoming Timer Server

Kotlin and Spring Boot server for durable, multi-device timer synchronization.

## Requirements

- Java 21
- Docker (for PostgreSQL integration tests)

## Commands

```shell
./gradlew check
./gradlew :app-api:bootRun
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Modules

- `app-api`: HTTPS synchronization API
- `app-worker`: transactional outbox publisher
- `app-realtime`: realtime gateway
- `domain`: timer domain model
- `application`: use cases and ports
- `infrastructure-db`: PostgreSQL adapters and migrations
- `infrastructure-kafka`: Kafka adapters
- `infrastructure-redis`: realtime fan-out adapters
- `support-test`: shared test fixtures
