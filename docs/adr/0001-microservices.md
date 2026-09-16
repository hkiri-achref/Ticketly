# ADR 0001 — Build Ticketly as microservices

Date: 2026-09-16
Status: Accepted

## Context

Ticketly is modeled as a ticketing business that has outgrown a monolith. The
pressures are real, not decorative (REQUIREMENTS.md §2):

- **Different load profiles.** Catalog browsing is read-heavy and cacheable;
  booking is write-heavy and spikes ~1000× during an on-sale. They cannot be
  scaled sensibly as one deployable.
- **Different risk profiles.** Payment code changes rarely and under scrutiny;
  notification templates change weekly. One release cadence would hold the
  fast side hostage to the slow side.
- **Failure isolation.** The email provider goes down regularly; the payment
  provider has latency spikes. A booking must still be accepted when email is
  down.
- **Team ownership.** Three teams (Catalog, Checkout, Engagement), each owning
  its service and its database — no shared tables, integration only through
  APIs and events.
- **Auditability.** Finance needs an immutable record per booking; a Kafka
  event log doubles as that audit trail, consumable without touching
  production databases.

## Decision

Split by business capability into independently built and deployed services —
`catalog-service`, `booking-service`, `payment-service`,
`notification-service`, behind an `api-gateway` — each owning its own
PostgreSQL database (database-per-service, physically separate instances),
integrating asynchronously through Kafka topics keyed by aggregate ID, and
synchronously only where the flow demands it (booking → catalog).

Locally the whole platform runs from `infra/docker-compose.yml`. Databases are
containers **in dev only**; production would use managed instances (e.g. RDS)
or a Kubernetes operator (e.g. CloudNativePG) — still one per service.

## Consequences

Accepted costs, to be handled explicitly in later features:

- **No distributed transactions** → sagas and eventual consistency (Phase 4).
- **Data duplication** → booking keeps its own inventory replica fed by
  catalog events (F-09, F-15).
- **Operational complexity** → gateway, per-service config, tracing, many
  containers (Phases 5–6).
- **Network calls fail** → timeouts everywhere, retries, idempotent consumers,
  circuit breaking (F-13, F-19).

Security note: the Keycloak client `ticketly-api` has **Direct Access Grants
enabled for local development only** — it lets us obtain tokens with `curl`
using a username/password. In production only Authorization Code + PKCE would
be enabled; the password grant is deprecated in OAuth 2.1.