---
feature: F-01
title: catalog-service skeleton
status: implemented
approved_on: 2026-09-17
requirements: REQUIREMENTS.md#F-01
depends_on: [F-00]
---

# F-01 — catalog-service skeleton

## Goal (in my words)
Stand up `catalog-service` as a walking skeleton: it boots, connects to its own
Postgres via Docker Compose support, runs an (almost) empty Flyway migration,
exposes `/api/v1/ping` and Swagger UI, and has one Testcontainers-backed test.
No business logic — its value is being the template every later service copies.

## Scope
- In: Maven project from start.spring.io (Java 25, Boot 4.1.x; Web, Validation,
  Data JPA, PostgreSQL, Flyway, Actuator, Docker Compose support, Testcontainers),
  springdoc, `application.yml` (`local`/`test` profiles, virtual threads on),
  `V1__init.sql`, `PingController`, `CatalogProperties` record, one
  `@SpringBootTest`, `http/catalog.http`, compose-profile change in `infra/`.
- Out (explicitly): entities/CRUD (F-02+), security (F-06+), Kafka (F-14+),
  Dockerfile (F-22), secrets hygiene (F-23), any second service.

## Design
| Component (file/class) | Responsibility | Concept practiced |
|---|---|---|
| `catalog-service/` Maven project | independent build, `com.ticketly.catalog` | Boot 4 starters, monorepo §5.1 |
| `CatalogApplication` | `@SpringBootApplication` + `@ConfigurationPropertiesScan` | config records found by scanning |
| `api/PingController` | `GET /api/v1/ping` | versioned paths §5.3 |
| `api/PingResponse` | `record PingResponse(String service, Instant time)` | records as response DTOs §6.2 |
| `config/CatalogProperties` | `@ConfigurationProperties("ticketly.catalog") record (@NotBlank String name)`, `@Validated` | immutable validated config, fail-fast |
| `application.yml` | default + `local`/`test`; `spring.threads.virtual.enabled=true`; datasource `localhost:5432/catalog_db` | virtual threads flag |
| `db/migration/V1__init.sql` | SQL comment only | Flyway creates `flyway_schema_history` anyway |
| `CatalogServiceApplicationTests` | `@SpringBootTest` + `@ServiceConnection` postgres:17 container | `@ServiceConnection` replaces `@DynamicPropertySource` |
| `http/catalog.http` | ping + health example requests | DoD |

### Data & migration
No tables. `V1__init.sql` = one comment; the lesson is that Flyway needs the
schema-history table even with an empty schema. Flyway on Postgres requires the
`flyway-database-postgresql` module (start.spring.io includes it).

### Contracts
`GET /api/v1/ping` → 200 `PingResponse(String service, Instant time)`;
`service` comes from `CatalogProperties.name`. Actuator: `health` (and `info`)
exposed. No Kafka.

### Docker Compose support (the one real decision)
`spring.docker.compose.file: ../infra/docker-compose.yml`. That file has 4
postgres containers → Boot would create 4 `JdbcConnectionDetails` candidates
and fail. Fix: **compose profiles** — tag each DB with a profile
(`catalog`, `booking`, `payment`, `notification`), add `infra/.env` with
`COMPOSE_PROFILES=catalog,booking,payment,notification` so the F-00 command
still starts everything, and set
`spring.docker.compose.profiles.active: catalog` in catalog-service. Scales to
every later service against the same file.
⚠ Verify in Boot 4.1 docs: exact property name (since Boot 3.1) and that
profile filtering hides the other DBs even when already running.
⚠ Verify springdoc's Boot-4-compatible version (3.x line) at springdoc.org.

### Transactions & concurrency
None — no application service exists yet, so no `@Transactional` anywhere.
Tests vs compose: `spring.docker.compose.skip.in-tests` defaults to true, so
Testcontainers owns the DB in tests.

## Alternatives rejected
- `org.springframework.boot.ignore` labels on other DBs → global, breaks at F-09.
- Disable compose support, require manual `docker compose up` → skips the F-01 learning concept.
- Shared singleton Testcontainer base class → premature for one test.
- Setter-based `@ConfigurationProperties` class → record binding is the lesson.

## Test plan
| Acceptance criterion (from REQUIREMENTS) | Test type | Test class/method |
|---|---|---|
| `./mvnw spring-boot:run` auto-starts DB via compose support | manual | stop `catalog-db`, run, watch Boot start it |
| `/actuator/health` is UP | manual + implicit | curl; context-load test proves DB wiring |
| Swagger UI shows `/ping` | manual | `http://localhost:8080/swagger-ui.html` |
| `./mvnw verify` passes CI-style | integration | `CatalogServiceApplicationTests` (`@SpringBootTest` + `@ServiceConnection`) |

## Implementation steps
- [x] 1. Add compose profiles to the 4 DBs in `infra/docker-compose.yml` + `infra/.env`; verify `docker compose up -d` still starts all.
- [x] 2. Generate project from start.spring.io; move into `catalog-service/`; align package `com.ticketly.catalog`.
- [x] 3. Add springdoc dependency (verify Boot-4 version).
- [x] 4. Write `application.yml` (profiles, virtual threads, datasource, compose file + profile).
- [x] 5. Add `V1__init.sql`, `CatalogProperties`, `PingController` + `PingResponse`.
- [x] 6. Write `CatalogServiceApplicationTests`; run `./mvnw verify`.
- [x] 7. Manual ACs; `http/catalog.http`; README note; learning note `docs/learning-notes/F-01.md`; set plan `status: implemented`.

## Learning focus
Boot 4 starter anatomy; virtual threads flag and why blocking JDBC is fine with
it; `@ConfigurationProperties` on records; `@ServiceConnection` vs the old
`@DynamicPropertySource`; compose support vs Testcontainers ownership.
Interview questions: "Why are your JPA entities classes but everything else
records?" and "How does Spring Boot's Docker Compose support decide which
container backs your DataSource?"

## Deviations
- Added `spring.docker.compose.start.skip: never`: the default (`if-running`)
  skips `up` when ANY project container runs, so a stopped `catalog-db` was
  never started while kafka/keycloak were up → app failed. `never` always runs
  `up` (idempotent) and Boot waits for the DB healthcheck. Verified live.
- Both ⚠ items resolved: `spring.docker.compose.profiles.active` exists and
  Boot filters connection details by profile even though plain
  `docker compose ps` shows all running containers; springdoc for Boot 4 is
  **3.1.1** (2.x = Boot 3; 3.1 added Jackson 3 support).
- Boot 4.1 renames vs the plan's Boot-3-era names: `spring-boot-starter-webmvc`
  (was `-web`), per-starter test artifacts (no `spring-boot-starter-test`),
  Testcontainers 2 (`org.testcontainers.postgresql` package, non-generic
  `PostgreSQLContainer`), `spring-boot-starter-flyway`.
- Manual run verified on port 8085 (`SERVER_PORT` override): an unrelated
  container (`eve-api-mock`) occupies host 8080. Config stays on 8080.
