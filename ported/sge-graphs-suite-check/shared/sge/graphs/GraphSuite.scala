package sge
package graphs

import munit.FunSuite

final case class Vector2(x: Float, y: Float) {
  def dst(v: Vector2): Float = {
    val xd = v.x - x
    val yd = v.y - y
    Math.sqrt((xd * xd + yd * yd).toDouble).toFloat
  }
  override def toString: String = s"($x, $y)"
}

object GraphTestUtils {
  def makeGridGraph[V >: Vector2 <: java.lang.Object](graph: Graph[V], n: Int): Graph[V] = {
    for {
      i <- 0 until n
      j <- 0 until n
    } graph.addVertex(Vector2(i.toFloat, j.toFloat).asInstanceOf[V])

    for {
      i <- 0 until n
      j <- 0 until n
    } {
      if (i < n - 1) {
        val v1 = Vector2(i.toFloat, j.toFloat).asInstanceOf[V]
        val v2 = Vector2((i + 1).toFloat, j.toFloat).asInstanceOf[V]
        val d  = Vector2(i.toFloat, j.toFloat).dst(Vector2((i + 1).toFloat, j.toFloat))
        graph.addEdge(v1, v2, d)
        if (graph.isDirected()) graph.addEdge(v2, v1, d)
      }
      if (j < n - 1) {
        val v1 = Vector2(i.toFloat, j.toFloat).asInstanceOf[V]
        val v2 = Vector2(i.toFloat, (j + 1).toFloat).asInstanceOf[V]
        val d  = Vector2(i.toFloat, j.toFloat).dst(Vector2(i.toFloat, (j + 1).toFloat))
        graph.addEdge(v1, v2, d)
        if (graph.isDirected()) graph.addEdge(v2, v1, d)
      }
    }
    graph
  }
}

class GraphSuite extends FunSuite {

  test("vertices can be sorted") {
    val graph = new UndirectedGraph[Integer]()
    val list  = List[Integer](9, 4, 3, 2, 5, 7, 6, 0, 8, 1)
    list.foreach(graph.addVertex)
    graph.sortVertices(java.util.Comparator.comparingInt[Integer](_.intValue))
    var i = 0
    graph.getVertices().foreach { vertex =>
      assertEquals(vertex.intValue, i)
      i += 1
    }
  }

  test("edges can be sorted") {
    val graph = new DirectedGraph[Integer]()
    val list  = List[Integer](9, 4, 3, 2, 5, 7, 6, 0, 8, 1)
    for (j <- list.indices) graph.addVertex(Integer.valueOf(j))
    for (j <- list.indices) graph.addEdge(list(j), list(list.size - j - 1))
    graph.sortEdges(java.util.Comparator.comparingInt[Connection[Integer]](_.getA().intValue))
    var i = 0
    graph.getEdges().foreach { edge =>
      assertEquals(edge.getA().intValue, i)
      i += 1
    }
  }

  test("removeVertexIf removes matching vertices") {
    val n     = 16
    val graph = new UndirectedGraph[Integer]()
    for (i <- 0 until n) graph.addVertex(Integer.valueOf(i))
    graph.removeVertexIf((i: Integer) => i.intValue % 2 == 0)

    for (i <- 0 until n by 2)
      assert(!graph.contains(Integer.valueOf(i)), s"Vertex $i should have been removed")
    for (i <- 1 until n by 2)
      assert(graph.contains(Integer.valueOf(i)), s"Vertex $i should still be present")
  }

  test("removeVertexIf with BadHashInteger") {
    val n        = 16
    val badGraph = new UndirectedGraph[BadHashInteger]()
    for (i <- 0 until n) badGraph.addVertex(BadHashInteger(i))
    badGraph.removeVertexIf((i: BadHashInteger) => i.i % 2 == 0)

    for (i <- 0 until n by 2)
      assert(!badGraph.contains(BadHashInteger(i)), s"Vertex $i should have been removed")
    for (i <- 1 until n by 2)
      assert(badGraph.contains(BadHashInteger(i)), s"Vertex $i should still be present")
  }

  test("removeEdgeIf on undirected graph") {
    val n                  = 5
    val undirectedGraph    = GraphTestUtils.makeGridGraph(new UndirectedGraph[Vector2](), n)
    val expectedUndirected = 2 * n * (n - 1)
    assertEquals(undirectedGraph.getEdgeCount(), expectedUndirected)

    val v1 = Vector2(1f, 1f)
    val v2 = Vector2(2f, 1f)
    undirectedGraph.removeEdgeIf((e: Edge[Vector2]) => e.hasEndpoint(v1) || e.hasEndpoint(v2))

    assertEquals(undirectedGraph.getEdgeCount(), expectedUndirected - 7)

    undirectedGraph.getEdges().foreach { e =>
      assert(!(e.hasEndpoint(v1) || e.hasEndpoint(v2)), s"Edge $e should have been removed")
    }
  }

  test("removeEdgeIf on directed graph") {
    val n                = 5
    val diGraph          = GraphTestUtils.makeGridGraph(new DirectedGraph[Vector2](), n)
    val expectedDirected = 2 * 2 * n * (n - 1)
    assertEquals(diGraph.getEdgeCount(), expectedDirected)

    val v1 = Vector2(1f, 1f)
    val v2 = Vector2(2f, 1f)
    diGraph.removeEdgeIf((e: Edge[Vector2]) => e.getA().equals(v1) || e.getA().equals(v2))

    assertEquals(diGraph.getEdgeCount(), expectedDirected - 8)

    diGraph.getEdges().foreach { e =>
      assert(!(e.getA().equals(v1) || e.getA().equals(v2)), s"Edge $e should have been removed via removeEdgeIf")
    }
  }
}
