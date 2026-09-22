---
name: implement-feature
description: Implement one Ticketly feature from its approved plan in docs/plans/.
argument-hint: [feature-id]
disable-model-invocation: true
---

We are IMPLEMENTING feature $ARGUMENTS of Ticketly from its approved plan.

## Preconditions
1. Find `docs/plans/$ARGUMENTS-*.md`. If it does not exist or `status` is not `approved`,
   stop and tell me to run `/plan-feature $ARGUMENTS`.
2. Read the plan in full. Read `REQUIREMENTS.md` only for the acceptance criteria of
   `#### $ARGUMENTS`; you will verify against them at the end.
3. **Branch before any code.** Derive the branch name from the plan filename:
   `docs/plans/F-02-venues-crud.md` → `feat/F-02-venues-crud` (use `fix/F-XX-<slug>` when
   the plan is a bug fix). If not already on that branch: `git switch main && git pull`,
   then `git switch -c <branch>`. Never write application code while on `main`; if you
   find yourself there, stop and create the branch first.

## Process
1. Show me the plan's "Implementation steps" checklist and confirm the first step.
   Then work step by step, ticking each box in the plan file as you complete it.
2. Follow the plan. If you must deviate (an API does not exist in Boot 4.1, the plan missed
   something), STOP, explain, get my OK, then record it under "## Deviations" in the plan.
3. For every modern Java or Spring Boot 4 feature used, add a 1–3 line comment explaining WHY.
   **Package placement:** every new class goes into `<layer>/<aggregate>/` (or `<layer>/common/`
   when shared), exactly as the plan's Design table states — never directly under `api/`,
   `application/`, `domain/` or `persistence/`. Tests mirror the main package. If the plan
   gives no package for a component, STOP and ask rather than guessing. `ArchitectureTest`
   (ArchUnit) fails `verify` on a class at a layer root or on an upward dependency
   (e.g. a service importing a request record): fix the placement, never the rule.
4. Commit as you go, one logical change per commit, in Conventional Commits format:
   `<type>(<service>): <imperative summary>` — e.g. `feat(catalog): add venue CRUD endpoints`,
   `test(catalog): cover venue not-found path`, `build(catalog): bump springdoc`.
   Types: `feat`, `fix`, `test`, `refactor`, `build`, `ci`, `docs`, `chore`. Body optional.
5. Run the tests named in the plan's test plan. Fix failures before moving on.
   Every new test is named `given_<state>_when_<action>_then_<outcome>` (camelCase inside
   each segment) and its body has three visible sections: `// given`, `// when`, `// then`.
6. When all steps are done, verify each acceptance criterion from REQUIREMENTS.md and
   report ✅ or ❌ per item, with the test that proves it.
7. Run the quality gates in the touched service: `./mvnw verify` must be green —
   Checkstyle, PMD, all tests, JaCoCo instruction coverage ≥ 90% — with zero warnings and
   zero inline suppressions. If a gate is red, fix the code (or, with my OK, adjust the
   shared rule under `config/` and say why); never suppress locally.
8. Only then set the plan's `status: implemented`, and draft
   `docs/learning-notes/$ARGUMENTS.md` with: what was built, concepts practiced, two
   interview questions, and one production concern deliberately simplified.
9. Push the branch (`git push -u origin <branch>`) and offer to open the PR
   (`gh pr create`). Merging happens through the PR once the `verify` check is green;
   never merge or push to `main` directly.
10. End with the **feature debrief** (always, in the chat, as the final message):
   - **Business tour** — what the platform can do now that it could not do before,
     in plain product language (no class names), and how this feature moves the
     Ticketly story forward.
   - **Pedagogic tour of the technical implementation** — walk through the design
     decision by decision: what I chose, WHY I chose it, what the idiomatic best
     practice is, and what the main alternative was and why it lost. Assume I am
     relearning modern Java/Spring: name the concept, then explain it in plain words.
   - **How to test it yourself** — exact commands and URLs to run, what output to
     expect, plus which automated test covers what.
   Keep it a readable narrative, not a checklist dump.

Rules: no Lombok, constructor injection, records vs entities per CLAUDE.md,
package layout per REQUIREMENTS.md §5.2 (layer → aggregate sub-package).
Never implement anything from later features. Never work on `main`.