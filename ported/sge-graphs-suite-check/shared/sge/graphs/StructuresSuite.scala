package sge
package graphs

import munit.FunSuite

final case class BadHashInteger(i: Int) {
  override def hashCode(): Int = 1
  override def toString: String = i.toString
}

class StructuresSuite extends FunSuite {

  test("nodeMap should work") {
    val graph   = new UndirectedGraph[Integer]()
    val nodeMap = graph.nodeMap
    val n       = 16

    val threshold = 22

    for (i <- 0 until threshold)
      assert(nodeMap.put(i) != null, s"Put did not return a node for $i")

    val minTableLength: Integer = 32
    assert(nodeMap.put(minTableLength) != null, "Put did not return a node")
    assertEquals(nodeMap.contains(minTableLength), true, "Object not contained in map")
    assertEquals(nodeMap.size, threshold + 1, "Map is not correct size")

    val removed = nodeMap.remove(Integer.valueOf(2))
    assert(removed != null, "Removal did not return node")

    val badGraph = new UndirectedGraph[BadHashInteger]()
    for (i <- 0 until n)
      assert(badGraph.nodeMap.put(BadHashInteger(i)) != null, "Put did not return a node")

    assertEquals(badGraph.size(), n)

    badGraph.removeVertex(BadHashInteger(2))

    assertEquals(badGraph.size(), n - 1)

    badGraph.nodeMap.clear()

    for (i <- 0 until n)
      assert(badGraph.nodeMap.put(BadHashInteger(i)) != null)
  }
}
