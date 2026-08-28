// ---------------------------------------------------------------------------------------------
// DIFFERENTIAL SUITE — a copy of the REFERENCE HAND PORT's own MUnit suite
//   ../sge/sge-extension/ai/src/test/scala/sge/ai/btree/TaskSuite.scala
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
// mapping rows applied here: M1, M4, M5, M6
// ---------------------------------------------------------------------------------------------
package sge
package ai
package btree


class TaskSuite extends munit.FunSuite {

  // ── Status enum ──────────────────────────────────────────────────────

  test("Status enum has exactly 5 values") {
    val values = Task.Status.values
    assertEquals(values.length, 5)
  }

  test("Status enum values are FRESH, RUNNING, FAILED, SUCCEEDED, CANCELLED") {
    assertEquals(Task.Status.FRESH.toString, "FRESH")
    assertEquals(Task.Status.RUNNING.toString, "RUNNING")
    assertEquals(Task.Status.FAILED.toString, "FAILED")
    assertEquals(Task.Status.SUCCEEDED.toString, "SUCCEEDED")
    assertEquals(Task.Status.CANCELLED.toString, "CANCELLED")
  }

  // ── Initial state ────────────────────────────────────────────────────

  test("new task starts FRESH") {
    val task = new SuccessTask[String]()
    assertEquals(task.status, Task.Status.FRESH)
  }

  // ── Lifecycle: init, start, end ──────────────────────────────────────

  test("start() is called before run() in behavior tree step") {
    var startCalled = false
    val task        = new LeafTask[String] {
      override def start():   Unit        = startCalled = true
      override def execute(): Task.Status = {
        assert(startCalled, "start() should be called before execute()")
        Task.Status.SUCCEEDED
      }
      def newInstance():                        Task[String] = throw new UnsupportedOperationException
      override def copyTo(task: Task[String]): Task[String] = task
    }
    val bt = new BehaviorTree[String]((task), ("bb"))
    bt.step()
    assert(startCalled, "start() should have been called")
    assertEquals(bt.status, Task.Status.SUCCEEDED)
  }

  test("end() is called when task succeeds") {
    var endCalled = false
    val task      = new LeafTask[String] {
      override def end():                                Unit         = endCalled = true
      override def execute():                            Task.Status  = Task.Status.SUCCEEDED
      def newInstance():                        Task[String] = throw new UnsupportedOperationException
      override def copyTo(task: Task[String]): Task[String] = task
    }
    val bt = new BehaviorTree[String]((task), ("bb"))
    bt.step()
    assert(endCalled, "end() should have been called on success")
  }

  test("end() is called when task fails") {
    var endCalled = false
    val task      = new LeafTask[String] {
      override def end():                                Unit         = endCalled = true
      override def execute():                            Task.Status  = Task.Status.FAILED
      def newInstance():                        Task[String] = throw new UnsupportedOperationException
      override def copyTo(task: Task[String]): Task[String] = task
    }
    val bt = new BehaviorTree[String]((task), ("bb"))
    bt.step()
    assert(endCalled, "end() should have been called on failure")
  }

  test("end() is not called when task is running") {
    var endCalled = false
    val task      = new LeafTask[String] {
      override def end():                                Unit         = endCalled = true
      override def execute():                            Task.Status  = Task.Status.RUNNING
      def newInstance():                        Task[String] = throw new UnsupportedOperationException
      override def copyTo(task: Task[String]): Task[String] = task
    }
    val bt = new BehaviorTree[String]((task), ("bb"))
    bt.step()
    assert(!endCalled, "end() should not be called when task is running")
  }

  // ── Status transitions ───────────────────────────────────────────────

  test("task transitions from FRESH to SUCCEEDED") {
    val task = new SuccessTask[String]()
    val bt   = new BehaviorTree[String]((task), ("bb"))
    assertEquals(task.status, Task.Status.FRESH)
    bt.step()
    assertEquals(task.status, Task.Status.SUCCEEDED)
  }

  test("task transitions from FRESH to FAILED") {
    val task = new FailTask[String]()
    val bt   = new BehaviorTree[String]((task), ("bb"))
    assertEquals(task.status, Task.Status.FRESH)
    bt.step()
    assertEquals(task.status, Task.Status.FAILED)
  }

  test("task transitions from FRESH to RUNNING") {
    val task = new RunningTask[String]()
    val bt   = new BehaviorTree[String]((task), ("bb"))
    assertEquals(task.status, Task.Status.FRESH)
    bt.step()
    assertEquals(task.status, Task.Status.RUNNING)
  }

  test("task transitions from RUNNING to SUCCEEDED on subsequent step") {
    val task = new MutableStatusTask[String]()
    task.nextStatus = Task.Status.RUNNING
    val bt = new BehaviorTree[String]((task), ("bb"))

    bt.step()
    assertEquals(task.status, Task.Status.RUNNING)

    task.nextStatus = Task.Status.SUCCEEDED
    bt.step()
    assertEquals(task.status, Task.Status.SUCCEEDED)
  }

  test("task transitions from RUNNING to FAILED on subsequent step") {
    val task = new MutableStatusTask[String]()
    task.nextStatus = Task.Status.RUNNING
    val bt = new BehaviorTree[String]((task), ("bb"))

    bt.step()
    assertEquals(task.status, Task.Status.RUNNING)

    task.nextStatus = Task.Status.FAILED
    bt.step()
    assertEquals(task.status, Task.Status.FAILED)
  }

  // ── Reset ────────────────────────────────────────────────────────────

  test("resetTask() returns task to FRESH") {
    val task = new SuccessTask[String]()
    val bt   = new BehaviorTree[String]((task), ("bb"))
    bt.step()
    assertEquals(task.status, Task.Status.SUCCEEDED)

    task.resetTask()
    assertEquals(task.status, Task.Status.FRESH)
  }

  test("resetTask() cancels running task before resetting") {
    var endCalled = false
    val task      = new LeafTask[String] {
      override def end():                                Unit         = endCalled = true
      override def execute():                            Task.Status  = Task.Status.RUNNING
      def newInstance():                        Task[String] = throw new UnsupportedOperationException
      override def copyTo(task: Task[String]): Task[String] = task
    }
    val bt = new BehaviorTree[String]((task), ("bb"))
    bt.step()
    assertEquals(task.status, Task.Status.RUNNING)

    task.resetTask()
    assert(endCalled, "end() should be called during cancel")
    assertEquals(task.status, Task.Status.FRESH)
  }

  test("resetTask() on FRESH task is a no-op (stays FRESH)") {
    val task = new SuccessTask[String]()
    assertEquals(task.status, Task.Status.FRESH)
    task.resetTask()
    assertEquals(task.status, Task.Status.FRESH)
  }

  // ── Cancel ───────────────────────────────────────────────────────────

  test("cancel() sets status to CANCELLED") {
    val task = new RunningTask[String]()
    val bt   = new BehaviorTree[String]((task), ("bb"))
    bt.step()
    assertEquals(task.status, Task.Status.RUNNING)

    task.cancel()
    assertEquals(task.status, Task.Status.CANCELLED)
  }

  test("cancel() calls end()") {
    var endCalled = false
    val task      = new LeafTask[String] {
      override def end():                                Unit         = endCalled = true
      override def execute():                            Task.Status  = Task.Status.RUNNING
      def newInstance():                        Task[String] = throw new UnsupportedOperationException
      override def copyTo(task: Task[String]): Task[String] = task
    }
    val bt = new BehaviorTree[String]((task), ("bb"))
    bt.step()
    task.cancel()
    assert(endCalled, "end() should be called by cancel()")
  }

  // ── LeafTask child management ────────────────────────────────────────

  test("LeafTask getChildCount is 0") {
    val task = new SuccessTask[String]()
    assertEquals(task.childCount, 0)
  }

  test("LeafTask addChild throws") {
    val task = new SuccessTask[String]()
    interceptMessage[IllegalStateException]("A leaf task cannot have any children") {
      task.addChild(new SuccessTask[String]())
    }
  }

  test("LeafTask getChild throws") {
    val task = new SuccessTask[String]()
    intercept[IndexOutOfBoundsException] {
      task.getChild(0)
    }
  }

  // ── setControl ───────────────────────────────────────────────────────

  test("setControl sets the parent task") {
    val parent = new SuccessTask[String]()
    val child  = new SuccessTask[String]()
    // setControl requires parent to have tree set, so use BehaviorTree as parent
    val bt = new BehaviorTree[String]((parent), ("bb"))
    child.setControl(bt)
    // After setControl, getObject should work because tree is set
    assertEquals(child.`object`, "bb")
  }

  // ── getObject ────────────────────────────────────────────────────────

  test("getObject throws when task has never run") {
    val task = new SuccessTask[String]()
    intercept[IllegalStateException] {
      task.`object`
    }
  }

  test("getObject returns blackboard after task has run") {
    val task = new SuccessTask[String]()
    val bt   = new BehaviorTree[String]((task), ("hello"))
    bt.step()
    assertEquals(task.`object`, "hello")
  }

  // ── cloneTask ────────────────────────────────────────────────────────

  test("cloneTask creates independent copy") {
    val task = new CountingTask[String](3)

    val clone = task.cloneTask()
    assert(clone ne task, "clone should be a different instance")
    assertEquals(clone.asInstanceOf[CountingTask[String]].succeedOn, 3)
    assertEquals(clone.status, Task.Status.FRESH)
  }

  test("cloneTask clones guard") {
    val task = new SuccessTask[String]()
    task.guard = (new FailTask[String]())

    val clone = task.cloneTask()
    assert((clone.guard != null), "clone should have a guard")
    assert(clone.guard ne task.guard, "guard should be independently cloned")
  }

  // ── Pool.Poolable reset ──────────────────────────────────────────────

  test("reset() clears control, guard, status, tree") {
    val task = new SuccessTask[String]()
    val bt   = new BehaviorTree[String]((task), ("bb"))
    bt.step()
    task.guard = (new FailTask[String]())

    task.reset()
    assertEquals(task.status, Task.Status.FRESH)
    assert((task.guard == null), "guard should be cleared")
  }
}
