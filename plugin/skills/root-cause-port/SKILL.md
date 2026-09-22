---
name: root-cause-port
description: Find WHY a generated declaration is spelled the way it is before changing anything — the run's own tables (decisions, derived-policy, port-map, findings, errors by member), the engine's corpus port beside the consumer's own, master's own spelling, and which of the three kinds the fix is. Use when a consumer compile error points into src_managed, when a lane row moves without an obvious cause, when a phase "silently did nothing", and before writing any workaround.
---

# Root cause, not symptom

A generated line is the output of a decision chain; the tables name every link. Read them in this
order, and stop at the first that explains the spelling.

## 1. Attribute the error, never read the emitted file by hand

`port-report/<Run>/run-latest/errors.tsv` — lane (`EngineGap` / `Unmapped` = injected or hand
file), member, java origin; `correlate.txt` has the message. One `Unmapped` error can STOP the
compile before the port's own sources (one E161 in an injected `Collections` file hid every error of
a whole port for a day): a lane at "1 error" may be measuring nothing.

## 2. The decision behind the member

```
grep 'Owner#member' port-report/<Run>/run-latest/decisions.tsv      # kind, reason, phase, key, from/to
grep 'Owner#member' port-report/<Run>/run-latest/derived-policy.tsv # what the REFERENCE spelled there
grep 'Owner#member' port-report/<Run>/run-latest/port-map.tsv       # the upstream→emitted signature
grep 'Owner#member' port-report/<Run>/run-latest/findings.tsv       # refusals, each naming its guard
```
A `policy` finding says which key never matched or which guard refused — that sentence IS the
cause (`"the setter's override component reaches Sprite which declares the setter without a
getter"`, `"the component reaches java.util.Comparator#compareTo"`). A key that "matched and the
rule did nothing" is a binding question (`Ownership.Owned` vs an external type).

## 3. Compare the three spellings

- The CORPUS port: `ported/sge/src_managed/…` in the engine checkout and its
  `port-report/LibgdxCoreMigrate/` — the same upstream under the corpus policy. The CONSUMER's
  port: `target/balticporter-<module>/src_managed/…` under the policy its own repository holds
  (`sge-port/`, `lls-port/`). A difference between them at one member is a policy difference or
  a derivation gap (a derived seed is exact, so nothing grows from it beyond what was explicitly
  seeded).
- MASTER: `git -C ../sge show origin/master:sge/src/main/scala/<path> | grep -n <member>` — the
  spelling the port owes (API parity) and the migration note that explains it. A consumer file
  patched away from master is a symptom; master's text is the test.

## 4. Which kind is the fix

(a) a Java/Scala fact → engine, unparameterised, with a spec; (b) a mechanism gap → the phase,
parameterised, empty = no-op, `SurfacePolicy`, counted refusals; (c) one library's spelling →
the port's policy (the consumer's own port policy, an inject file). A shape that recurs in a
consumer (`Nullable[java.lang.Integer]` ×16 in one class) is a mechanism, not sixteen patches.
Walk a policy that changes what an EXTERNAL type spells on a testkit spec before regenerating a
consumer — a member rename on an external redirected type hits are the owned overrides whose closure
is anchored on the (source, member) pair, so a second unknown parent's surface is port policy, never
a new closed platform row (`iterate-lane` step 5).

## 5. Measure the fix where it can be seen

An engine change moves the base's map: regenerate the base before its dependents (else "contract
questions could not be answered from a base's published port map"). The engine's corpus lanes
may not reach the class in question: then the consumer's `testCompile-*` is the measurement, and
the lane row you expect to move is stated in the commit.
A moved lane row is attributed by member (`members-changed.tsv`) or it is a regression.

## 6. Record it in the same commit

The rule line goes to the place that loads it (`.claude/rules/*.md` for the phase's files, a
skill for a procedure); the numbers go in
the commit subject (`before->after`). A residue nobody fixes today is named in that port's own
status notes.
