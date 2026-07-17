---
name: goal
description: Dutifully implement the current phase of Baltic Porter (GOAL.md + PLAN.md §13), gate-driven, no shortcuts. Use when asked to continue phase work or via /loop.
---

# /goal — implement the current phase, dutifully

You are working on Baltic Porter (this repo). `PLAN.md` defines the milestones
M0–M6 with acceptance gates; `GOAL.md` holds the live state of the current
phase; `RESEARCH.md` holds the evidence and design rationale.

Each invocation:

1. Read `GOAL.md`. Identify the current phase and the first unchecked item.
2. Work on that item for real. Iterate until it is done or you are genuinely
   blocked on user input. Prefer finishing one item over starting three.
3. Run whatever verifies the item (sbt compile, the M-gate pipeline, tests).
   An item is checked ONLY with a passing command you actually ran this
   iteration. Paste the command in GOAL.md next to the evidence line if it is
   part of the gate.
4. Update `GOAL.md` (check items, note discoveries/decisions/blockers tersely).
5. Commit completed, compiling work with a descriptive message. Do not commit
   broken intermediate states; do not leave completed work uncommitted.
6. A phase's Status flips to DONE only when its gate line is green with
   re-runnable evidence recorded in GOAL.md. Then promote the next phase from
   PLAN.md §13 into GOAL.md with a fresh checklist.

Hard rules (this project exists because agents violated them — see
`../ssg/docs/plans/remediation-2026-06.md` C1–C16):

- Porting/engineering is binary: an item is 100% done or it is not done.
  Banned: stubs, `???`, "mostly works", "diminishing returns", narrowing a
  gate to make it pass, marking checklist items done without evidence.
- If a gate check fails, the fix goes in the engine/code, never in the gate.
  Changing a gate or the 20-file set requires recording the reason in GOAL.md.
- Determinism contract: no wall-clock, no randomness, no hash-order iteration
  in engine code paths that affect output.
- When you discover the plan itself is wrong, update PLAN.md in its own commit
  with a short rationale — don't silently drift from it.

If the phase is blocked on the user, say so in GOAL.md under `Blocked:` and
stop the loop (`ScheduleWakeup stop`) with a clear summary rather than idling.
