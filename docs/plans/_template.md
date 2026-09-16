---
feature: F-XX
title: <short title>
status: draft            # draft | approved | implemented
approved_on:
requirements: REQUIREMENTS.md#F-XX
depends_on: []
---

# F-XX — <title>

## Goal (in my words)
2–3 sentences: the business outcome and why it matters.

## Scope
- In:
- Out (explicitly):

## Design
| Component (file/class) | Responsibility | Concept practiced |
|---|---|---|
| | | |

### Data & migration
Tables, columns, constraints, indexes, `V<n>__description.sql`.

### Contracts
Endpoints (method, path, request/response records, status codes) and/or Kafka events touched.

### Transactions & concurrency
Where @Transactional sits, locking strategy, idempotency, behaviour on failure.

## Alternatives rejected
- Option → why not (one line each).

## Test plan
| Acceptance criterion (from REQUIREMENTS) | Test type | Test class/method |
|---|---|---|
| | | |

## Implementation steps
- [ ] 1.
- [ ] 2.

## Learning focus
Concepts to watch for, and the two interview questions this feature should let me answer.

## Deviations
(filled during implementation: what changed vs. the plan and why)