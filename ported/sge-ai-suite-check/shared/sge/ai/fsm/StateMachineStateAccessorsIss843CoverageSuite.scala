package sge
package ai
package fsm


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
