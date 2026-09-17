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

## Process
1. Show me the plan's "Implementation steps" checklist and confirm the first step.
   Then work step by step, ticking each box in the plan file as you complete it.
2. Follow the plan. If you must deviate (an API does not exist in Boot 4.1, the plan missed
   something), STOP, explain, get my OK, then record it under "## Deviations" in the plan.
3. For every modern Java or Spring Boot 4 feature used, add a 1–3 line comment explaining WHY.
4. Run the tests named in the plan's test plan. Fix failures before moving on.
5. When all steps are done, verify each acceptance criterion from REQUIREMENTS.md and
   report ✅ or ❌ per item, with the test that proves it.
6. Set the plan's `status: implemented`, then draft `docs/learning-notes/$ARGUMENTS.md`
   with: what was built, concepts practiced, two interview questions, and one production
   concern deliberately simplified.
7. End with the **feature debrief** (always, in the chat, as the final message):
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

Rules: no Lombok, constructor injection, records vs entities per CLAUDE.md.
Never implement anything from later features.