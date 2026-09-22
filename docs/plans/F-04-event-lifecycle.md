---
feature: F-04
title: Event lifecycle: publish and cancel
status: implemented
approved_on: 2026-09-22
requirements: REQUIREMENTS.md#F-04
depends_on: [F-03]
---

# F-04 — Event lifecycle: publish and cancel

## Goal (in my words)
An event stays a draft until the organizer explicitly publishes it, and only a
sellable event (≥1 tier, starts in the future) can be published. Cancelling is
explicit, needs a reason, and is final. Two concurrent modifications of the
same event must not overwrite each other: the loser gets a 409.

## Scope
- In: `TransitionResult` sealed type + `RejectionReason` enum, `Event.publish()`
  / `cancel(reason)`, `@Version`, cancellation columns, `V4__event_lifecycle.sql`,
  `CancelEventCommand`, service methods, `CancelEventRequest`, two POST
  endpoints, exhaustive switch in the controller, 409 handler, CANCELLED guard
  on `update` / `addTier`, tests, http collection, OpenAPI.
- Out (explicitly): Kafka `EventPublished` (F-15, TODO only), real organizer
  (F-07), security (F-06), `If-Match` / ETag handling (learning note only),
  DRAFT-only guard on PUBLISHED events (published events stay editable).

## Design
| Component (`<layer>/<aggregate>/<Class>`) | Responsibility | Concept practiced |
|---|---|---|
| `domain/event/TransitionResult` | `sealed interface TransitionResult permits Ok, Rejected`; nested `record Ok(Event event)`, `record Rejected(RejectionReason reason, EventStatus currentStatus)` | sealed interface, records as result type (§6.2) |
| `domain/event/RejectionReason` | enum `NO_TIERS, STARTS_IN_PAST, ALREADY_PUBLISHED, ALREADY_CANCELLED`, each with a message | enum carrying data |
| `domain/event/Event` | `publish()`: DRAFT + ≥1 tier + startsAt in future → PUBLISHED, else `Rejected`; `cancel(String reason)`: DRAFT/PUBLISHED → CANCELLED, sets `cancellationReason`, `cancelledAt`; CANCELLED → `Rejected(ALREADY_CANCELLED)`. Mutates only on `Ok`. `@Version private long version`. `update` / `addTier` throw `EventNotEditableException` when CANCELLED | state machine in the aggregate, `@Version` |
| `domain/event/EventNotEditableException` | extends `DomainRuleViolationException` → existing 422 handler | exception hierarchy reuse |
| `application/event/CancelEventCommand` | `record CancelEventCommand(String reason)` | command record |
| `application/event/EventService` | `publish(UUID)`, `cancel(UUID, CancelEventCommand)`: `loadWithTiers`, call domain method, return `TransitionResult`; read-write `@Transactional`, no `save`; `// TODO: F-15` emit `EventPublished` | tx boundary, dirty checking, version bump at commit |
| `api/event/CancelEventRequest` | `@NotBlank @Size(max = 500) String reason`, compact ctor `strip()`, `toCommand()` | validation on record components |
| `api/event/EventResponse` | add `cancellationReason`, `cancelledAt`, `version` | hand mapping |
| `api/event/EventController` | `POST /{id}/publish`, `POST /{id}/cancel`; one private method with exhaustive `switch` + record patterns: `case Ok(var event)` → 200, `case Rejected(var reason, var status)` → `ProblemDetail` 422 (`NO_TIERS`, `STARTS_IN_PAST`) or 409 (`ALREADY_*`); returns `ResponseEntity<Object>`; no `default` | record patterns, exhaustive switch, 409/422 semantics |
| `api/common/ApiExceptionHandler` | `ObjectOptimisticLockingFailureException` → 409 ProblemDetail "Concurrent modification", detail: reload and retry | HTTP 409 |

### Data & migration
`V4__event_lifecycle.sql`:
```sql
alter table events
  add column version bigint not null default 0,
  add column cancellation_reason varchar(500),
  add column cancelled_at timestamptz,
  add constraint chk_events_cancelled
    check ((status = 'CANCELLED') = (cancelled_at is not null and cancellation_reason is not null));
```
`default 0` back-fills existing rows (Hibernate must never see a null version).
The check makes the DB agree with the state machine.

### Contracts
- `POST /api/v1/events/{id}/publish`, no body → 200 `EventResponse`; 404;
  422 (no tiers / starts in past); 409 (already published / cancelled);
  409 (version conflict).
- `POST /api/v1/events/{id}/cancel`, body `CancelEventRequest{reason}` → 200
  `EventResponse`; 400; 404; 409 (already cancelled); 409 (version conflict).
- POST, not PATCH: named actions with side effects. 200, not 204: the client
  sees the new status and version. No Kafka yet.

### Transactions & concurrency
Service methods remain the boundary; a `Rejected` path changes nothing, so
the commit issues no UPDATE. `@Version` turns every UPDATE into
`where id = ? and version = ?`; the second writer matches zero rows, Hibernate
throws `StaleObjectStateException`, Spring translates it to
`ObjectOptimisticLockingFailureException` → 409. No retry in the service: the
tx is rolled back and the client must decide. Also closes F-03's
check-then-act gap in `addTier`.

## Alternatives rejected
- Domain exceptions for illegal transitions → spec asks for the sealed type; discussed in the learning note.
- `Rejected(String message)` → controller would parse text to pick a status.
- Pessimistic lock → holds a DB lock across the request; conflicts are rare.
- Retry on 409 in the service → hides the conflict, second cancel reason silently wins.
- `PATCH` with a status field → cannot express "cancel needs a reason, publish does not".
- Idempotent publish → contradicts DRAFT → PUBLISHED and hides double-submits.
- Block edits on PUBLISHED → editing a live event's text or adding a tier is normal business.

## Test plan
| Acceptance criterion (from REQUIREMENTS) | Test type | Test class/method |
|---|---|---|
| Every transition (DRAFT→PUBLISHED ok; no tiers; starts in past; PUBLISHED→publish; CANCELLED→publish; DRAFT→CANCELLED; PUBLISHED→CANCELLED; CANCELLED→cancel; rejected leaves state untouched) | unit | `EventTest.given_<state>_when_<publish|cancel>_then_<ok|rejected_<reason>>` |
| update / addTier on CANCELLED throw | unit | `EventTest.given_cancelledEvent_when_update_then_throwsNotEditable` (+ addTier) |
| Service delegates and returns result; 404 | Mockito unit | `EventServiceTest` |
| Ok → 200; each reason → 409/422 ProblemDetail; blank reason → 400 | `@WebMvcTest` + `@MockitoBean` | `EventControllerTest` |
| `ObjectOptimisticLockingFailureException` → 409 | `@WebMvcTest` | `EventControllerTest.given_staleVersion_when_publish_then_409ProblemDetail` |
| Two threads, one fails with optimistic lock | `@DataJpaTest` + Testcontainers, `@Transactional(NOT_SUPPORTED)`, `TransactionTemplate` per thread, `CountDownLatch` after both loads | `EventRepositoryTest.given_twoThreadsLoadSameEvent_when_bothPublish_then_oneFailsWithOptimisticLock` |
| Version starts at 0 and increments on flush | `@DataJpaTest` | `EventRepositoryTest.given_savedEvent_when_publishedAndFlushed_then_versionIncremented` |

## Implementation steps
- [x] 1. `V4__event_lifecycle.sql`.
- [x] 2. `RejectionReason`, `TransitionResult`, `EventNotEditableException`.
- [x] 3. `Event`: `@Version`, cancellation fields, `publish`, `cancel`, CANCELLED guard + `EventTest`.
- [x] 4. `EventRepositoryTest`: version increment, two-thread optimistic lock.
- [x] 5. `CancelEventCommand`, `EventService.publish/cancel` (+ F-15 TODO) + `EventServiceTest`.
- [x] 6. `CancelEventRequest`, `EventResponse` fields, `EventController` switch, 409 handler + `EventControllerTest`.
- [x] 7. `./mvnw verify`; Swagger pass; update `http/catalog.http`.
- [x] 8. Learning note `docs/learning-notes/F-04.md` (when to prefer exceptions over a sealed result; `If-Match`); plan `status: implemented`.

## Learning focus
Sealed interfaces + record patterns + exhaustive switch; `@Version` and
optimistic locking end to end (SQL, Hibernate, Spring exception translation);
409 vs 422 semantics; state machines inside the aggregate.
Interview questions: "How does optimistic locking work in JPA and how do you
surface it to an HTTP client?" and "When would you model business outcomes as
a sealed result type instead of exceptions?"

## Deviations
None of substance. Notes for transparency:
- `RejectionReason`'s accessor is `getMessage()` (not `message()`): PMD's
  `AvoidFieldNameMatchingMethodName` fires on enums, unlike records.
- The rejected-transition `ProblemDetail` carries two extension members,
  `reason` and `currentStatus`, so a client can branch without parsing text.
- The version-increment test adds the tier BEFORE the first flush: any
  flushed change (addTier bumps `updated_at`) increments `version`, not only
  `publish()`; the first draft of the test learned this the hard way.
- The two-thread test uses `Executors.newVirtualThreadPerTaskExecutor()` in a
  try-with-resources (ExecutorService is AutoCloseable since Java 19).
- The reason → status mapping is a `@ParameterizedTest` over all four enum
  constants rather than four hand-written tests.
