package sge
package graphs

import munit.FunSuite

import sge.graphs.utils.SearchProcessor
import sge.graphs.algorithms.SearchStep

class DirectedGraphSuite extends FunSuite {

  test("add and remove vertices") {
    val graph = new DirectedGraph[String]()
    assert(graph.addVertex("A"))
    assert(graph.addVertex("B"))
    assert(!graph.addVertex("A"))
    assertEquals(graph.size(), 2)
    assert(graph.contains("A"))
    assert(graph.removeVertex("A"))
    assertEquals(graph.size(), 1)
    assert(!graph.contains("A"))
  }

  test("add and remove edges") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B", 2.0f)
    graph.addEdge("B", "C", 3.0f)
    assertEquals(graph.getEdgeCount(), 2)
    assert(graph.edgeExists("A", "B"))
    assert(!graph.edgeExists("B", "A"))
    assert(graph.removeEdge("A", "B"))
    assertEquals(graph.getEdgeCount(), 1)
    assert(!graph.edgeExists("A", "B"))
  }

  test("shortest path with Dijkstra") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C", "D"))
    graph.addEdge("A", "B", 1.0f)
    graph.addEdge("B", "C", 2.0f)
    graph.addEdge("A", "C", 10.0f)
    graph.addEdge("C", "D", 1.0f)

    val path = graph.algorithms().findShortestPath("A", "D")
    assertEquals(path.size(), 4)
    assertEquals(path.getFirst(), "A")
    assertEquals(path.getLast(), "D")
    assertEquals(path.getLength(), 4.0f)
  }

  test("shortest path - unreachable") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B"))
    val path = graph.algorithms().findShortestPath("A", "B")
    assertEquals(path.isEmpty(), true)
  }

  test("BFS visits all reachable vertices") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3, 4))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(1: Integer, 3: Integer)
    graph.addEdge(2: Integer, 4: Integer)

    var visited = List.empty[Integer]
    graph.algorithms().breadthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit =
          visited = visited :+ step.vertex()
      }
    )
    assertEquals(visited.size, 4)
    assertEquals(visited.head.intValue, 1)
    assert(visited.exists(_.intValue == 4))
  }

  test("DFS visits all reachable vertices") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3, 4))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(1: Integer, 3: Integer)
    graph.addEdge(2: Integer, 4: Integer)

    var visited = List.empty[Integer]
    graph.algorithms().depthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit =
          visited = visited :+ step.vertex()
      }
    )
    assertEquals(visited.size, 4)
    assertEquals(visited.head.intValue, 1)
  }

  test("cycle detection - has cycle") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B")
    graph.addEdge("B", "C")
    graph.addEdge("C", "A")
    assert(graph.algorithms().containsCycle())
  }

  test("cycle detection - no cycle") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B")
    graph.addEdge("B", "C")
    assert(!graph.algorithms().containsCycle())
  }

  test("topological sort") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C", "D"))
    graph.addEdge("A", "B")
    graph.addEdge("A", "C")
    graph.addEdge("B", "D")
    graph.addEdge("C", "D")

    val success = graph.topologicalSort()
    assert(success)

    val verts = scala.collection.mutable.ArrayBuffer.empty[String]
    graph.getVertices().foreach(v => verts += v)
    assert(verts.indexOf("A") < verts.indexOf("B"))
    assert(verts.indexOf("A") < verts.indexOf("C"))
    assert(verts.indexOf("B") < verts.indexOf("D"))
    assert(verts.indexOf("C") < verts.indexOf("D"))
  }

  test("edge weight") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B"))
    val edge = graph.addEdge("A", "B", 5.0f)
    assertEquals(edge.getWeight(), 5.0f)
  }

  test("graph toString") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B"))
    graph.addEdge("A", "B")
    assertEquals(graph.toString, "Directed graph with 2 vertices and 1 edges")
  }

  test("complete graph builder") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3))
    GraphBuilder.buildCompleteGraph(graph)
    assertEquals(graph.getEdgeCount(), 6)
  }

  test("minimum distance") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B", 3.0f)
    graph.addEdge("B", "C", 4.0f)
    assertEquals(graph.algorithms().findMinimumDistance("A", "C"), 7.0f)
  }

  test("isConnected between vertices") {
    val graph = new DirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C"))
    graph.addEdge("A", "B")
    assert(graph.algorithms().isConnected("A", "B"))
    assert(!graph.algorithms().isConnected("A", "C"))
  }

  test("vertex can be disconnected from directed graph") {
    val graph = new DirectedGraph[Integer]()
    val n     = 10
    for (i <- 0 until n) graph.addVertex(Integer.valueOf(i))
    for (i <- 0 until n - 1) graph.addEdge(Integer.valueOf(i), Integer.valueOf(i + 1))

    assertEquals(graph.size(), n)
    assertEquals(graph.getEdgeCount(), n - 1)
    graph.disconnect(Integer.valueOf(n / 2))
    assertEquals(graph.size(), n)
    assertEquals(graph.getEdgeCount(), n - 3)
  }
}
