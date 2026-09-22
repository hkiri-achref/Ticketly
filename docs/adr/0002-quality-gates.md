# ADR 0002 — Quality gates enforced by the build and by CI

Date: 2026-09-22
Status: Accepted

## Context

Ticketly is a learning project preparing for senior interviews, so the code
must look like code a senior would ship: consistent style, no dead code, real
test coverage, and a merge process that cannot be bypassed on a bad day. Until
now nothing enforced any of this — work happened directly on `main`, tests
had ad-hoc names, and no tool checked style or coverage.

## Decision

Every service runs four gates inside plain `./mvnw verify`, in this order:

| Gate | Tool | Bound to | Rule |
|---|---|---|---|
| Style | `maven-checkstyle-plugin` 3.6.0 + Checkstyle 14.1.0 | `validate` | `config/checkstyle/checkstyle.xml` (Google rules adapted: tabs, 120 cols, no mandatory Javadoc), severity `error`, main **and** test sources |
| Clean code | `maven-pmd-plugin` 3.28.0 + PMD 7.27.0 | `verify` | `config/pmd/ruleset.xml` (best-practices, design, error-prone minus `LawOfDemeter`, `DataClass`, `LoosePackageCoupling`), main sources only |
| Tests | Surefire | `test` | JUnit 5 unit + Spring slice + Testcontainers integration tests |
| Coverage | `jacoco-maven-plugin` 0.8.15 | `verify` | INSTRUCTION coverage ≥ 90% per module; `*Application` and `config/**` excluded |

Rulesets live once at the repo root under `config/` and are referenced by
relative path from each service pom, so all services share one definition.
Inline suppressions (`@SuppressWarnings`, `// NOPMD`, `CHECKSTYLE.OFF`) are
not used; if a rule is wrong for this codebase it is removed from the shared
ruleset with a comment explaining why. The only file-scoped relaxation is
`config/checkstyle/suppressions.xml`, which lets test methods be named
`given_<state>_when_<action>_then_<outcome>`.

Tests follow that naming and are structured in three visible sections
(`// given`, `// when`, `// then`).

CI is one GitHub Actions workflow per service in `.github/workflows/`,
running the same `./mvnw verify` on every pull request and on pushes to
`main`. `main` is protected: the service check must pass and even admins
cannot merge past a red check. The workflow has **no** workflow-level `paths:`
filter — a required check that never triggers would block unrelated PRs
forever — so the job always starts, detects changes with `dorny/paths-filter`,
and exits green early when the service is untouched.

Branches are named `feat/F-XX-<plan-slug>` (or `fix/...`, `chore/...`), and
commits follow Conventional Commits: `<type>(<service>): <imperative summary>`.

Why these tools: Checkstyle and PMD are pure Maven plugins (no SaaS, no
token, tunable per rule); JaCoCo instruction coverage is the least gameable
counter; a full `verify` in CI means zero drift between CI and a laptop.

## Consequences

- A red gate blocks the merge; fix the code or fix the shared rule, never
  suppress locally.
- Integration tests need Docker, on the laptop and on `ubuntu-latest`.
- Plugin versions are pinned per service pom because the Boot parent does not
  manage them; hoist into a shared parent pom once a second service exists.
- PMD prints a "current platform jrt-fs.jar" warning when the local JDK is
  newer than `targetJdk` 25; it is harmless and absent on CI.

## Recipe: wiring a new service into the gates

1. **pom.xml** — copy the `<properties>` version pins and the three plugin
   blocks (`maven-checkstyle-plugin`, `maven-pmd-plugin`, `jacoco-maven-plugin`)
   from `catalog-service/pom.xml`. Keep the `../config/...` paths (same depth),
   and change the JaCoCo exclusion to the new `**/<Name>Application.class`.
2. **Workflow** — copy `.github/workflows/catalog-service-ci.yml` to
   `<service>-ci.yml`; rename the workflow `name`, the concurrency group
   (`<service>-ci-${{ github.ref }}`), the `working-directory`, and the three
   filter paths (`<service>/**`, `config/**`, the workflow file itself). Keep
   the job id `verify`.
3. **Branch protection** — the required check is reported as
   `<workflow name> / verify` (see below); add it to the `contexts` array:

   ```bash
   gh api repos/hkiri-achref/Ticketly/branches/main/protection -X PUT --input - <<'EOF_JSON'
   {
     "required_status_checks": {
       "strict": true,
       "contexts": ["verify"]
     },
     "enforce_admins": true,
     "required_pull_request_reviews": null,
     "restrictions": null,
     "allow_force_pushes": false
   }
   EOF_JSON
   ```

   All four top-level keys must be present (the endpoint returns 422
   otherwise). Use the exact context name shown on a PR's checks tab; with
   several services, list one context per service.
4. **Run** `./mvnw verify` in the new service: it must be green before the
   first PR, with zero suppressions.
