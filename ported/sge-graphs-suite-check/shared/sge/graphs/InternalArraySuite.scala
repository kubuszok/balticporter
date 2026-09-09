package sge
package graphs

import munit.FunSuite

class InternalArraySuite extends FunSuite {

  test("addAll should add all items from source to target and resize target") {
    val target = new sge.graphs.Array[Integer](0, false)
    val source = new sge.graphs.Array[Integer]()
    source.add(3)
    target.add(1)
    target.add(2)
    target.addAll(source)
    assertEquals(target.size(), 3, "Target Array has wrong size.")
    assertEquals(target.get(0), Integer.valueOf(1), "Item 0 of Target Array was overwritten.")
    assertEquals(target.get(1), Integer.valueOf(2), "Item 1 of Target Array was overwritten.")
    assertEquals(target.get(2), Integer.valueOf(3), "Item 0 of Source Array was not copied.")
  }

  test("addAll should add all items from source to target and update target size") {
    val target = new sge.graphs.Array[Integer]()
    val source = new sge.graphs.Array[Integer]()
    target.add(0)
    source.add(1)
    target.addAll(source)
    assertEquals(target.size(), 2, "Target Array has wrong size.")
    assertEquals(target.get(0), Integer.valueOf(0), "Item of Target Array was overwritten.")
    assertEquals(target.get(1), source.get(0), "Item of Source Array was not copied.")
  }
}
