---
feature: F-00
title: Repository, infrastructure and first ADR
status: implemented
approved_on: 2026-09-16
requirements: REQUIREMENTS.md#F-00
depends_on: []
---

# F-00 — Repository, infrastructure and first ADR

## Goal (in my words)
Anyone cloning the repo runs one command and gets the whole local platform up —
four databases, Kafka, Keycloak, Mailpit, observability — all healthy. The first
ADR records *why* microservices before any service exists. No application code.

## Scope
- In: monorepo skeleton (§5.1), `infra/docker-compose.yml`, per-service Postgres
  containers, Kafka topics script, Keycloak realm (UI-configured then exported),
  ADR 0001, README, `http/keycloak.http`, learning note.
  Also: delete the leftover root `pom.xml`, `src/`, `target/` (violates §5.1 —
  no root build; one Maven project per service) and add a root `.gitignore`.
- Out (explicitly): any Spring Boot code or profiles (F-01), service Dockerfiles
  (F-22), secrets hygiene (F-23), Kubernetes (F-32), backups/replication/pooling.

## Design
| Component (file) | Responsibility | Concept practiced |
|---|---|---|
| `infra/docker-compose.yml` | 8 containers, named volumes, healthchecks | Compose, volume lifecycle, readiness |
| `catalog-db`..`notification-db` (postgres:17) | one DB container per service, own credentials, ports 5432–5435 | database-per-service, physical isolation |
| `kafka` (apache/kafka:4.x) | single-node KRaft broker, port 9092 | KRaft (no ZooKeeper in Kafka 4) |
| `keycloak` (quay.io/keycloak:26.x) | OIDC provider, port 8081, `--import-realm` | realm, client, grant, claim, issuer, JWKS |
| `mailpit` | SMTP catcher, UI :8025 / SMTP :1025 | fake external provider |
| `lgtm` (grafana/otel-lgtm) | Grafana :3000, OTLP :4317/:4318 | observability stack (used from F-25) |
| `infra/kafka/create-topics.sh` | create §8.1 topics, idempotent | partitions, keys, DLT |
| `infra/keycloak/realm-export.json` + `README.md` | committed realm + dev passwords | export/import reproducibility |
| `docs/adr/0001-microservices.md` | Context/Decision/Consequences from §2 | ADR discipline, trade-offs |

### Data & migration
No Flyway yet (no service). One named volume per DB container
(`catalog-db-data`…): data survives `down`/recreate/upgrade; deletion only via
explicit `down -v`. Pin `postgres:17` — data dirs are not compatible across
major versions. Inside the Docker network services will address DBs by
container DNS name (`catalog-db:5432`), mirroring prod hostnames; host ports
5432–5435 exist only because the laptop is one machine.

Healthchecks: `pg_isready` (postgres), `kafka-broker-api-versions` (kafka),
Keycloak has no curl/wget in the image → `KC_HEALTH_ENABLED=true` and bash
`/dev/tcp` probe on management port 9000 (KC 25+ moved health there).

### Contracts
No HTTP/Kafka app contracts. Topics created (from §8.1):
`catalog.events`, `booking.events`, `payment.events` — 3 partitions each;
`<topic>.DLT` — 1 partition each; replication factor 1.

Keycloak realm `ticketly` (§9.1): roles `customer`, `organizer`, `admin`;
public client `ticketly-api` (PKCE, Direct Access Grants **dev-only** — noted
in ADR); users `alice`/`carol` (customer), `bob` (organizer), `admin` (admin).
Flow: configure in UI (learning goal) → export JSON → commit → auto-import.

### Transactions & concurrency
N/A (no app code). Idempotency: `create-topics.sh` uses `--if-not-exists`;
Keycloak skips import when the realm already exists.

## Alternatives rejected
- One Postgres server, four databases → logical but not physical isolation; hides
  the database-per-service shape needed for K8s (StatefulSet/PVC per DB) later.
- Hosted/external dev databases → cost, network flakiness, kills one-command repro.
- Hand-writing `realm-export.json` → skips the OIDC vocabulary this feature teaches.
- Confluent/Bitnami Kafka images → official `apache/kafka` is the simplest KRaft node.
- Testcontainers smoke test → no code to attach a test to; manual ACs suffice.

## Test plan
| Acceptance criterion | Test type | How |
|---|---|---|
| `docker compose -f infra/docker-compose.yml up -d` → all healthy | manual | `docker compose ps` shows every container `healthy` |
| Token endpoint returns JWT for `alice` with `realm_access.roles: ["customer"]` | manual | request in `http/keycloak.http`; decode at jwt.io |
| Mailpit UI reachable | manual | browser → `http://localhost:8025` |
| Grafana reachable | manual | browser → `http://localhost:3000` |
| Data survives restart (volume lifecycle) | manual | `down` then `up`, Keycloak realm/users still present |

## Implementation steps
- [x] 1. Delete root `pom.xml`, `src/`, `target/` (and empty `.mvn/`); root `.gitignore` already adequate.
- [x] 2. Create skeleton dirs: `docs/adr`, `docs/learning-notes`, `infra/{kafka,keycloak,grafana}`, `http/`.
- [x] 3. Write `infra/docker-compose.yml` (4× postgres, kafka, keycloak, mailpit, lgtm; volumes; healthchecks; comments).
- [x] 4. Write `infra/kafka/create-topics.sh`; run it; verify with `kafka-topics.sh --list`.
- [x] 5. Configure realm `ticketly` in Keycloak UI (checklist in `infra/keycloak/README.md`), export, commit, enable `--import-realm`.
- [x] 6. Write `docs/adr/0001-microservices.md` (§2 arguments + trade-offs + DAG dev-only note).
- [x] 7. Write root `README.md` (how to run) and `http/keycloak.http` (token request).
- [x] 8. Verify all acceptance criteria; write `docs/learning-notes/F-00.md`.

## Learning focus
Container vs. volume lifecycle; database-per-service as physical isolation;
dev-containers vs. managed-prod databases; healthchecks as readiness probes;
KRaft; OIDC vocabulary. Interview questions: "Would you run Postgres in
containers in production?" and "Why did you split the monolith — and what did
it cost you?"

## Deviations
- Realm export lives at `infra/keycloak/import/realm-export.json` (not
  `infra/keycloak/realm-export.json` as §5.1 sketches): a dedicated `import/`
  subfolder is mounted into the container, avoiding Docker's
  missing-file-becomes-directory bind-mount pitfall and keeping `README.md`
  out of Keycloak's import scanner. Content and behavior unchanged.
- Realm export requires stopping Keycloak first (dev-mode embedded H2 file
  lock); working procedure documented in `infra/keycloak/README.md`.
- Added (user request): Kafbat UI container on :8082 — Kafka ships no UI;
  needed to visualize topics/partitions/consumer lag while learning Kafka.