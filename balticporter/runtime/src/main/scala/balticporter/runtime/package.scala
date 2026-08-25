/** `balticporter-runtime` — the support types Baltic-Porter-emitted Scala LINKS AGAINST.
  *
  * ==What may be added here, and what may not==
  *
  * There is exactly one criterion, and it is not "the port needed a helper":
  *
  *   - '''Semantics the target language LACKS''' → a runtime type, published here, DEPENDED ON by
  *     the port. `java.util.Iterator` has `remove()`; `scala.collection.Iterator` has no such
  *     operation and no way to express one. No emitter can close that gap, because the missing
  *     thing is a member of an interface the ported code implements and calls polymorphically.
  *     That is a genuine runtime. It ships.
  *   - '''Shapes the engine can EMIT correctly''' → the engine emits them, and nothing ships. A
  *     helper that exists because the emitted call had the wrong argument types, or sat in a
  *     companion where the framework's instance members were invisible, is SHAPE ADAPTATION. It
  *     hides an engine bug behind an artifact, and every port then carries the bug's workaround
  *     as a dependency. Fix the emitter.
  *
  * The distinction has already been got wrong once, in both directions, at real cost: an
  * assertion-helper and a suite base class were each argued into this artifact and then argued
  * back out, because the errors that "justified" them were widenable numeric comparisons and
  * companion-visibility — both of which the engine can and should emit correctly.
  *
  * So before adding a type here, answer: '''could a correct emitter have avoided it?''' If yes, it
  * does not belong in this module at any level of generality.
  *
  * ==Constraints on what is here==
  *
  *   - '''Nothing JVM-only.''' No reflection, no threads, no I/O, no `java.*` beyond what Scala.js
  *     and Native implement. Ports target the same platforms sge does, and this artifact is the
  *     one piece of them the engine ships rather than generates. This module is cross-built for
  *     all three (`runtimeJVM`, `runtimeJS`, `runtimeNative`, one shared `src/main/scala`), and
  *     those two rows are the ONLY instrument that checks this rule — but they check it at LINK
  *     time and not at compile time, because both backends compile against the real JDK and
  *     resolve `java.*` against their own javalib when they link. So a member naming an
  *     unimplemented JDK API builds on every row and refuses only where something REACHES it: the
  *     residue is per-MEMBER, and the three that exist today are listed in `PROGRESS.md` §13.1
  *     (`orderedSpliterator`/`distinctSpliterator` on JS; `JavaEnumSet.allOf`/`range`/
  *     `complementOf` on both). Do not add a fourth without recording it there.
  *   - '''Java's shape, not Scala's collection traits''' (CLAUDE.md §4.5). Java interfaces are
  *     small and orthogonal and a class routinely implements several; Scala's collection traits
  *     are large and interlocking and that shape is illegal under them. Java's method arity is
  *     part of the shape: `iterator()`, `hasNext()`, `next()`, never the parameterless forms.
  *     Scala interop is restored with EXTENSION methods, which add a view and cannot conflict,
  *     never with inheritance, which adds members and does.
  *   - '''Version-locked to the engine.''' One artifact version per engine version, so two ported
  *     modules of the same library cannot end up with divergent bodies at one FQN — which is the
  *     entire reason this is a published artifact and not a per-port source drop.
  */
package balticporter.runtime
