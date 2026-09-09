package sge
package graphs

import munit.FunSuite

class BinaryHeapSuite extends FunSuite {

  private def makeNode(value: Float): Node[String] = {
    val node = new Node[String]("v", false, 0)
    node.heapValue = value
    node
  }

  test("add and peek") {
    val heap = new BinaryHeap()
    heap.add(makeNode(5.0f))
    heap.add(makeNode(3.0f))
    heap.add(makeNode(7.0f))
    assertEquals(heap.size, 3)
    assertEquals(heap.peek().heapValue, 3.0f)
  }

  test("pop removes smallest") {
    val heap = new BinaryHeap()
    heap.add(makeNode(5.0f))
    heap.add(makeNode(1.0f))
    heap.add(makeNode(3.0f))

    assertEquals(heap.pop().heapValue, 1.0f)
    assertEquals(heap.size, 2)
    assertEquals(heap.pop().heapValue, 3.0f)
    assertEquals(heap.size, 1)
    assertEquals(heap.pop().heapValue, 5.0f)
    assertEquals(heap.size, 0)
  }

  test("isEmpty and notEmpty") {
    val heap = new BinaryHeap()
    assertEquals(heap.isEmpty(), true)
    assertEquals(heap.notEmpty(), false)

    heap.add(makeNode(1.0f))
    assertEquals(heap.isEmpty(), false)
    assertEquals(heap.notEmpty(), true)

    heap.pop()
    assertEquals(heap.isEmpty(), true)
    assertEquals(heap.notEmpty(), false)
  }

  test("clear empties the heap") {
    val heap = new BinaryHeap()
    heap.add(makeNode(1.0f))
    heap.add(makeNode(2.0f))
    heap.add(makeNode(3.0f))
    assertEquals(heap.size, 3)
    heap.clear()
    assertEquals(heap.size, 0)
    assertEquals(heap.isEmpty(), true)
  }

  test("add with explicit value") {
    val heap = new BinaryHeap()
    val node = new Node[String]("v", false, 0)
    heap.add(node, 42.0f)
    assertEquals(node.heapValue, 42.0f)
    assertEquals(heap.peek().heapValue, 42.0f)
  }

  test("setValue moves node up") {
    val heap = new BinaryHeap()
    val n1 = makeNode(10.0f)
    val n3 = makeNode(30.0f)
    heap.add(n1)
    heap.add(makeNode(20.0f))
    heap.add(n3)
    assertEquals(heap.peek().heapValue, 10.0f)
    heap.setValue(n3, 1.0f)
    assertEquals(heap.peek().heapValue, 1.0f)
  }

  test("setValue moves node down") {
    val heap = new BinaryHeap()
    val n1 = makeNode(1.0f)
    heap.add(n1)
    heap.add(makeNode(2.0f))
    heap.add(makeNode(3.0f))
    assertEquals(heap.peek().heapValue, 1.0f)
    heap.setValue(n1, 100.0f)
    assertEquals(heap.peek().heapValue, 2.0f)
  }

  test("contains finds node by identity") {
    val heap = new BinaryHeap()
    val n1 = makeNode(1.0f)
    val n2 = makeNode(2.0f)
    val n3 = makeNode(3.0f)
    heap.add(n1)
    heap.add(n2)
    assertEquals(heap.contains(n1, true), true)
    assertEquals(heap.contains(n2, true), true)
    assertEquals(heap.contains(n3, true), false)
  }

  test("contains returns false for empty heap") {
    val heap = new BinaryHeap()
    assertEquals(heap.contains(makeNode(1.0f), true), false)
  }

  test("contains after pop") {
    val heap = new BinaryHeap()
    val n1 = makeNode(1.0f)
    val n2 = makeNode(2.0f)
    heap.add(n1)
    heap.add(n2)
    assertEquals(heap.contains(n1, true), true)
    heap.pop()
    assertEquals(heap.contains(n1, true), false)
    assertEquals(heap.contains(n2, true), true)
  }

  test("equals with same values") {
    val heap1 = new BinaryHeap()
    val heap2 = new BinaryHeap()
    heap1.add(makeNode(1.0f))
    heap1.add(makeNode(2.0f))
    heap2.add(makeNode(1.0f))
    heap2.add(makeNode(2.0f))
    assert(heap1.equals(heap2))
    assert(heap2.equals(heap1))
  }

  test("equals with different values") {
    val heap1 = new BinaryHeap()
    val heap2 = new BinaryHeap()
    heap1.add(makeNode(1.0f))
    heap1.add(makeNode(2.0f))
    heap2.add(makeNode(1.0f))
    heap2.add(makeNode(3.0f))
    assert(!heap1.equals(heap2))
  }

  test("equals with different sizes") {
    val heap1 = new BinaryHeap()
    val heap2 = new BinaryHeap()
    heap1.add(makeNode(1.0f))
    heap2.add(makeNode(1.0f))
    heap2.add(makeNode(2.0f))
    assert(!heap1.equals(heap2))
  }

  test("equals with non-BinaryHeap returns false") {
    val heap = new BinaryHeap()
    assert(!heap.equals("not a heap"))
    assert(!heap.equals(42))
    assert(!heap.equals(null))
  }

  test("equals empty heaps") {
    assert(new BinaryHeap().equals(new BinaryHeap()))
  }

  test("hashCode consistent with equals") {
    val heap1 = new BinaryHeap()
    val heap2 = new BinaryHeap()
    heap1.add(makeNode(1.0f))
    heap1.add(makeNode(2.0f))
    heap2.add(makeNode(1.0f))
    heap2.add(makeNode(2.0f))
    assertEquals(heap1.hashCode(), heap2.hashCode())
  }

  test("hashCode changes when elements change") {
    val heap = new BinaryHeap()
    val h1   = heap.hashCode()
    heap.add(makeNode(5.0f))
    assert(h1 != heap.hashCode(), "Hash should change after adding element")
  }

  test("toString empty heap") {
    assertEquals(new BinaryHeap().toString(), "[]")
  }

  test("toString single element") {
    val heap = new BinaryHeap()
    heap.add(makeNode(3.0f))
    val s = heap.toString()
    assert(s == "[3.0]" || s == "[3]", s"Expected [3.0] or [3], got $s")
  }

  test("toString multiple elements") {
    val heap = new BinaryHeap()
    heap.add(makeNode(1.0f))
    heap.add(makeNode(2.0f))
    heap.add(makeNode(3.0f))
    val str = heap.toString()
    assert(str.startsWith("[1.0") || str.startsWith("[1,") || str.startsWith("[1]"))
    assert(str.endsWith("]"))
  }

  test("heap grows beyond initial capacity") {
    val heap = new BinaryHeap(2)
    for (i <- 0 until 20) heap.add(makeNode(i.toFloat))
    assertEquals(heap.size, 20)
    var prev = -1.0f
    while (heap.notEmpty()) {
      val v = heap.pop().heapValue
      assert(v >= prev, s"Expected sorted order, got $v after $prev")
      prev = v
    }
  }

  test("pop and re-add maintains heap property") {
    val heap = new BinaryHeap()
    heap.add(makeNode(5.0f))
    heap.add(makeNode(3.0f))
    heap.add(makeNode(7.0f))
    assertEquals(heap.pop().heapValue, 3.0f)
    heap.add(makeNode(1.0f))
    assertEquals(heap.peek().heapValue, 1.0f)
  }
}
