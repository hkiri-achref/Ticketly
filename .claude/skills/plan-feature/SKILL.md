---
name: plan-feature
description: Plan one Ticketly feature by ID. Discuss the design with me, then write the approved plan to docs/plans/.
argument-hint: [feature-id]
disable-model-invocation: true
---

We are PLANNING feature $ARGUMENTS of Ticketly. No application code is written by this skill.

## Inputs
- `REQUIREMENTS.md`: read ONLY the section headed `#### $ARGUMENTS`, plus §5, §6 and the
  §7/§8/§9 subsections that feature references.
- `docs/plans/`: read the plans of earlier features in the same service so the design stays consistent.
- `docs/plans/_template.md`: the plan format.

## Process
1. Restate the business goal and acceptance criteria in your own words. Ask me at most 3
   clarifying questions if something is ambiguous; otherwise state your assumptions.
2. Propose the design as a conversation, not a document yet:
    - components: class/file → responsibility → which modern Java/Spring concept it practices.
      Name each component with its full package path per REQUIREMENTS.md §5.2, i.e.
      `<layer>/<aggregate>/<Class>` (e.g. `domain/event/TicketTier`, `api/common/ApiExceptionHandler`);
      children belong to their aggregate root's sub-package, shared pieces to `<layer>/common/`.
      A plan that lists a class directly under a layer root is not approvable.
    - data model and migration
    - API or Kafka contracts touched
    - transaction boundaries and concurrency risks
    - test plan mapped to each acceptance criterion
    - alternatives you rejected and why (one line each)
      Explain the WHY behind each choice in plain words; I am relearning Java 21+ and Spring Boot 4.
3. Wait for my feedback. Iterate until I write the single word "approved".
4. Only after "approved": create `docs/plans/$ARGUMENTS-<short-slug>.md` from the template,
   with `status: approved` and today's date. Keep it under 120 lines.
5. Reply with the file path and a one-line summary. Do not start implementing.

Never design beyond the scope of $ARGUMENTS.