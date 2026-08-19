// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — the shared FIXTURES, cut out of the reference hand port's
//   ../sge/sge-extension/ai/src/test/scala/sge/ai/btree/BehaviorTreeSuite.scala
// which is itself class (c) of the §10.7.12 census. A FIXTURE IS A DECLARATION, NEVER A TEST:
// what travels here is the five leaf tasks the class (a)+(b) files construct, and not one
// assertion from the file they happened to be declared in. Splitting them out is what lets a
// (b) file ship while the (c) file that housed them does not.
//
// mapping rows applied here: M1, M5, M6, M7
// ---------------------------------------------------------------------------------------------
package sge
package ai
package btree

import sge.ai.btree.branch.{ Selector, Sequence }
import sge.ai.btree.branch.Parallel
import sge.ai.btree.decorator.{ Invert, Repeat }
import sge.ai.utils.random.ConstantIntegerDistribution

// ── Test leaf tasks ──────────────────────────────────────────────────────

class SuccessTask[E <: java.lang.Object] extends LeafTask[E] {
  override def execute():                       Task.Status = Task.Status.SUCCEEDED
   def newInstance():                   Task[E]     = new SuccessTask[E]()
  override def copyTo(task: Task[E]): Task[E]     = task
}

class FailTask[E <: java.lang.Object] extends LeafTask[E] {
  override def execute():                       Task.Status = Task.Status.FAILED
   def newInstance():                   Task[E]     = new FailTask[E]()
  override def copyTo(task: Task[E]): Task[E]     = task
}

class RunningTask[E <: java.lang.Object] extends LeafTask[E] {
  override def execute():                       Task.Status = Task.Status.RUNNING
   def newInstance():                   Task[E]     = new RunningTask[E]()
  override def copyTo(task: Task[E]): Task[E]     = task
}

/** A leaf task that succeeds on the Nth call, fails on all others. */
class CountingTask[E <: java.lang.Object](var succeedOn: Int) extends LeafTask[E] {
  private var callCount:  Int         = 0
  override def execute(): Task.Status = {
    callCount += 1
    if (callCount == succeedOn) Task.Status.SUCCEEDED else Task.Status.FAILED
  }
   def newInstance():                   Task[E] = new CountingTask[E](succeedOn)
  override def copyTo(task: Task[E]): Task[E] = {
    task.asInstanceOf[CountingTask[E]].succeedOn = succeedOn
    task
  }
}

/** A leaf task with mutable status and execution counter, for multi-step tests. */
class MutableStatusTask[E <: java.lang.Object] extends LeafTask[E] {
  var nextStatus:         Task.Status = Task.Status.RUNNING
  var executions:         Int         = 0
  override def execute(): Task.Status = {
    executions += 1
    nextStatus
  }
   def newInstance():                   Task[E] = new MutableStatusTask[E]()
  override def copyTo(task: Task[E]): Task[E] = task
}
