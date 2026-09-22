---
feature: F-03
title: Events and ticket tiers (aggregate modeling)
status: implemented
approved_on: 2026-09-22
requirements: REQUIREMENTS.md#F-03
depends_on: [F-02]
---

# F-03 — Events and ticket tiers (aggregate modeling)

## Goal (in my words)
An organizer describes what is being sold: an event at an existing venue with
one or more ticket tiers, each with its own price and quantity. `Event` is the
aggregate root; tiers are created, changed and removed only through it, and it
enforces the one rule that spans them: total tier quantity never exceeds venue
capacity. `Money` becomes a real value type. Everything stays DRAFT and owned by
a placeholder organizer until F-04 / F-07.

## Scope
- In: `Money` record embeddable, `Event` + `TicketTier` entities, `EventStatus`,
  domain exception hierarchy (422), `V3__events_tiers.sql`, `EventRepository`
  with `@EntityGraph`, commands + `EventService`, request/response records,
  `EventController` (POST, PUT, POST tiers, GET), 422 in `ApiExceptionHandler`,
  tests, http collection.
- Out (explicitly): `@Version` / optimistic locking and the "DRAFT-only" guard
  on PUT (F-04, nothing can leave DRAFT before it), publish/cancel, DELETE tier
  endpoint (domain method only), real organizerId (F-07), Kafka, list/search (F-05).

## Design
| Component (file/class) | Responsibility | Concept practiced |
|---|---|---|
| `domain/Money` | `@Embeddable record Money(BigDecimal amount, String currency)`; compact ctor rejects null, negative, scale > 2, currency not `[A-Z]{3}`; `of(BigDecimal,String)`, `plus(Money)` (same currency else IAE), `times(int)` | record embeddable; Hibernate uses the canonical ctor so validation also runs on load |
| `domain/EventStatus` | enum DRAFT, PUBLISHED, CANCELLED (only DRAFT reachable now) | `@Enumerated(STRING)` |
| `domain/Event` | aggregate root: public ctor (organizerId, title, description, startsAt, endsAt, venue) validates endsAt > startsAt; `addTier(name, price, quantity, maxPerBooking)` checks sum of quantities ≤ `venue.getCapacity()` else `CapacityExceededException`; `removeTier(UUID)`; `update(title, description, startsAt, endsAt)` bumps `updatedAt`; `tiers` = `List<TicketTier>` `@OneToMany(mappedBy="event", cascade=ALL, orphanRemoval=true)` exposed unmodifiable; `venue` `@ManyToOne(fetch=LAZY)`; ID-only equals/hashCode | invariants inside the entity, cascade/orphanRemoval, LAZY |
| `domain/TicketTier` | child: package-private ctor (only `Event.addTier` creates one); `@ManyToOne(fetch=LAZY) event`; `@Embedded Money price` with `@AttributeOverride` → `price_amount`, `price_currency`; quantity; maxPerBooking; ID-only equals/hashCode | child entity reachable only via the root |
| `domain/DomainRuleViolationException` (+ `CapacityExceededException`, `InvalidEventPeriodException`) | unchecked base for business-rule failures | one handler → 422; F-04 extends it |
| `persistence/EventRepository` | `JpaRepository<Event, UUID>` + `@EntityGraph(attributePaths = "tiers") Optional<Event> findWithTiersById(UUID)` | `@EntityGraph` vs `join fetch` (join-fetch variant in learning note only). No `TicketTierRepository` |
| `application/CreateEventCommand`, `UpdateEventCommand`, `AddTierCommand` | records decoupling HTTP shape from use cases | commands (§6.2) |
| `application/EventService` | `@Service @Transactional(readOnly = true)`; read-write `create` (loads venue, 404 via `EntityNotFoundException`), `update`, `addTier` — both load, call domain method, return; **no `save`**; `getById` via `findWithTiersById` | dirty checking; tx boundary on the service |
| `api/CreateEventRequest`, `UpdateEventRequest`, `AddTierRequest` (+ nested `MoneyPayload`) | validation on record components, compact ctor `strip()`; `maxPerBooking` optional, default 8 | request records |
| `api/EventResponse`, `TierResponse`, `MoneyResponse` | static `from(...)` hand-mapping | §6.3 mapping boundary |
| `api/EventController` | endpoints below; injects `"dev-organizer"` into the command (TODO F-07: JWT subject) | controller only maps HTTP |
| `api/ApiExceptionHandler` | add `DomainRuleViolationException` → 422 ProblemDetail | RFC-7807 |

### Data & migration
`V3__events_tiers.sql`:
```
events(id uuid pk, organizer_id varchar(255) not null, title varchar(200) not null,
  description varchar(2000), starts_at timestamptz not null,
  ends_at timestamptz not null check (ends_at > starts_at),
  venue_id uuid not null references venues(id),
  status varchar(20) not null check (status in ('DRAFT','PUBLISHED','CANCELLED')),
  created_at timestamptz not null, updated_at timestamptz not null);
create index idx_events_status_starts_at on events (status, starts_at);
ticket_tiers(id uuid pk, event_id uuid not null references events(id),
  name varchar(80) not null, price_amount numeric(12,2) not null check (price_amount >= 0),
  price_currency char(3) not null, quantity int not null check (quantity > 0),
  max_per_booking int not null check (max_per_booking between 1 and 100));
create index idx_ticket_tiers_event_id on ticket_tiers (event_id);
```
No `on delete cascade` on the tier FK: JPA owns child lifecycle; a DB cascade
would hide whether orphanRemoval did its job.

### Contracts
- `POST /api/v1/events` → 201 + `Location` + `EventResponse`; 400 / 404 (venue).
- `PUT /api/v1/events/{id}` → 200 `EventResponse`; 400 / 404 / 422 (period).
- `POST /api/v1/events/{id}/tiers` → 201 `EventResponse` with `tiers[]`, no
  `Location` (tiers have no URI of their own); 400 / 404 / 422 (capacity).
- `GET /api/v1/events/{id}` → 200 `EventResponse` incl. `tiers[]`; 404.
- No Kafka. Security off until F-06.

### Transactions & concurrency
One service method = one transaction; writes rely on dirty checking at commit.
`getById` fetches tiers inside the transaction so the response can be built
after it closes (OSIV is off). Known gap: the capacity check is check-then-act;
two concurrent `addTier` calls can overshoot. Closed by `@Version` in F-04.

## Alternatives rejected
- `Set<TicketTier>` → List keeps API order and avoids set surprises with ID equality.
- Pass `venueCapacity` int into `addTier` → leaks the boundary; caller could lie.
- `Money` as class → §6.2 mandates record embeddables.
- One generic exception with a status field → small typed hierarchy reads better.
- EAGER tiers → exactly the anti-pattern criterion 3 exposes.
- Explicit `save` in `update`/`addTier` → hides dirty checking, a core lesson.
- `@Version` now → F-04 owns optimistic locking and its test; no building ahead.

## Test plan
| Acceptance criterion (from REQUIREMENTS) | Test type | Test class/method |
|---|---|---|
| Tier exceeding capacity throws domain exception | unit | `EventTest.given_tiersAtCapacity_when_addTierExceeding_then_throwsCapacityExceeded` |
| …and maps to 422 ProblemDetail | `@WebMvcTest` + `@MockitoBean EventService` | `EventControllerTest.given_capacityExceeded_when_postTier_then_422ProblemDetail` |
| Removing a tier deletes the row (orphanRemoval) | `@DataJpaTest` + Testcontainers | `EventRepositoryTest.given_eventWithTier_when_removeTierAndFlush_then_rowIsGone` (JdbcTemplate count = 0) |
| Lazy access outside tx throws, then fixed | `@DataJpaTest`, method `@Transactional(propagation = NOT_SUPPORTED)` + `TransactionTemplate` setup | `EventRepositoryTest.given_findById_when_tiersAccessedOutsideTx_then_lazyInitializationException` and `..._when_findWithTiersById_then_tiersLoaded` |
| HashSet before/after persist | `@DataJpaTest` | `EventRepositoryTest.given_eventInHashSet_when_persisted_then_stillContained` |
| Money validation / plus / times | unit | `MoneyTest` |
| Service orchestration, venue 404, commands | Mockito unit | `EventServiceTest` |

## Implementation steps
- [x] 1. `V3__events_tiers.sql`.
- [x] 2. `Money` + `MoneyTest`.
- [x] 3. `EventStatus`, exceptions, `TicketTier`, `Event` + `EventTest`.
- [x] 4. `EventRepository` + `EventRepositoryTest` (orphanRemoval, lazy, entity graph, HashSet).
- [x] 5. Commands + `EventService` + `EventServiceTest`.
- [x] 6. Request/response records, `EventController`, 422 handler + `EventControllerTest`.
- [x] 7. `./mvnw verify`; Swagger pass; update `http/catalog.http`.
- [x] 8. Learning note `docs/learning-notes/F-03.md` (both lazy fixes); plan `status: implemented`.

## Learning focus
Aggregate boundary; cascade/orphanRemoval; LAZY vs EAGER and N+1;
`@EntityGraph` vs `join fetch`; dirty checking; record embeddables with
constructor validation; ID-based equality and HashSet.
Interview questions: "How do you design a JPA aggregate so invariants cannot be
bypassed?" and "What causes LazyInitializationException and what are the
trade-offs of each fix?"

## Deviations
None of substance. Notes recorded for transparency:
- `Money` also normalises the amount to scale 2 (after the planned scale ≤ 2
  check) so `10` and `10.00` are equal records — a strict superset of the plan.
- `Event.tiers` is declared `final` (PMD `ImmutableField`); Hibernate still
  populates it by reflection, proven by `EventRepositoryTest`.
- `Event.removeTier` throws `EntityNotFoundException` for an unknown tier id
  instead of silently ignoring it (the plan did not say either way).
- `EventService.update` loads through `findWithTiersById` as well (not only
  `getById`/`addTier`): the response includes `tiers[]` and is built after
  the transaction has closed.
- The orphanRemoval test works on the instance returned by `save()`: with an
  app-assigned id Spring Data calls `merge`, which returns a managed copy.
  Documented in the learning note; `Persistable` left for later.
- 422 uses `HttpStatus.UNPROCESSABLE_CONTENT` (Spring 7 name; the older
  `UNPROCESSABLE_ENTITY` is deprecated).
