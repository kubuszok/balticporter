// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/ai/src/test/scala/sge/ai/fsm/StateMachineSuite.scala
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
// mapping rows applied here: M2, M3
// ---------------------------------------------------------------------------------------------
package sge
package ai
package fsm

import sge.ai.msg.Telegram

class TrackingState extends State[String] {
  var events:                                                 List[String] = List.empty
  override def enter(entity:     String):                     Unit         = events = events :+ s"enter:$entity"
  override def update(entity:    String):                     Unit         = events = events :+ s"update:$entity"
  override def exit(entity:      String):                     Unit         = events = events :+ s"exit:$entity"
  override def onMessage(entity: String, telegram: Telegram): Boolean      = false
}

class MessageHandlingState(val handle: Boolean) extends State[String] {
  override def enter(entity:     String):                     Unit    = {}
  override def update(entity:    String):                     Unit    = {}
  override def exit(entity:      String):                     Unit    = {}
  override def onMessage(entity: String, telegram: Telegram): Boolean = handle
}

class StateMachineSuite extends munit.FunSuite {

  test("initial state enter is called via changeState") {
    val state = new TrackingState()
    val fsm   = new DefaultStateMachine[String, TrackingState](
      "hero",
      null,
      null
    )
    fsm.changeState(state)
    assertEquals(state.events, List("enter:hero"))
  }

  test("update delegates to current state") {
    val state = new TrackingState()
    val fsm   = new DefaultStateMachine[String, TrackingState](
      "hero",
      null,
      null
    )
    fsm.changeState(state)
    state.events = Nil // reset
    fsm.update()
    assertEquals(state.events, List("update:hero"))
  }

  test("changeState calls exit on old and enter on new") {
    val stateA = new TrackingState()
    val stateB = new TrackingState()
    val fsm    = new DefaultStateMachine[String, TrackingState](
      "hero",
      null,
      null
    )
    fsm.changeState(stateA)
    stateA.events = Nil
    fsm.changeState(stateB)
    assertEquals(stateA.events, List("exit:hero"))
    assertEquals(stateB.events, List("enter:hero"))
  }

  test("revertToPreviousState works") {
    val stateA = new TrackingState()
    val stateB = new TrackingState()
    val fsm    = new DefaultStateMachine[String, TrackingState](
      "hero",
      null,
      null
    )
    fsm.changeState(stateA)
    fsm.changeState(stateB)
    stateA.events = Nil
    stateB.events = Nil
    val reverted = fsm.revertToPreviousState()
    assert(reverted, "revertToPreviousState should return true")
    // exit B, enter A
    assertEquals(stateB.events, List("exit:hero"))
    assertEquals(stateA.events, List("enter:hero"))
    assert(fsm.isInState(stateA), "should be in stateA")
  }

  test("revertToPreviousState returns false when no previous") {
    val fsm = new DefaultStateMachine[String, TrackingState](
      "hero",
      null,
      null
    )
    assert(!fsm.revertToPreviousState(), "should return false with no previous state")
  }

  test("handleMessage routes to current state then global state") {
    val currentState = new MessageHandlingState(false)
    val globalState  = new MessageHandlingState(true)
    val fsm          = new DefaultStateMachine[String, MessageHandlingState](
      "hero",
      initialState$p = (currentState),
      globalState$p = (globalState)
    )
    val telegram = new Telegram()
    telegram.message = 99
    // Current state returns false, so global state handles it
    val handled = fsm.handleMessage(telegram)
    assert(handled, "global state should handle the message")
  }

  test("handleMessage: current state handles if it returns true") {
    val currentState = new MessageHandlingState(true)
    val globalState  = new MessageHandlingState(true)
    val fsm          = new DefaultStateMachine[String, MessageHandlingState](
      "hero",
      initialState$p = (currentState),
      globalState$p = (globalState)
    )
    val telegram = new Telegram()
    telegram.message = 1
    assert(fsm.handleMessage(telegram), "current state should handle the message")
  }

  // ── StackStateMachine ──────────────────────────────────────────────────

  test("StackStateMachine: push and pop states") {
    val stateA = new TrackingState()
    val stateB = new TrackingState()
    val stateC = new TrackingState()
    val fsm    = new StackStateMachine[String, TrackingState](
      "hero",
      null,
      null
    )

    fsm.changeState(stateA)
    fsm.changeState(stateB)
    fsm.changeState(stateC)

    assert(fsm.isInState(stateC), "should be in stateC")

    // Pop back to B
    stateC.events = Nil
    stateB.events = Nil
    assert(fsm.revertToPreviousState(), "should revert to B")
    assertEquals(stateC.events, List("exit:hero"))
    assertEquals(stateB.events, List("enter:hero"))
    assert(fsm.isInState(stateB), "should be in stateB")

    // Pop back to A
    stateB.events = Nil
    stateA.events = Nil
    assert(fsm.revertToPreviousState(), "should revert to A")
    assertEquals(stateB.events, List("exit:hero"))
    assertEquals(stateA.events, List("enter:hero"))
    assert(fsm.isInState(stateA), "should be in stateA")

    // No more previous states
    assert(!fsm.revertToPreviousState(), "no more previous states")
  }

  test("StackStateMachine: update delegates to current state") {
    val state = new TrackingState()
    val fsm   = new StackStateMachine[String, TrackingState](
      "hero",
      null,
      null
    )
    fsm.changeState(state)
    state.events = Nil
    fsm.update()
    assertEquals(state.events, List("update:hero"))
  }
}
