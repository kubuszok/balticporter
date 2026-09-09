package sge
package ai
package fsm

import sge.ai.msg.Telegram

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
    emptyDefault().currentState
  }

  test("ISS-730 c3: DefaultStateMachine.getPreviousState on an empty machine returns null-equivalent, not a crash") {
    emptyDefault().previousState
  }

  test("ISS-730 c3: DefaultStateMachine.getGlobalState on an empty machine returns null-equivalent, not a crash") {
    emptyDefault().globalState
  }

  test("ISS-730 c3: StackStateMachine.getCurrentState on an empty machine returns null-equivalent, not a crash") {
    emptyStack().currentState
  }

  test("ISS-730 c3: StackStateMachine.getPreviousState on an empty stack returns null-equivalent, not a crash") {
    emptyStack().previousState
  }
}
