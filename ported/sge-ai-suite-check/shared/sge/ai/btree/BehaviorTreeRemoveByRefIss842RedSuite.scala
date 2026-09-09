package sge
package ai
package btree


class BehaviorTreeRemoveByRefIss842RedSuite extends munit.FunSuite {

  final private class EqualListener extends BehaviorTree.Listener[String] {
    var notified: Int = 0

    override def statusUpdated(task: Task[String], previousStatus: Task.Status): Unit = notified += 1
    override def childAdded(task:    Task[String], index:          Int):         Unit = notified += 1

    override def equals(other: Any): Boolean = other.isInstanceOf[EqualListener]
    override def hashCode():         Int     = 0
  }

  test("ISS-842: removeListener unregisters the SPECIFIC instance by identity, leaving a value-equal sibling registered") {
    val bt     = new BehaviorTree[String](null, ("bb"))
    val first  = new EqualListener()
    val second = new EqualListener()

    assert(first == second, "test fixture invariant: the two listeners must be .equals-equal")
    assert(!(first eq second), "test fixture invariant: the two listeners must be distinct instances")

    bt.addListener(first)
    bt.addListener(second)

    bt.removeListener(second)

    bt.notifyChildAdded(bt, 0)

    assertEquals(
      first.notified,
      1,
      "removeListener(second) must NOT unregister `first`"
    )
    assertEquals(
      second.notified,
      0,
      "removeListener(second) must unregister exactly `second`"
    )
  }
}
