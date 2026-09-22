# CI checks — how catalog-service is verified on GitHub

## What was built
`.github/workflows/catalog-service-ci.yml` no longer runs one big
`./mvnw verify`. It runs the **same Maven plugins, versions, rules and
thresholds** (all read from `catalog-service/pom.xml`) as **independent jobs**,
each with its own log, artifact and step summary, plus a CodeQL security scan.
A small standard-library Python helper, `scripts/ci/report.py`, turns the XML
and SARIF reports into GitHub summaries and file/line annotations and computes
the final `verify` verdict.

```text
changes ─┬─ checkstyle ──────────────────────┐
         ├─ tests  (package → jacoco:report → jacoco:check) ─┤
         ├─ pmd ───────────────────────────────┤── verify (required check)
         └─ security (CodeQL, java-kotlin) ─────┘
```

Locally nothing changed: `./mvnw verify` is still the complete build and the
one command to run before pushing.

## Command map (run from `catalog-service/`, CI adds `--batch-mode --no-transfer-progress`)

| Job / step | Command | What it does | Report |
|---|---|---|---|
| checkstyle | `./mvnw checkstyle:check` | style gate only, nothing compiled | `target/checkstyle-result.xml` |
| tests | `./mvnw -Dcheckstyle.skip=true package` | compile, all tests with the JaCoCo agent, Boot JAR | `target/surefire-reports/`, `target/jacoco.exec` |
| tests → coverage report | `./mvnw jacoco:report@report` | HTML + XML from `jacoco.exec` | `target/site/jacoco/` |
| tests → coverage gate | `test -s target/jacoco.exec && ./mvnw jacoco:check@check` | the pom's 90 % INSTRUCTION rule | log |
| pmd | `./mvnw -Dcheckstyle.skip=true compile pmd:check` | compiled classes for type resolution, then PMD | `target/pmd.xml` |
| security | CodeQL init → `./mvnw -Dcheckstyle.skip=true compile` → analyze | default CodeQL Java suite | `target/codeql/*.sarif` |
| local | `./mvnw verify` | everything above except CodeQL, in one go | all of the above |

Why each skip flag exists (and nothing else is skipped):
- `-Dcheckstyle.skip=true` in `tests`, `pmd`, `security`: Checkstyle is bound
  to `validate`, the first phase of every build, so without the flag a style
  error would fail those three jobs too and hide their own result. The
  `checkstyle` job owns that gate. Never set it locally.
- No `jacoco.skip`, no `pmd.skip`, no `skipTests` anywhere.
- `package` (not `verify`) in the tests job: PMD and the JaCoCo report/check
  are bound to `verify`; stopping at `package` keeps them in their own jobs.

## Things I learned

- **`goal@executionId`** (`jacoco:check@check`) runs one specific execution
  from the pom, *with its execution-level configuration*. A bare `jacoco:check`
  would ignore the `<rules>` declared inside the `check` execution and check
  nothing. Same for `jacoco:report@report`.
- **`jacoco:check` succeeds when there is no data.** With `target/jacoco.exec`
  missing it logs "Skipping JaCoCo execution due to missing execution data
  file" and returns 0. So CI asserts the file is non-empty first; the helper
  also refuses to report coverage without it. Never `clean` between the test
  step and the coverage steps: it deletes the data.
- **Plugin-level vs execution-level config** matters for CLI goals:
  `checkstyle:check` from the command line sees only the `<configuration>` at
  plugin level (ours is), not one nested in an `<execution>`.
- **`./mvnw` vs `mvn`.** The wrapper (`.mvn/wrapper/maven-wrapper.properties`,
  wrapper 3.3.4, `only-script` type) downloads exactly Maven 3.9.16 on first
  use, so a laptop, CI and a colleague all build with the same Maven. `mvn`
  would use whatever is on `PATH`. The runner has no Maven preinstalled that we
  rely on; `setup-java` only provides the JDK and caches `~/.m2/repository`
  keyed on `catalog-service/pom.xml` (never `target/`).
- **`if: always()` on the final job** is what lets it run when a dependency
  failed or was skipped; without it the job is skipped and a required check
  that is "skipped" is treated as passing by branch protection. That is why
  `verify` evaluates every `needs.<job>.result` explicitly (decision table
  below) instead of relying on "no failure".
- **`!cancelled()` on report steps**, not `continue-on-error`. The Maven step
  that owns a gate fails the job; the report step then still runs and
  explains. `continue-on-error: true` on a gate would turn red into green.
  It is used exactly once, on the best-effort SARIF *upload*, which is not a
  gate.
- **Artifacts are immutable per run** (`actions/upload-artifact@v4`): re-running
  a job with the same artifact name fails, hence `-${{ github.run_attempt }}`
  in every name.
- **Job outputs** are the only way to carry a one-line status from a job into
  another job's summary; they are passed to the final step through `env:`,
  never interpolated into the shell command, so a test message with a quote
  cannot break or inject into the step.
- **Workflow-command escaping** (`::error file=…,line=…::msg`) needs `%`,
  `\r`, `\n` escaped in the message and additionally `:` and `,` in the
  properties; Markdown cells need `|` and friends escaped. The helper does both
  and caps inline output at 40 rows; the full report is always in the artifact.
- **XML parsing**: the helper refuses any `<!ENTITY` declaration (the XXE
  vector) but allows the DOCTYPE JaCoCo writes; expat does not fetch external
  DTDs unless a handler is installed.

## Final `verify` decision table

| Change detection | Relevant change | Required jobs | `verify` |
|---|---|---|---|
| success | no | all skipped | **PASS** "catalog-service untouched, required checks intentionally skipped" |
| success | yes | all success | **PASS** |
| success | yes | any failure / cancelled / skipped | **FAIL** naming the job |
| failure / cancelled / output not `true`/`false` | unknown | any | **FAIL** |

A cancelled run (a newer push) may cancel `verify` too; it is then reported as
cancelled, never as success. `workflow_dispatch` forces "relevant = yes".

## Security scan (CodeQL)

- Scanner: `github/codeql-action` v4.38.1, pinned to its commit SHA, default
  Java query suite, `java-kotlin`, manual build mode, `source-root`
  `catalog-service`. The bundled CodeQL CLI supports Java 7–26.
- Policy: any finding with SARIF `security-severity` ≥ 7.0 (high/critical)
  fails the job. Findings without that metadata block when their SARIF level
  is `error`. Lower severities are listed in the summary with a link to the
  query help but do not block. A failed or missing scan, an unsuccessful
  invocation or a malformed SARIF is a failure: a green scan is never assumed.
- Eligibility: code scanning is free on public repositories, which this is.
  SARIF upload to the Security tab is best-effort (`continue-on-error` on that
  step only); if the repository ever enables *default* code scanning setup,
  the upload is rejected but the gate above still runs on the SARIF artifact.
- Fork PRs: `GITHUB_TOKEN` is read-only, so the upload step is skipped; the
  scan, the gate and the SARIF artifact still run and still block.
- Local `./mvnw verify` does **not** run CodeQL; it covers the Maven gates only.

## Reading a failed run

1. Open the PR's *Checks* tab: each job is listed separately; the `verify`
   summary shows one line per check (Checkstyle, Tests, Coverage, PMD, Security).
2. Click the failed job: its *Summary* tab lists rule names with file:line
   (also shown as annotations on the *Files changed* tab).
3. Need the full report? *Artifacts* at the bottom of the run page:
   `checkstyle-report-*`, `test-reports-*`, `coverage-report-*` (open
   `index.html`), `pmd-report-*`, `security-sarif-*`. Retention 14 days.
4. Reproduce locally with the command from the map above, or `./mvnw verify`.

## Maintenance

- A new Maven execution or a changed output path in the pom must be mirrored
  in the workflow; the pom comment above the quality-gates block says so.
  A split build cannot promise that future pom changes appear in CI by
  themselves.
- Helper tests: `python3 -m unittest discover scripts/ci/tests` (fixtures for
  passing/failing/missing/malformed reports, special characters, every row of
  the decision table, and SARIF severity handling).
- Rollback: `git revert` the workflow commit restores the single-job
  `./mvnw verify` workflow; the job is still named `verify`, so the required
  check on `main` keeps working.
