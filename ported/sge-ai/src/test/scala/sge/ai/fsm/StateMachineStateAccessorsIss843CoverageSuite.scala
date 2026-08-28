// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE -- a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/ai/src/test/scala/sge/ai/fsm/StateMachineStateAccessorsIss843CoverageSuite.scala
// run against THIS port's mechanically emitted `sge.ai.*`. It is HAND-WRITTEN Scala and must
// never be counted as a ported test (`CLAUDE.md` section 3, and the jbump differential probe's rule);
// `PROGRESS.md` section 10.7.12 is the census that says why this file is here and its siblings are not.
//
// Class (b) of that census. NO ASSERTION IS EDITED -- an assertion changed is evidence
// destroyed, and a file whose assertions could not survive the mapping is class (c) and was
// left out rather than repaired. The only edits are the mapping rows below, each a NAME or
// SHIM substitution between the hand port's surface and this port's emitted one, and each
// applied to CODE only -- a comment is the hand port's own prose.
//
// mapping rows applied here: M1, M4, M7, M10
// ---------------------------------------------------------------------------------------------
package sge
package ai
package fsm


/** Coverage suite for ISS-843 (wave 2026-07-18-G, territory G2).
  *
  * The wave-F mutation `getPreviousState := currentState` (a field-swap in DefaultStateMachine's accessors) survived the entire pre-existing ai suite because no test asserted the STATEFUL VALUES
  * returned by `getCurrentState` / `getPreviousState` / `getGlobalState` — the existing StateMachineSuite only observes State enter/exit/update event ordering, never the identity of the states the
  * getters return.
  *
  * This suite pins those accessors against DefaultStateMachine.java:90-103 (each getter returns its own field verbatim) and DefaultStateMachine.java:105-116 (`changeState` records `previousState =
  * currentState` before transitioning) plus the `revertToPreviousState` round-trip (DefaultStateMachine.java:118-124).
  *
  * This is GREEN coverage: current behavior is correct, so these assertions pass today. Identity (`eq`) assertions per the Nullable[S] accessor signatures landed in wave-F commit 40c4aca7. Verified
  * to go RED under a local re-application of the `getPreviousState := currentState` field-swap mutation.
  */
class StateMachineStateAccessorsIss843CoverageSuite extends munit.FunSuite {

  test("ISS-843: getCurrentState/getPreviousState/getGlobalState return the EXACT expected instance at each step") {
    val stateA = new TrackingState()
    val stateB = new TrackingState()
    val global = new TrackingState()

    val fsm = new DefaultStateMachine[String, TrackingState](
      "hero",
      null,
      null
    )

    fsm.globalState = (global)

    // Before any transition: no current, no previous, global set.
    assert((fsm.currentState == null), "getCurrentState must be empty before any changeState")
    assert((fsm.previousState == null), "getPreviousState must be empty before any changeState")
    assert(fsm.globalState eq global, "getGlobalState must return exactly the state passed to setGlobalState")

    // First transition: current == A, previous stays empty (it was empty before).
    fsm.changeState(stateA)
    assert(fsm.currentState eq stateA, "getCurrentState must return exactly stateA after changeState(stateA)")
    assert((fsm.previousState == null), "getPreviousState must remain empty: the state before stateA was empty")
    assert(fsm.globalState eq global, "getGlobalState must be unaffected by changeState")

    // Second transition: current == B, previous == A.
    fsm.changeState(stateB)
    assert(fsm.currentState eq stateB, "getCurrentState must return exactly stateB after changeState(stateB)")
    assert(fsm.previousState eq stateA, "getPreviousState must return exactly stateA (the state current held before)")
    assert(fsm.globalState eq global, "getGlobalState must be unaffected by changeState")

    // Revert round-trip: current <- previous (A), previous <- current-before-revert (B).
    val reverted = fsm.revertToPreviousState()
    assert(reverted, "revertToPreviousState must return true when a previous state exists")
    assert(fsm.currentState eq stateA, "after revert, getCurrentState must return exactly stateA")
    assert(fsm.previousState eq stateB, "after revert, getPreviousState must return exactly stateB")
    assert(fsm.globalState eq global, "getGlobalState must be unaffected by revert")
  }
}
