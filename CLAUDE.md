# Ticketly — learning project (Java 25 / Spring Boot 4.1)

## What this is
Event ticketing platform built as microservices, to relearn modern Java and
Spring Boot for senior interviews. The full spec is `REQUIREMENTS.md`.
Read only the section for the current feature ID (e.g. `#### F-03`) plus the
sections it references. Never load the whole file unless I ask.

## Workflow
- Every feature goes through two skills, in order:
  `/plan-feature F-XX` (discuss → I write "approved" → plan saved to `docs/plans/`)
  then `/implement-feature F-XX` (builds from the approved plan only).
- Never write application code for a feature without an approved plan for it.
- `REQUIREMENTS.md` is the WHAT (goal, acceptance criteria). `docs/plans/F-XX-*.md` is the HOW.
- Never build ahead of the current feature.

## How to work with me
- I am relearning: for every modern Java (21+) or Spring Boot 4 feature you use,
  add a 1–3 line comment explaining WHY, and explain design choices in plain words.
- If unsure an API exists in Boot 4.1 / Spring 7, say so and tell me where to verify.
- Ask when a requirement is ambiguous instead of guessing.

## Stack
Java 25, Spring Boot 4.1.x, Maven wrapper, PostgreSQL 17, Flyway, Spring Data JPA,
Spring Kafka 4, Spring Security 7 + Keycloak, Spring Cloud 2025.1.2+, Testcontainers.

## Conventions (details in REQUIREMENTS.md §5 and §6)
- No Lombok. Constructor injection only.
- Records for DTOs, commands, Kafka events, projections, @ConfigurationProperties.
  Classes for JPA entities (protected no-arg ctor, ID-based equals/hashCode).
- @Transactional on application services, never on controllers.
- UUID ids, Instant timestamps, a `Money` record for amounts.
- Flyway `V<n>__description.sql`; never edit an applied migration.
- Errors as ProblemDetail via @RestControllerAdvice. Use @MockitoBean, not @MockBean.

## Commands
- Infra: `docker compose -f infra/docker-compose.yml up -d`
- Run a service: `cd <service> && ./mvnw spring-boot:run`
- Tests: `./mvnw verify` (Docker must be running for Testcontainers)

## Layout
Monorepo: `infra/`, `docs/adr/`, `docs/plans/`, `docs/learning-notes/`, `http/`,
one Maven project per service (`catalog-service/`, `booking-service/`, ...).
Package layout per service: REQUIREMENTS.md §5.2.

## Definition of done
Zero warnings, migration if schema changed, tests green, OpenAPI updated,
`http/` collection updated, plan `status: implemented`,
learning note in `docs/learning-notes/F-XX.md`.