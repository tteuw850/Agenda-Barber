# Agenda Barber - Java Backend

API independent of the Android application. Java 21, Spring Boot 4.1.1, Maven.

## Local-only bootstrap

- Run `mvn -f backend/pom.xml verify`.
- Run `mvn -f backend/pom.xml spring-boot:run`.

With the **default** Spring profile, the API does not connect to Neon. Local diagnostics:

- http://localhost:8080/actuator/health
- http://localhost:8080/actuator/info
- http://localhost:8080/v3/api-docs
- http://localhost:8080/swagger-ui.html

Business routes are denied by default. OpenAPI and Actuator web endpoints are disabled
outside the default profile. This is not a production deployment profile.

## Database configuration

Environment variable names (never commit real values):

- `SPRING_PROFILES_ACTIVE=neon`
- `NEON_JDBC_URL`: Java JDBC URL for the **development** branch; include `sslmode=verify-full`.
- `NEON_DB_USERNAME=agenda_app_dev`
- `NEON_DB_PASSWORD`: inject securely into the process, never embed in app or repository.

See `.env.example` for placeholders. The example is **not** automatically loaded by Spring Boot.
Never place a Neon URL or password in an Android APK.

`NeonConnectionSmokeTest` runs only when the Neon profile is active. It checks `SELECT 1`,
database/user identity and active TLS. A passing default-profile `mvn verify` does
**not** prove TLS connectivity to Neon.

Database schema migrations are deliberately disabled (`spring.flyway.enabled=false`)
during F01-02. F01-03 will configure a separate privileged migration identity,
direct database connection, migrations and validation. Runtime users must not own schema objects.

## Security and error responses

Security denies all business endpoints until F01-05 introduces JWT authorization.
Validation errors return HTTP 400 Problem Details; unexpected errors return generic HTTP 500
without disclosing exception text or credentials. Health and Swagger are local-only diagnostics.

## Build

Run `mvn -f backend/pom.xml --batch-mode verify`.
The normal CI build must not require a Neon password or a live database.


## F01-03A database integration

Versioned migration: `src/main/resources/db/migration/V1__create_initial_schema.sql`.
Disposable PostgreSQL 17 via Testcontainers verifies Flyway migrate/validate and constraints.
Run `mvn -f backend/pom.xml verify` with Docker Engine running. No Neon credentials are needed.
Default and Neon profiles keep automatic Flyway disabled. A separately approved direct
connection with a migration identity is needed for Neon schema rollout.
Preserve JPA ddl-auto=none until entities are added; never use ddl-auto=update.
Cross-table blocked-period conflicts require application transactions and locking.
