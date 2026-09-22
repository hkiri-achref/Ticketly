---
feature: F-02
title: Venues CRUD
status: implemented
approved_on: 2026-09-17
requirements: REQUIREMENTS.md#F-02
depends_on: [F-01]
---

# F-02 — Venues CRUD

## Goal (in my words)
Organizers register the physical places where events happen; one venue is reused
by many events (F-03 links `Event` to it). First real vertical slice of
catalog-service: entity → repository → service → controller with validation and
ProblemDetail error handling. Despite the title, scope is create + read only.

## Scope
- In: `Venue` entity + `Address` embeddable record, `V2__venues.sql`,
  `VenueRepository`, `VenueService`, `CreateVenueRequest`/`VenueResponse`,
  `VenueController` (POST, GET by id, GET list by city, paged),
  `ApiExceptionHandler` (400 validation + 404), slice tests, http collection.
- Out (explicitly): update/delete endpoints, security (F-06 — leave TODO),
  `CreateVenueCommand` (commands start in F-03), Kafka, domain exception
  hierarchy, idempotency/unique constraint on name.

## Design
| Component (file/class) | Responsibility | Concept practiced |
|---|---|---|
| `domain/Venue` | class: private fields, `protected` no-arg ctor, public ctor sets `UUID.randomUUID()` + `createdAt`, ID-only equals/hashCode, no setters | why entities are classes §6.1 |
| `domain/Address` | `@Embeddable record Address(String street, String city, String country)` | Hibernate 7 record embeddables: 1 field → 3 columns |
| `persistence/VenueRepository` | `JpaRepository<Venue, UUID>` + `Page<Venue> findByAddressCityIgnoreCase(String, Pageable)` | derived query walking into an embeddable |
| `application/VenueService` | `@Service @Transactional(readOnly = true)`; `create` (`@Transactional`), `getById`, `list(city, pageable)`; `orElseThrow(EntityNotFoundException::new)` | tx boundary on service, never controller |
| `api/CreateVenueRequest` | record; `@Size(min=3,max=120)` name, `@Min(1) @Max(200000)` capacity, nested `@Valid` address record with `@NotBlank` fields; compact ctor `strip()`s | validation on record components; compact ctor normalization |
| `api/VenueResponse` | record + static `from(Venue)` | §6.3 hand-mapping boundary |
| `api/VenueController` | POST → 201 + `Location` (ServletUriComponentsBuilder), GET `{id}`, GET `?city=` + `Pageable` | `Pageable` resolved from query params |
| `api/ApiExceptionHandler` | `@RestControllerAdvice`: 400 ProblemDetail + `errors[]` (`MethodArgumentNotValidException`), 404 (`EntityNotFoundException`) | RFC-7807 `ProblemDetail` |
| config | `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` | stable `PagedModel` JSON instead of unstable `PageImpl` |

### Data & migration
`V2__venues.sql` (never touch applied `V1__init.sql`):
```
venues(
  id uuid primary key,
  name varchar(120) not null,
  street varchar(255) not null, city varchar(120) not null, country varchar(120) not null,
  capacity int not null check (capacity between 1 and 200000),
  created_at timestamptz not null
);
create index idx_venues_city_lower on venues (lower(city));
```
Check constraint mirrors bean validation on purpose: API edge vs data safety.

### Contracts
- `POST /api/v1/venues` → 201 + `Location: /api/v1/venues/{id}`, body `VenueResponse`; 400 ProblemDetail on invalid input.
- `GET /api/v1/venues/{id}` → 200 / 404 ProblemDetail.
- `GET /api/v1/venues?city=&page=&size=` → 200 `PagedModel` of `VenueResponse`; no `city` → all venues paged.
- No Kafka. Security off until F-06 (TODO comment).
⚠ Verify in Boot 4.1 / Spring Data 2025 docs that `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` is unchanged (introduced Boot 3.3).

### Transactions & concurrency
Class-level `@Transactional(readOnly = true)` on `VenueService`, overridden
read-write on `create`. No `@Version` (per §7.1) — no update endpoint, so no
lost-update risk. Duplicate POSTs create duplicate venues by design (no natural
key required by spec).

## Alternatives rejected
- `CreateVenueCommand` now → field-for-field copy of the request here; introduce commands in F-03 where the aggregate makes them meaningful.
- Custom `VenueNotFoundException` → spec names jakarta's `EntityNotFoundException`; hierarchy can wait.
- MapStruct → §6.3: hand mapping first to feel the cost.
- DB-generated ids → §5.3 mandates app-generated UUIDs; entity valid at construction.
- Raw `Page` JSON → Spring warns it is an unstable contract.
- `getReferenceById` for 404 → lazy proxy throws far from the call site; explicit `orElseThrow` is clearer.

## Test plan
| Acceptance criterion (from REQUIREMENTS) | Test type | Test class/method |
|---|---|---|
| `Address` maps to 3 columns and round-trips | `@DataJpaTest` + Testcontainers (`@ServiceConnection` postgres:17, `replace = NONE`) | `VenueRepositoryTest`: persist → flush/clear → reload, assert record equality; raw `JdbcTemplate` asserts the 3 physical columns; Flyway runs in the slice so migration ↔ entity match is proven |
| Validation returns ProblemDetail with `errors[]` | `@WebMvcTest(VenueController.class)` + `@MockitoBean VenueService` | `VenueControllerTest`: invalid POST → 400 `application/problem+json` + `errors[]`; also 201+Location and 404 paths |
| Manual: create → get → list by city via Swagger | manual | Swagger UI + `http/catalog.http` |

## Implementation steps
- [x] 1. `V2__venues.sql`.
- [x] 2. `Address` record + `Venue` entity.
- [x] 3. `VenueRepository`; `VenueRepositoryTest` (`@DataJpaTest`) green.
- [x] 4. `VenueService` + request/response records.
- [x] 5. `VenueController` + `ApiExceptionHandler` + page-serialization config; `VenueControllerTest` green.
- [x] 6. `./mvnw verify`; manual Swagger pass; update `http/catalog.http`.
- [x] 7. Learning note `docs/learning-notes/F-02.md`; set plan `status: implemented`.

## Learning focus
Entity-as-class vs record-as-value in practice; `@Embeddable` records;
derived queries over embeddables; `Pageable`/`PagedModel`; `ProblemDetail`;
slice tests (`@DataJpaTest`, `@WebMvcTest` + `@MockitoBean`).
Interview questions: "Why can't a JPA entity be a record, and where do records
belong instead?" and "How do you return RFC-7807 errors from Spring and keep
paginated JSON a stable contract?"

## Deviations
- Boot 4.1 moved slice-test annotations to per-module packages:
  `o.s.boot.data.jpa.test.autoconfigure.DataJpaTest`,
  `o.s.boot.webmvc.test.autoconfigure.WebMvcTest`. Verified in the 4.1.1 jars.
- `@AutoConfigureTestDatabase(replace = NONE)` dropped: since Boot 3.4 the
  test-database replacement backs off automatically when a `@ServiceConnection`
  container is present.
- `TestEntityManager` no longer exists in the Boot 4.1 test jars — used a plain
  injected `jakarta.persistence.EntityManager` for flush/clear instead.
- Index is `upper(city)` not `lower(city)`: Spring Data's `IgnoreCase` derived
  query compares with `upper()`, confirmed in the logged SQL — a `lower()`
  index would never be used.
- `TestcontainersConfiguration` widened to `public` so tests in subpackages can
  `@Import` it.
- ⚠ from plan resolved: `@EnableSpringDataWebSupport(pageSerializationMode =
  VIA_DTO)` exists unchanged in Spring Data Commons 4.1.1; page JSON verified
  as `{content: [...], page: {...}}` both in the slice test and live.
