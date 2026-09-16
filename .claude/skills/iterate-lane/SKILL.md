---
name: iterate-lane
description: Iterate on a red step of a port — one lane per cycle, the run's own tables before a second compile, the full chain only to land. Use when a phase or policy change leaves a lane red and the cause is not yet known.
---

# Iterating on a red lane

A full chain (base lanes, suite check, test lane, twelve demos) costs 30–40 minutes and answers
nothing new while the port is red. A cycle is ONE lane plus three tables. sbt 2 caches a compile by
input hash, so a regeneration that emits identical text costs the migrator alone (~80 s warm).

1. Run the one lane the change is aimed at (`just gdx-l0-measure`, `just lls-measure`, …), with
   `SGE_REF=../sge-master` exported: the Justfile's default reference is `../sge`, which is on the
   generated branch with its hand port deleted, and the derive step then seeds from a gutted tree
   (L0 `5 -> 499` errors, all opaque-slot mismatches, 2026-09-16 — the engine had not changed).
2. Read, in this order, before touching anything:
   `. scripts/_lib.sh; iteration_summary port-report/<Report> <phase> <phase>`
   - **errors by member** — `run-latest/errors.tsv`, MEMBER column: a family per member, never the
     raw compiler text (`srcmap.tsv` joins it to the java origin).
   - **decisions per phase** — a phase you expected to act with 0 rows is a bug in its inputs (a
     lookup key, an ordering, a package chain); find it there, not by compiling again.
   - **members changed** — `run-latest/members-changed.tsv` is the blast radius before any compile.
3. An ORDER question: `just debug-set balticporter.tracePhases true`, one migrator run, `just debug-clear`.
4. A seam in INJECTED code is a reconciliation to undo (diff the file against the hand port); a seam
   in EMITTED code is the engine's. Keep the two in separate commits.
5. A policy that changes what an EXTERNAL type spells at the consumer — a `ClassTableTransform`
   redirect, a `MemberRenameTransform` over an external anchor, a retarget of a JDK interface — is
   tried on a testkit spec or a `.balticporter/` scratch program that exercises every layer (redirect
   scope, member rename, anchor binding, the anchor's own surface, hit scope) BEFORE any dependent
   port is regenerated. Discovering the layers one per regeneration cost five publish/regenerate
   round-trips (`Comparable`→`Ordered`, 2026-09-16), about an hour, for a redirect one spec would
   have walked in minutes.
6. Land only from a green lane: `measure-all`, the suite check, the demos, `baseline-accept` from
   that run, docs in the same commit.
