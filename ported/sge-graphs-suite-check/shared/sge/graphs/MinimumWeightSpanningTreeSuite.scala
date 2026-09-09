package sge
package graphs

import munit.FunSuite

class MinimumWeightSpanningTreeSuite extends FunSuite {

  test("MST of simple triangle") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B", 1.0f)
    graph.addEdge("B", "C", 2.0f)
    graph.addEdge("A", "C", 3.0f)

    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.getEdgeCount(), 2)
    var totalWeight = 0f
    mst.getEdges().foreach(e => totalWeight += e.getWeight())
    assertEquals(totalWeight, 3.0f)
  }

  test("MST of path graph returns same graph") {
    val graph = new UndirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3, 4))
    graph.addEdge(1: Integer, 2: Integer, 1.0f)
    graph.addEdge(2: Integer, 3: Integer, 1.0f)
    graph.addEdge(3: Integer, 4: Integer, 1.0f)

    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.getEdgeCount(), 3)
    var totalWeight = 0f
    mst.getEdges().foreach(e => totalWeight += e.getWeight())
    assertEquals(totalWeight, 3.0f)
  }

  test("MST picks lighter edges") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C", "D"))
    graph.addEdge("A", "B", 1.0f)
    graph.addEdge("A", "C", 5.0f)
    graph.addEdge("B", "C", 2.0f)
    graph.addEdge("B", "D", 3.0f)
    graph.addEdge("C", "D", 4.0f)
    graph.addEdge("A", "D", 10.0f)

    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.getEdgeCount(), 3)
    var totalWeight = 0f
    mst.getEdges().foreach(e => totalWeight += e.getWeight())
    assertEquals(totalWeight, 6.0f)
  }

  test("MST of single vertex graph has no edges") {
    val graph = new UndirectedGraph[Integer]()
    graph.addVertex(1: Integer)
    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.getEdgeCount(), 0)
    assertEquals(mst.size(), 1)
  }

  test("MST of two vertices with one edge") {
    val graph = new UndirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2))
    graph.addEdge(1: Integer, 2: Integer, 5.0f)
    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.getEdgeCount(), 1)
    var totalWeight = 0f
    mst.getEdges().foreach(e => totalWeight += e.getWeight())
    assertEquals(totalWeight, 5.0f)
  }

  test("MST preserves all vertices") {
    val graph = new UndirectedGraph[Integer]()
    for (i <- 1 to 6) graph.addVertex(Integer.valueOf(i))
    graph.addEdge(1: Integer, 2: Integer, 1.0f)
    graph.addEdge(2: Integer, 3: Integer, 2.0f)
    graph.addEdge(3: Integer, 4: Integer, 3.0f)
    graph.addEdge(4: Integer, 5: Integer, 4.0f)
    graph.addEdge(5: Integer, 6: Integer, 5.0f)
    graph.addEdge(1: Integer, 6: Integer, 100.0f)

    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.size(), 6)
    assertEquals(mst.getEdgeCount(), 5)
  }

  test("MST with equal weight edges") {
    val graph = new UndirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3))
    graph.addEdge(1: Integer, 2: Integer, 1.0f)
    graph.addEdge(2: Integer, 3: Integer, 1.0f)
    graph.addEdge(1: Integer, 3: Integer, 1.0f)

    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.getEdgeCount(), 2)
    var totalWeight = 0f
    mst.getEdges().foreach(e => totalWeight += e.getWeight())
    assertEquals(totalWeight, 2.0f)
  }

  test("MST on grid graph") {
    val n     = 3
    val graph = new UndirectedGraph[Vector2]()
    GraphTestUtils.makeGridGraph(graph, n)

    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.getEdgeCount(), n * n - 1)
  }

  test("MST on complete graph") {
    val graph = new UndirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3, 4))
    GraphBuilder.buildCompleteGraph(graph)

    graph.addEdge(1: Integer, 2: Integer, 1.0f)
    graph.addEdge(1: Integer, 3: Integer, 4.0f)
    graph.addEdge(1: Integer, 4: Integer, 3.0f)
    graph.addEdge(2: Integer, 3: Integer, 2.0f)
    graph.addEdge(2: Integer, 4: Integer, 5.0f)
    graph.addEdge(3: Integer, 4: Integer, 6.0f)

    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.getEdgeCount(), 3)
    var totalWeight = 0f
    mst.getEdges().foreach(e => totalWeight += e.getWeight())
    assertEquals(totalWeight, 6.0f)
  }
}
