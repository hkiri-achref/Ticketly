---
date: 2026-09-22T08:37:12+00:00
git_commit: ca8ccd7597a6e916c177e76378d40d8da10642a1
branch: main
topic: "Quality gates, CI workflows, and implement-feature skill upgrade"
tags: [plan, tooling, ci, checkstyle, pmd, jacoco, skills]
status: ready
---

# PLAN: Quality gates, CI workflows, and implement-feature skill upgrade

Introduce enforceable quality gates for every Ticketly microservice — Checkstyle,
PMD, and a 90% JaCoCo coverage check wired into `./mvnw verify` — plus a
path-filtered GitHub Actions workflow per service that must pass before merging
to `main`. Retrofit the existing catalog-service code and tests to the new rules,
and upgrade the `/implement-feature` skill to create a `feat/F-XX-<slug>` branch
before coding, commit with Conventional Commits, and treat the gates and the
`given_when_then` test convention as part of its Definition of Done.

## Acceptance Criteria

- `./mvnw verify` in `catalog-service` runs Checkstyle (adapted Google rules),
  PMD, unit + integration tests, and fails when instruction coverage < 90%
  (Application/config boilerplate excluded).
- All existing catalog-service main code passes Checkstyle and PMD with zero
  violations and zero suppressions.
- All existing tests are renamed to `given_<state>_when_<action>_then_<outcome>`
  and restructured into three visible sections (`// given`, `// when`, `// then`);
  coverage is ≥ 90%.
- A GitHub Actions workflow per microservice (in root `.github/workflows/`,
  change-detection inside the job) runs full `./mvnw verify` on PRs and pushes
  to `main`; `main` is protected so the check must pass before merge, and
  docs-only PRs are not blocked.
- `/implement-feature` creates `feat/F-XX-<plan-slug>` (or `fix/...`) before
  writing any code, commits with Conventional Commits format, and verifies the
  quality gates before declaring a feature done.
- Shared configs live at the repo root (`config/checkstyle/`, `config/pmd/`) and
  a documented recipe exists for wiring a new service into the same gates.

## Technical Key Decisions and Tradeoffs

1. **Clean-code tool: PMD (`maven-pmd-plugin`):** static source analysis for
   unused code, complexity, and design smells.
   - Why: pure Maven plugin, no external service/token, tunable per rule; the
     best learning-project fit vs SpotBugs (bytecode bugs) or SonarCloud (SaaS).
   - Impact: a `config/pmd/ruleset.xml` at repo root; `pmd:check` bound to the
     build and failing on violations.
2. **Coverage: JaCoCo 90% instruction coverage with exclusions:** counted across
   unit + integration tests during `verify`.
   - Why: instruction coverage is the least gameable JaCoCo counter; excluding
     `*Application` and `config/*` avoids testing Spring boilerplate for the metric.
   - Impact: `jacoco-maven-plugin` with a `check` execution; exclusions listed in
     the plugin config, not scattered annotations.
3. **Checkstyle: `google_checks.xml` adapted, vendored at repo root:** copy the
   Google ruleset into `config/checkstyle/checkstyle.xml` and modify it.
   - Why: industry-recognizable baseline; the codebase uses tabs and compact
     Javadoc, so the stock file would flag nearly every line.
   - Impact: remove `FileTabCharacter`, set `tabWidth` to 4 on the `Checker`,
     adapt `Indentation` accordingly, relax mandatory-Javadoc modules, and allow
     underscores in test method names via a `MethodName` override scoped to
     `src/test` (separate suppressions or a test-specific format).
4. **Retrofit now, no baseline:** existing F-01/F-02 code is brought up to the
   rules in this plan.
   - Why: gates that start red get suppressed and rot; green-from-day-one keeps
     them hard.
   - Impact: existing tests are renamed/restructured and a `VenueService` unit
     test is added to reach 90%.
5. **Branch/commit convention: `feat/F-XX-<plan-slug>` + Conventional Commits:**
   enforced by the updated skill, not by tooling (no commit-lint hook for now).
   - Why: consistent story between branch names and commit messages; enables
     changelog tooling later without adding infra today.
   - Impact: skill derives the branch name from the plan filename
     (`docs/plans/F-02-venues-crud.md` → `feat/F-02-venues-crud`).
6. **CI: one workflow file per service in root `.github/workflows/`, full
   `./mvnw verify`:** merge gate via branch protection.
   - Why: GitHub only reads workflows from the root; full `verify` means zero
     drift between CI and local (ubuntu runners have Docker for Testcontainers).
   - Impact: `catalog-service-ci.yml` now; copy-and-rename recipe for future
     services; a `gh api` call configures required status checks on `main`.
   - Gotcha handled: a required status check combined with workflow-level
     `paths:` filters would leave PRs that don't touch the service (docs-only,
     other services) blocked forever — the check never reports. So the workflow
     triggers on **all** PRs and does change detection **inside** the job
     (`dorny/paths-filter`); when the service is untouched the job exits early
     as success, satisfying the required check.

## Current State

```
Ticketly-project/                       (GitHub: hkiri-achref/Ticketly)
├── .claude/skills/
│   ├── plan-feature/SKILL.md           # discuss → "approved" → docs/plans/
│   └── implement-feature/SKILL.md      # 7 steps; no branch step, no quality gates
├── .github/                            # does not exist
├── config/                             # does not exist
├── catalog-service/
│   ├── pom.xml                         # Boot 4.1.1, Java 25; only spring-boot-maven-plugin
│   └── src/
│       ├── main/java/com/ticketly/catalog/…   # F-01/F-02 code (tabs, comment-rich)
│       └── test/java/com/ticketly/catalog/
│           ├── api/VenueControllerTest.java        # createReturns201WithLocationAndBody()
│           ├── persistence/VenueRepositoryTest.java
│           ├── CatalogServiceApplicationTests.java
│           ├── TestCatalogServiceApplication.java
│           └── TestcontainersConfiguration.java
└── CLAUDE.md                           # conventions; Definition of Done has no gates
```

Tests today: descriptive camelCase names, no given/when/then sections, no
dedicated `VenueService` unit test. Nothing enforces style, coverage, or
branch discipline; work happens directly on `main`.

## Desired End State

```
Ticketly-project/
├── .github/workflows/
│   └── catalog-service-ci.yml          # all PRs + main push; in-job path filter; ./mvnw verify
├── config/
│   ├── checkstyle/checkstyle.xml       # adapted google_checks (tabs, relaxed Javadoc)
│   ├── checkstyle/suppressions.xml     # test-scoped relaxations (underscore names)
│   └── pmd/ruleset.xml                 # curated PMD categories
├── catalog-service/pom.xml             # + checkstyle, pmd, jacoco plugins (gates bound to verify)
├── .claude/skills/implement-feature/SKILL.md   # branch step, commits, gates, test convention
├── CLAUDE.md                           # conventions + DoD updated
└── docs/adr/                           # short note: how to wire a new service into the gates
```

`./mvnw verify` = compile → checkstyle:check → pmd:check → unit tests →
integration tests → jacoco check (≥ 90%). The same command runs in CI; a PR
cannot merge to `main` unless the service's check passes.

## Abstractions and Code Reuse

Config is shared by **path reference**, not duplication: each service's
`pom.xml` points at `../config/checkstyle/checkstyle.xml` and
`../config/pmd/ruleset.xml`. Plugin versions are declared per service pom for
now (no parent aggregator exists); when a second service arrives, hoisting into
a shared parent pom can be revisited.

- `config/`
  - `checkstyle/checkstyle.xml` — vendored google_checks, adapted (tabs,
    Javadoc relaxed, `tabWidth=4`)
  - `checkstyle/suppressions.xml` — allow `given_x_when_y_then_z` method names
    in `src/test/**` (suppress `MethodName` there; a test-scoped format check
    is stated in CLAUDE.md instead)
  - `pmd/ruleset.xml` — best-practices, design, error-prone categories; noisy
    rules (e.g. `LawOfDemeter`, `AtLeastOneConstructor`,
    `MethodArgumentCouldBeFinal`) excluded
- `catalog-service/pom.xml`
  - `maven-checkstyle-plugin` — `check` goal at `validate`, `failOnViolation=true`
  - `maven-pmd-plugin` — `check` goal at `verify`, `failOnViolation=true`
  - `jacoco-maven-plugin` — `prepare-agent`, `report`, and `check` (INSTRUCTION
    COVEREDRATIO ≥ 0.90); excludes `**/CatalogServiceApplication.class`,
    `**/config/**`
- `catalog-service/src/test/…`
  - `VenueControllerTest` — rename methods, add `// given / when / then` sections
  - `VenueRepositoryTest` — same
  - `application/VenueServiceTest` — new plain-Mockito unit test (no Spring
    context) covering create/get/list/not-found paths
- `.claude/skills/implement-feature/SKILL.md` — new step 0 (branch), commit
  convention, gates in verification
- `CLAUDE.md` — test convention + Definition of Done additions

## Logging & Observability

No runtime logging changes. Build-time visibility: Checkstyle/PMD violations
print in the Maven log; JaCoCo HTML report at
`catalog-service/target/site/jacoco/index.html`; CI failures surface as
required-check failures on the PR.

## Implementation

### Phase 1: Static analysis gates (Checkstyle + PMD) on catalog-service

Dependencies: None.

Create the shared rulesets, wire both plugins into the catalog-service build,
and fix every violation in existing code so the build is green with hard gates.

**Tasks**:
- [x] Create `config/checkstyle/checkstyle.xml`: vendor the google_checks.xml
      matching the chosen Checkstyle version, then adapt: set `tabWidth=4` on
      `Checker`, remove `FileTabCharacter`, adjust `Indentation`
      (basicOffset/caseIndent/etc. consistent with tab display width), relax
      `MissingJavadoc*` modules (delete or set severity `ignore`), keep line
      length 120, keep import/naming/brace rules.
- [x] Create `config/checkstyle/suppressions.xml` suppressing `MethodName` for
      files under `[\\/]src[\\/]test[\\/]`. Wire it the way google_checks does:
      in checkstyle.xml add
      `<module name="SuppressionFilter"><property name="file" value="${checkstyle.suppressions.file}" default=""/></module>`,
      and set the plugin's `suppressionsLocation` to
      `../config/checkstyle/suppressions.xml` in the pom (the plugin exposes it
      as `checkstyle.suppressions.file` by default). Avoids relative-path
      resolution against the module working directory.
- [x] Create `config/pmd/ruleset.xml` with `category/java/bestpractices.xml`,
      `category/java/design.xml`, `category/java/errorprone.xml`, excluding
      noisy rules (`LawOfDemeter`, `AtLeastOneConstructor`, `OnlyOneReturn`,
      `MethodArgumentCouldBeFinal`, `LocalVariableCouldBeFinal`,
      `ShortClassName`, `CommentRequired`, JUnit-assertion-message rules).
- [x] Add `maven-checkstyle-plugin` to `catalog-service/pom.xml`: pin the
      plugin version, override the `com.puppycrawl.tools:checkstyle` dependency
      to a current release, `configLocation=../config/checkstyle/checkstyle.xml`,
      `includeTestSourceDirectory=true`, `failOnViolation=true`, `check` goal
      bound to `validate`.
- [x] Add `maven-pmd-plugin` to `catalog-service/pom.xml`: pin version
      **≥ 3.28.0** (bundles PMD 7.17; Java 25 support landed in PMD 7.16, so no
      extra PMD dependency pinning needed), point `rulesets` at
      `../config/pmd/ruleset.xml`, `failOnViolation=true`,
      `includeTests=false`, set `<targetJdk>25</targetJdk>` explicitly (the
      default derives from `maven.compiler.source`, which the Boot parent may
      not set), `check` goal bound to `verify`.
- [x] Run `./mvnw verify` and fix every Checkstyle and PMD violation in
      existing main and test code (imports order, unused code, complexity),
      preserving the existing explanatory comments.

**Automated Verification**:
- [x] `cd catalog-service && ./mvnw verify` passes with zero Checkstyle and
      zero PMD violations.
- [x] `./mvnw checkstyle:check pmd:check` alone passes (gates run standalone).
- [x] `git grep -il "suppress" catalog-service/src` returns nothing (no inline
      suppression annotations sneaked in — case-insensitive to catch
      `@SuppressWarnings`).

### Phase 2: Coverage gate + test retrofit

Dependencies: Phase 1 (test files change; style gates must already apply).

Add JaCoCo with the 90% check, rewrite existing tests to the naming/structure
convention, and add the missing `VenueService` unit test to clear the bar.

**Tasks**:
- [x] Add `jacoco-maven-plugin` to `catalog-service/pom.xml`, pinned to
      **≥ 0.8.14** (Java 25 class-file support; the Boot parent does not manage
      this plugin's version): `prepare-agent`
      (unit) + `report`, and a `check` execution bound to `verify` with rule
      `BUNDLE / INSTRUCTION / COVEREDRATIO ≥ 0.90`; exclude
      `**/CatalogServiceApplication.class` and `**/config/**`. (Integration
      tests currently run via Surefire/`@SpringBootTest`, so a single
      prepare-agent covers them; revisit if Failsafe is introduced.)
- [x] Rename every test method in `VenueControllerTest`,
      `VenueRepositoryTest`, and `CatalogServiceApplicationTests` to
      `given_<state>_when_<action>_then_<outcome>` (e.g.
      `given_validBody_when_postVenue_then_returns201WithLocation`) and
      restructure bodies into three sections separated by `// given`,
      `// when`, `// then` comments (for MockMvc chains, `when+then` may share
      the perform/expect chain — mark the sections anyway).
- [x] Add `@DisplayName` only where the method name alone is unreadable; keep
      tests self-explanatory.
- [x] Create `catalog-service/src/test/java/com/ticketly/catalog/application/VenueServiceTest.java`:
      plain JUnit + Mockito (`@ExtendWith(MockitoExtension.class)`, no Spring
      context) covering create, getById found/not-found, and list paths, in the
      same given/when/then convention.
- [x] Run `./mvnw verify`; if coverage < 90%, inspect
      `target/site/jacoco/index.html` and add targeted tests (e.g.
      `ApiExceptionHandler` branches) until the gate passes.

**Automated Verification**:
- [x] `cd catalog-service && ./mvnw verify` passes including the JaCoCo check.
- [x] Every `@Test` method matches the convention (sections use camelCase, so
      `given_validBody_when_postVenue_then_returns201`):
      `grep -rhE "^\s*(public )?void [a-zA-Z]" catalog-service/src/test | grep -vE "void given_[a-zA-Z0-9]+_when_[a-zA-Z0-9]+_then_[a-zA-Z0-9]+"`
      prints nothing (all test-class methods are `@Test`/lifecycle methods; if
      a private helper trips this, it's `static` or returns non-void and won't
      match).
- [x] Every test file contains all three section markers:
      `for m in "// given" "// when" "// then"; do grep -rL --include="*Test*.java" "$m" catalog-service/src/test; done`
      prints nothing.

### Phase 3: CI workflow + merge protection

Dependencies: Phase 2 (CI runs the full gated build; it must be green).

**Tasks**:
- [ ] Create `.github/workflows/catalog-service-ci.yml`. IMPORTANT: no
      workflow-level `paths:` filter on `pull_request` — a required check that
      never triggers blocks unrelated PRs forever. Instead the job always runs,
      detects changes itself, and exits early with success when the service is
      untouched:
      ```yaml
      name: catalog-service CI
      on:
        pull_request:
        push:
          branches: [main]
      concurrency:
        group: catalog-ci-${{ github.ref }}
        cancel-in-progress: true
      jobs:
        verify:
          runs-on: ubuntu-latest
          steps:
            - uses: actions/checkout@v5
            - uses: dorny/paths-filter@v3
              id: changes
              with:
                filters: |
                  service:
                    - 'catalog-service/**'
                    - 'config/**'
                    - '.github/workflows/catalog-service-ci.yml'
            - if: steps.changes.outputs.service == 'true'
              uses: actions/setup-java@v5
              with: { distribution: temurin, java-version: "25", cache: maven }
            - if: steps.changes.outputs.service == 'true'
              run: ./mvnw --batch-mode verify
              working-directory: catalog-service
      ```
      (Temurin 25 is available in setup-java; Docker is preinstalled on
      ubuntu-latest so Testcontainers works. Skipped steps still leave the
      `verify` job green, satisfying the required check on docs-only PRs.)
- [ ] Push the branch, open a test PR touching `catalog-service/`, and confirm
      the `verify` job runs and passes; also confirm a docs-only commit lets
      the job succeed via the early skip.
- [ ] Protect `main` with the full payload the PUT endpoint requires (all four
      top-level keys must be present or it returns 422), `enforce_admins: true`
      so the solo admin cannot merge past red checks:
      ```bash
      gh api repos/hkiri-achref/Ticketly/branches/main/protection -X PUT --input - <<'EOF'
      {
        "required_status_checks": { "strict": true, "contexts": ["verify"] },
        "enforce_admins": true,
        "required_pull_request_reviews": null,
        "restrictions": null,
        "allow_force_pushes": false
      }
      EOF
      ```
      Use the exact check context name as reported on the test PR; record the
      command in the ADR from the next task.
- [ ] Write `docs/adr/ADR-XXX-quality-gates.md` (next free number): decisions
      (Checkstyle/PMD/JaCoCo/CI) and the **new-service recipe**: copy the three
      plugin blocks into the new pom (adjust `../config` path), copy
      `catalog-service-ci.yml` → `<service>-ci.yml` renaming the job, the
      concurrency group, and the in-job filter paths, add the new check context
      to the branch-protection `contexts` array.

**Automated Verification**:
- [ ] `gh pr checks <test-pr>` shows the `verify` check green.
- [ ] `gh api repos/hkiri-achref/Ticketly/branches/main/protection --jq '.required_status_checks.contexts'`
      lists the catalog-service check.

**Manual Verification**:
- [ ] On the test PR, the GitHub UI shows merging blocked until the required
      check passes.

### Phase 4: Skill + docs update

Dependencies: Phases 1–3 (the skill references gates and CI that must exist).

**Tasks**:
- [ ] Update `.claude/skills/implement-feature/SKILL.md`:
      - New **Preconditions step 3**: derive the branch name from the plan
        filename — `feat/F-XX-<slug>` (or `fix/F-XX-<slug>` for bug-fix plans);
        if not already on it, `git switch -c` it from up-to-date `main`; never
        implement on `main`.
      - New **Process rule**: commits use Conventional Commits —
        `<type>(<service>): <imperative summary>` (e.g.
        `feat(catalog): add venue CRUD endpoints`), body optional; one logical
        change per commit.
      - Extend the test step: all new tests follow
        `given_<state>_when_<action>_then_<outcome>` naming with
        `// given / when / then` sections.
      - Extend the final verification step: `./mvnw verify` must pass in the
        touched service (Checkstyle, PMD, tests, coverage ≥ 90%) before the
        plan is marked `implemented`; end by pushing the branch and offering to
        open a PR (merge happens via PR, gated by CI).
- [ ] Update `CLAUDE.md`:
      - Conventions: add the test naming/structure rule, branch naming, and
        Conventional Commits.
      - Definition of done: add "quality gates pass (`./mvnw verify`:
        Checkstyle, PMD, coverage ≥ 90%)" and "work merged via PR from a
        `feat/F-XX-*` branch with green CI".
- [ ] Update `.claude/skills/plan-feature/SKILL.md` only if it names test
      conventions (quick check; expected: no change needed).

**Automated Verification**:
- [ ] `grep -q "feat/F" .claude/skills/implement-feature/SKILL.md` and
      `grep -q "given_" .claude/skills/implement-feature/SKILL.md` succeed.
- [ ] `grep -q "coverage" CLAUDE.md` succeeds.

**Manual Verification**:
- [ ] Dry-read `/implement-feature` output on the next feature: it creates the
      branch before writing code and refuses to work on `main`.

## Implementation Notes

During implementation, document user feedback, problems, and decisions here.

- Branch: `chore/quality-gates-and-ci` (tooling change, not a feature, so no `feat/F-XX` prefix).
- Checkstyle 14.1.0's `google_checks.xml` already ships `GoogleMethodName` plus an XPath
  filter allowing `a_b_c` names on `@Test` methods; `suppressions.xml` still suppresses
  `MethodName` (regex also matches `GoogleMethodName`) under `src/test` so lifecycle/helper
  methods are covered too.
- maven-pmd-plugin 3.28.0 bundles PMD 7.17, whose ASM cannot read the JDK 26 class files on
  the dev machine (`Unsupported class file major version 70`). Overrode the four PMD modules
  to 7.27.0 in the plugin's `<dependencies>`.
- PMD exclusions beyond the plan: `DataClass` (fires on every JPA entity) and
  `LoosePackageCoupling` (warns when unconfigured). The JUnit rule names in the plan were
  renamed to `UnitTest*` in PMD 7 and PMD only scans main sources, so they were dropped.
- `NullAssignment` hits in `CreateVenueRequest` compact constructors were fixed in code
  (`if (x != null) x = x.strip()`) rather than excluded.
- Remaining build warning: PMD's "Adding current platform jrt-fs.jar ... could be the wrong
  java version" appears only because the local JDK (26) differs from `targetJdk` 25; it
  needs a Maven toolchains file to silence, and does not occur on CI (Temurin 25).
- Phase 2: coverage landed at 96.9% instruction coverage (342/353). Added `PingControllerTest`
  (F-01 endpoint had no test) besides the planned `VenueServiceTest`. Learned: slice tests
  ignore `@ConfigurationPropertiesScan`, so the ping slice needs
  `@EnableConfigurationProperties(CatalogProperties.class)`.
- The section-marker grep in the plan uses `*Test*.java`, which also matches the two
  support classes `TestcontainersConfiguration` and `TestCatalogServiceApplication`; with
  `--include="*Test.java" --include="*Tests.java"` it prints nothing, as intended.

## References

- `.claude/skills/implement-feature/SKILL.md` — skill being upgraded
- `catalog-service/pom.xml` — build to be gated
- Google Checkstyle config: https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/google_checks.xml
- PMD Java rule categories: https://docs.pmd-code.org/latest/pmd_rules_java.html
- JaCoCo check rules: https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html
- GitHub branch protection API: https://docs.github.com/en/rest/branches/branch-protection
