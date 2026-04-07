# Tech Stack

## Language & Runtime
- Java 17
- Spring Boot 4.0.5

## Frameworks & Libraries
- `spring-boot-starter` - core Spring Boot
- `spring-boot-starter-web` - REST controllers
- `spring-boot-starter-data-jpa` - JPA / Spring Data repositories
- `spring-boot-starter-validation` - Bean Validation (`@Valid`, `@NotBlank`, etc.)
- `spring-boot-starter-webmvc-test` - MockMvc support in tests
- `lombok` - boilerplate reduction (getters, setters, builders, etc.)
- `spring-boot-starter-test` - testing (JUnit 5, Mockito, AssertJ, etc.)
- `spring-boot-configuration-processor` - annotation processing for `@ConfigurationProperties`

## Persistence
- PostgreSQL (runtime JDBC driver: `org.postgresql:postgresql`) — used in `postgres` profile
- H2 in-memory database (`com.h2database:h2`) — used in `h2` profile
- Spring Data JPA with Hibernate
  - `postgres` profile: `ddl-auto: none`, schema managed by Liquibase
  - `h2` profile: `ddl-auto: create-drop`, schema created by Hibernate on startup
- Liquibase for schema migrations (`org.liquibase:liquibase-core`) — enabled only in `postgres` profile
  - Master changelog: `src/main/resources/db/changelog/db.changelog-master.yaml`
- H2 seed data: `src/main/resources/h2/data.sql` — executed on every startup in `h2` profile

## Testing
- Unit tests: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`)
- Integration tests: Testcontainers (`org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` v1.21.3)
  - Requires Docker to be running
  - Base class: `AbstractIntegrationTest` (spins up a shared `postgres:16-alpine` container)

## Build System
- Maven (wrapper included: `mvnw` / `mvnw.cmd`)

## Common Commands

```bash
# Compile
./mvnw compile

# Run tests (unit + integration — Docker required for integration tests)
./mvnw test

# Run unit tests only (skip integration tests)
./mvnw test -Dtest='!*IT,!*IntegrationTest'

# Build JAR
./mvnw package

# Run the application with PostgreSQL profile (requires DB_PASSWORD env var)
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres

# Run the application with H2 in-memory profile (no external DB required)
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# Run with profile via environment variable
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run

# Skip tests during build
./mvnw package -DskipTests
```

## Configuration
- Base config: `src/main/resources/application.yaml` (shared across all profiles)
- PostgreSQL profile: `src/main/resources/application-postgres.yaml`
- H2 profile: `src/main/resources/application-h2.yaml`
- H2 console available at `http://localhost:8080/h2-console` when running with `h2` profile
