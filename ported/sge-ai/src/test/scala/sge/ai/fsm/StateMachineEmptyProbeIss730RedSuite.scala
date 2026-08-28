// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/ai/src/test/scala/sge/ai/fsm/StateMachineEmptyProbeIss730RedSuite.scala
// run against THIS port's mechanically emitted `sge.ai.*`. It is HAND-WRITTEN Scala and must
// never be counted as a ported test (`CLAUDE.md` §3, and the jbump differential probe's rule);
// `PROGRESS.md` §10.7.12 is the census that says why this file is here and its siblings are not.
//
// Class (b) of that census. NO ASSERTION IS EDITED — an assertion changed is evidence
// destroyed, and a file whose assertions could not survive the mapping is class (c) and was
// left out rather than repaired. The only edits are the mapping rows below, each a NAME or
// SHIM substitution between the hand port's surface and this port's emitted one, and each
// applied to CODE only — a comment is the hand port's own prose.
//
// mapping rows applied here: M1, M4, M10
// ---------------------------------------------------------------------------------------------
package sge
package ai
package fsm

import sge.ai.msg.Telegram

/** Red suite for ISS-730 clause c3 (wave 2026-07-17-F, territory Z).
  *
  * Probing a state machine that has no current / previous / global state must return the null-equivalent (an empty [[Nullable]]), NOT crash.
  *
  * Original contract (states are plain nullable references):
  *   - com/badlogic/gdx/ai/fsm/DefaultStateMachine.java:90-103 — `getCurrentState()`, `getGlobalState()`, `getPreviousState()` each simply `return currentState/globalState/previousState;`, which is
  *     `null` on a freshly built machine (constructor `this(null, null, null)`, lines 42-44).
  *   - com/badlogic/gdx/ai/fsm/StackStateMachine.java `getCurrentState()` returns the (null) current state, and `getPreviousState()` returns `null` when the stack is empty in the original's
  *     null-based contract.
  *
  * The port force-unwraps: `DefaultStateMachine.getCurrentState = currentState.get` (DefaultStateMachine.scala:72,74,76) and `StackStateMachine.getPreviousState` throws `NullPointerException`
  * explicitly (StackStateMachine.scala:63-68). Both therefore CRASH on an empty machine where the original returns `null`.
  *
  * Faithful contract under the project null-mapping: these accessors should be typed `Nullable[S]` and return `Nullable.empty` when unset (a signature change the implementer must make on the
  * `StateMachine` trait and its impls). This suite is written as a RUNTIME red — it only asserts "must not throw" — because that no-crash assertion compiles against the current `: S` signature yet is
  * satisfiable ONLY by returning the empty null-equivalent (there is no non-empty `S` to hand back on an empty machine, and `orNull` is forbidden by project rules). See the reproducer report for the
  * exact `Nullable[S]` signature spec.
  *
  * These assertions encode the ORIGINAL semantics and MUST NOT be weakened.
  */
class StateMachineEmptyProbeIss730RedSuite extends munit.FunSuite {

  final private class NoopState extends State[String] {
    override def enter(entity:     String):                     Unit    = ()
    override def update(entity:    String):                     Unit    = ()
    override def exit(entity:      String):                     Unit    = ()
    override def onMessage(entity: String, telegram: Telegram): Boolean = false
  }

  private def emptyDefault(): DefaultStateMachine[String, NoopState] =
    new DefaultStateMachine[String, NoopState]("hero", null, null)

  private def emptyStack(): StackStateMachine[String, NoopState] =
    new StackStateMachine[String, NoopState]("hero", null, null)

  test("ISS-730 c3: DefaultStateMachine.getCurrentState on an empty machine returns null-equivalent, not a crash") {
    // DefaultStateMachine.java:91-93 returns null here; the port's `.get` throws.
    emptyDefault().currentState
  }

  test("ISS-730 c3: DefaultStateMachine.getPreviousState on an empty machine returns null-equivalent, not a crash") {
    // DefaultStateMachine.java:101-103 returns null here; the port's `.get` throws.
    emptyDefault().previousState
  }

  test("ISS-730 c3: DefaultStateMachine.getGlobalState on an empty machine returns null-equivalent, not a crash") {
    // DefaultStateMachine.java:96-98 returns null here; the port's `.get` throws.
    emptyDefault().globalState
  }

  test("ISS-730 c3: StackStateMachine.getCurrentState on an empty machine returns null-equivalent, not a crash") {
    // The original returns the (null) current state; the port's `.get` throws.
    emptyStack().currentState
  }

  test("ISS-730 c3: StackStateMachine.getPreviousState on an empty stack returns null-equivalent, not a crash") {
    // StackStateMachine.scala:63-68 throws NullPointerException explicitly on an
    // empty stack; the original's null-based contract returns null instead.
    emptyStack().previousState
  }
}
