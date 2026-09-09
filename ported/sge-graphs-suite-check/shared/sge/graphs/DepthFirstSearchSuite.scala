package sge
package graphs

import scala.collection.mutable.ArrayBuffer

import munit.FunSuite

import sge.graphs.algorithms.SearchStep
import sge.graphs.utils.SearchProcessor

class DepthFirstSearchSuite extends FunSuite {

  test("DFS visits all reachable vertices in directed graph") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3, 4, 5))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(1: Integer, 3: Integer)
    graph.addEdge(2: Integer, 4: Integer)
    graph.addEdge(3: Integer, 5: Integer)

    val visited = ArrayBuffer.empty[Integer]
    graph.algorithms().depthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit =
          visited += step.vertex()
      }
    )

    assertEquals(visited.size, 5)
    assertEquals(visited.head.intValue, 1)
    assert(visited.exists(_.intValue == 2))
    assert(visited.exists(_.intValue == 3))
    assert(visited.exists(_.intValue == 4))
    assert(visited.exists(_.intValue == 5))
  }

  test("DFS only visits reachable vertices") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3, 4))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(3: Integer, 4: Integer)

    val visited = ArrayBuffer.empty[Integer]
    graph.algorithms().depthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit =
          visited += step.vertex()
      }
    )

    assertEquals(visited.size, 2)
    assert(visited.exists(_.intValue == 1))
    assert(visited.exists(_.intValue == 2))
    assert(!visited.exists(_.intValue == 3))
    assert(!visited.exists(_.intValue == 4))
  }

  test("DFS visits in depth-first order") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3, 4))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(2: Integer, 3: Integer)
    graph.addEdge(3: Integer, 4: Integer)

    val visited = ArrayBuffer.empty[Int]
    graph.algorithms().depthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit =
          visited += step.vertex().intValue
      }
    )
    assertEquals(visited.toList, List(1, 2, 3, 4))
  }

  test("DFS tracks depth correctly") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(2: Integer, 3: Integer)

    val depths = ArrayBuffer.empty[(Int, Int)]
    graph.algorithms().depthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit =
          depths += ((step.vertex().intValue, step.depth()))
      }
    )

    assertEquals(depths.size, 3)
    assertEquals(depths.find(_._1 == 1).get._2, 0)
    assertEquals(depths.find(_._1 == 2).get._2, 1)
    assertEquals(depths.find(_._1 == 3).get._2, 2)
  }

  test("DFS with terminate stops early") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3, 4))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(2: Integer, 3: Integer)
    graph.addEdge(3: Integer, 4: Integer)

    val visited = ArrayBuffer.empty[Int]
    graph.algorithms().depthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit = {
          visited += step.vertex().intValue
          if (step.vertex().intValue == 2) step.terminate()
        }
      }
    )

    assert(visited.contains(1))
    assert(visited.contains(2))
    assert(!visited.contains(3))
    assert(!visited.contains(4))
  }

  test("DFS with ignore skips neighbours") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3, 4))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(2: Integer, 3: Integer)
    graph.addEdge(1: Integer, 4: Integer)

    val visited = ArrayBuffer.empty[Int]
    graph.algorithms().depthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit = {
          visited += step.vertex().intValue
          if (step.vertex().intValue == 2) step.ignore()
        }
      }
    )

    assert(visited.contains(1))
    assert(visited.contains(2))
    assert(!visited.contains(3), "Vertex 3 should not be visited when vertex 2 is ignored")
    assert(visited.contains(4))
  }

  test("DFS on undirected graph visits all connected vertices") {
    val graph = new UndirectedGraph[String]()
    graph.addVertices(scala.Array("A", "B", "C", "D"))
    graph.addEdge("A", "B")
    graph.addEdge("B", "C")
    graph.addEdge("C", "D")

    val visited = ArrayBuffer.empty[String]
    graph.algorithms().depthFirstSearch("A",
      new SearchProcessor[String] {
        def accept(step: SearchStep[String]): Unit =
          visited += step.vertex()
      }
    )
    assertEquals(visited.size, 4)
  }

  test("DFS on single vertex graph") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertex(42: Integer)

    val visited = ArrayBuffer.empty[Int]
    graph.algorithms().depthFirstSearch(42: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit =
          visited += step.vertex().intValue
      }
    )
    assertEquals(visited.toList, List(42))
  }

  test("DFS handles cycles without infinite loop") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(2: Integer, 3: Integer)
    graph.addEdge(3: Integer, 1: Integer)

    val visited = ArrayBuffer.empty[Integer]
    graph.algorithms().depthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit =
          visited += step.vertex()
      }
    )
    assertEquals(visited.size, 3)
  }

  test("DFS tracks count correctly") {
    val graph = new DirectedGraph[Integer]()
    graph.addVertices(scala.Array[Integer](1, 2, 3))
    graph.addEdge(1: Integer, 2: Integer)
    graph.addEdge(2: Integer, 3: Integer)

    val counts = ArrayBuffer.empty[(Int, Int)]
    graph.algorithms().depthFirstSearch(1: Integer,
      new SearchProcessor[Integer] {
        def accept(step: SearchStep[Integer]): Unit =
          counts += ((step.vertex().intValue, step.count()))
      }
    )

    assertEquals(counts.find(_._1 == 1).get._2, 0)
    assertEquals(counts.find(_._1 == 2).get._2, 1)
    assertEquals(counts.find(_._1 == 3).get._2, 2)
  }
}
