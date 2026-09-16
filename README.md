# Ticketly

Event ticketing platform built as microservices — a learning project for
modern Java (25) and Spring Boot 4. The full specification lives in
`REQUIREMENTS.md`; architectural decisions in `docs/adr/`.

## Prerequisites

- Docker Desktop (running)
- Java 25 (services are built per-directory with their own Maven wrapper)

## Run the local platform

```bash
docker compose -f infra/docker-compose.yml up -d   # start everything
docker compose -f infra/docker-compose.yml ps      # wait until all healthy
infra/kafka/create-topics.sh                       # idempotent, run anytime
```

| Service | URL | Credentials |
|---|---|---|
| Keycloak admin | http://localhost:8081 | `admin` / `admin` |
| Mailpit (mail UI) | http://localhost:8025 | — |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| Kafka | `localhost:9092` | — |
| Kafbat UI (Kafka web UI) | http://localhost:8082 | — |
| Postgres ×4 | `localhost:5432`–`5435` | see `infra/docker-compose.yml` |

Dev users and how to get a JWT: `infra/keycloak/README.md` and
`http/keycloak.http`.

Data persists across `docker compose down` (named volumes). Full reset:
`docker compose -f infra/docker-compose.yml down -v`.

## Repository layout

Monorepo with independent builds — one Maven project per service, no root
build. See REQUIREMENTS.md §5.1. Feature plans: `docs/plans/`. Per-feature
learning notes: `docs/learning-notes/`.