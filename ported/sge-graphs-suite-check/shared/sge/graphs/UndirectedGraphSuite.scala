package sge
package graphs

import munit.FunSuite

class UndirectedGraphSuite extends FunSuite {

  test("add and remove vertices") {
    val graph = new UndirectedGraph[String]()
    assert(graph.addVertex("A"))
    assert(graph.addVertex("B"))
    assertEquals(graph.size(), 2)
    assert(graph.removeVertex("A"))
    assertEquals(graph.size(), 1)
  }

  test("undirected edges are bidirectional") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B"))
    graph.addEdge("A", "B", 5.0f)
    assertEquals(graph.getEdgeCount(), 1)
    assert(graph.edgeExists("A", "B"))
    assert(graph.edgeExists("B", "A"))
  }

  test("no duplicate edges") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B"))
    graph.addEdge("A", "B")
    graph.addEdge("B", "A")
    assertEquals(graph.getEdgeCount(), 1)
  }

  test("remove undirected edge") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B")
    graph.addEdge("B", "C")
    assertEquals(graph.getEdgeCount(), 2)
    assert(graph.removeEdge("B", "A"))
    assertEquals(graph.getEdgeCount(), 1)
    assert(!graph.edgeExists("A", "B"))
    assert(!graph.edgeExists("B", "A"))
  }

  test("minimum spanning tree") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C", "D"))
    graph.addEdge("A", "B", 1.0f)
    graph.addEdge("B", "C", 2.0f)
    graph.addEdge("C", "D", 3.0f)
    graph.addEdge("A", "D", 10.0f)
    graph.addEdge("A", "C", 5.0f)

    val mst = graph.algorithms().findMinimumWeightSpanningTree()
    assertEquals(mst.getEdgeCount(), 3)
    var totalWeight = 0f
    mst.getEdges().foreach(e => totalWeight += e.getWeight())
    assertEquals(totalWeight, 6.0f)
  }

  test("shortest path in undirected graph") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B", 1.0f)
    graph.addEdge("B", "C", 2.0f)
    graph.addEdge("A", "C", 10.0f)

    val path = graph.algorithms().findShortestPath("A", "C")
    assertEquals(path.size(), 3)
    assertEquals(path.getFirst(), "A")
    assertEquals(path.getLast(), "C")
    assertEquals(path.getLength(), 3.0f)
  }

  test("cycle detection in undirected graph") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B")
    graph.addEdge("B", "C")
    graph.addEdge("C", "A")
    assert(graph.algorithms().containsCycle())
  }

  test("no cycle in tree") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B")
    graph.addEdge("B", "C")
    assert(!graph.algorithms().containsCycle())
  }

  test("isDirected returns false") {
    val graph = new UndirectedGraph[String]()
    assert(!graph.isDirected())
  }

  test("graph toString") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B"))
    graph.addEdge("A", "B")
    assertEquals(graph.toString, "Undirected graph with 2 vertices and 1 edges")
  }

  test("degree") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B")
    graph.addEdge("A", "C")
    assertEquals(graph.getDegree("A"), 2)
    assertEquals(graph.getDegree("B"), 1)
  }

  test("complete graph builder for undirected") {
    val graph = new UndirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3))
    GraphBuilder.buildCompleteGraph(graph)
    assertEquals(graph.getEdgeCount(), 3)
  }
}
