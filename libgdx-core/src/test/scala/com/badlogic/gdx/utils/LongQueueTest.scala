package com.badlogic.gdx.utils

class LongQueueTest extends munit.FunSuite {
  test("addFirstAndLastTest")({
    val queue: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    queue.addFirst(1)
    queue.addLast(2)
    queue.addFirst(3)
    queue.addLast(4)
    assertEquals(queue.indexOf(3), 0)
    assertEquals(queue.indexOf(1), 1)
    assertEquals(queue.indexOf(2), 2)
    assertEquals(queue.indexOf(4), 3)
  })
  test("removeLastTest")({
    val queue: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    queue.addLast(1)
    queue.addLast(2)
    queue.addLast(3)
    queue.addLast(4)
    assertEquals(queue.size, 4)
    assertEquals(queue.indexOf(4), 3)
    assertEquals(queue.removeLast(), 4)
    assertEquals(queue.size, 3)
    assertEquals(queue.indexOf(3), 2)
    assertEquals(queue.removeLast(), 3)
    assertEquals(queue.size, 2)
    assertEquals(queue.indexOf(2), 1)
    assertEquals(queue.removeLast(), 2)
    assertEquals(queue.size, 1)
    assertEquals(queue.indexOf(1), 0)
    assertEquals(queue.removeLast(), 1)
    assertEquals(queue.size, 0)
  })
  test("removeFirstTest")({
    val queue: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    queue.addLast(1)
    queue.addLast(2)
    queue.addLast(3)
    queue.addLast(4)
    assertEquals(queue.size, 4)
    assertEquals(queue.indexOf(1), 0)
    assertEquals(queue.removeFirst(), 1)
    assertEquals(queue.size, 3)
    assertEquals(queue.indexOf(2), 0)
    assertEquals(queue.removeFirst(), 2)
    assertEquals(queue.size, 2)
    assertEquals(queue.indexOf(3), 0)
    assertEquals(queue.removeFirst(), 3)
    assertEquals(queue.size, 1)
    assertEquals(queue.indexOf(4), 0)
    assertEquals(queue.removeFirst(), 4)
    assertEquals(queue.size, 0)
  })
  test("resizableQueueTest")({
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue(8)
    assert(q.size == 0, "New queue is not empty!");
    { var i: scala.Int = 0; while (i < 100) { {
      { var j: scala.Int = 0; while (j < i) { {
        try {
          q.addLast(j)
        } catch {
          case e: java.lang.IllegalStateException => {
            fail(((("Failed to add element " + j) + " (") + i) + ")")
          }
        }
        val peeked: scala.Long = q.last()
        assert(peeked == j, ((((("peekLast shows " + peeked) + ", should be ") + j) + " (") + i) + ")")
        val size: scala.Int = q.size
        assert(size == (j + 1), ((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")")
      }; j = j + 1 } }
      if (i != 0) {
        val peek: scala.Long = q.first()
        assert(peek == 0, ((("First thing is not zero but " + peek) + " (") + i) + ")")
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: scala.Long = q.removeFirst()
        assert(pop == j, ((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")")
        val size: scala.Int = q.size
        assert(size == ((i - 1) - j), ((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")")
      }; j = j + 1 } }
      assert(q.size == 0, "Not empty after cycle " + i)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addLast(42)
    }; i = i + 1 } }
    q.clear()
    assert(q.size == 0, "Clear did not clear properly")
  })
  test("resizableDequeTest")({
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue(8)
    assert(q.size == 0, "New deque is not empty!");
    { var i: scala.Int = 0; while (i < 100) { {
      { var j: scala.Int = 0; while (j < i) { {
        try {
          q.addFirst(j)
        } catch {
          case e: java.lang.IllegalStateException => {
            fail(((("Failed to add element " + j) + " (") + i) + ")")
          }
        }
        val peeked: scala.Long = q.first()
        assert(peeked == j, ((((("peek shows " + peeked) + ", should be ") + j) + " (") + i) + ")")
        val size: scala.Int = q.size
        assert(size == (j + 1), ((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")")
      }; j = j + 1 } }
      if (i != 0) {
        val peek: scala.Long = q.last()
        assert(peek == 0, ((("Last thing is not zero but " + peek) + " (") + i) + ")")
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: scala.Long = q.removeLast()
        assert(pop == j, ((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")")
        val size: scala.Int = q.size
        assert(size == ((i - 1) - j), ((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")")
      }; j = j + 1 } }
      assert(q.size == 0, "Not empty after cycle " + i)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addFirst(42)
    }; i = i + 1 } }
    q.clear()
    assert(q.size == 0, "Clear did not clear properly")
  })
  test("getTest")({
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue(7);
    { var i: scala.Int = 0; while (i < 5) { {
      { var j: scala.Int = 0; while (j < 4) { {
        q.addLast(j)
      }; j = j + 1 } }
      assertEquals(q.first(), q.get(0), ("get(0) is not equal to peek (" + i) + ")")
      assertEquals(q.last(), q.get(q.size - 1), ("get(size-1) is not equal to peekLast (" + i) + ")");
      { var j: scala.Int = 0; while (j < 4) { {
        assert(q.get(j) == j)
      }; j = j + 1 } };
      { var j: scala.Int = 0; while (j < (4 - 1)) { {
        q.removeFirst()
        assertEquals(q.first(), q.get(0), ("get(0) is not equal to peek (" + i) + ")")
      }; j = j + 1 } }
      q.removeFirst()
      assert(q.size == 0)
      try {
        q.get(0)
        fail("get() on empty queue did not throw")
      } catch {
        case ignore: java.lang.IndexOutOfBoundsException => {
          ()
        }
      }
    }; i = i + 1 } }
  })
  test("removeTest")({
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j)
    }; j = j + 1 } }
    this.assertValues(q, scala.Array[scala.Long](0, 1, 2, 3, 4, 5, 6))
    q.removeIndex(0)
    this.assertValues(q, scala.Array[scala.Long](1, 2, 3, 4, 5, 6))
    q.removeIndex(1)
    this.assertValues(q, scala.Array[scala.Long](1, 3, 4, 5, 6))
    q.removeIndex(4)
    this.assertValues(q, scala.Array[scala.Long](1, 3, 4, 5))
    q.removeIndex(2)
    this.assertValues(q, scala.Array[scala.Long](1, 3, 5))
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j)
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j)
    }; j = j + 1 } }
    this.assertValues(q, scala.Array[scala.Long](0, 1, 2, 3, 4, 5, 6))
    q.removeIndex(1)
    this.assertValues(q, scala.Array[scala.Long](0, 2, 3, 4, 5, 6))
    q.removeIndex(0)
    this.assertValues(q, scala.Array[scala.Long](2, 3, 4, 5, 6))
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j)
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j)
    }; j = j + 1 } }
    this.assertValues(q, scala.Array[scala.Long](0, 1, 2, 3, 4, 5, 6))
    q.removeIndex(5)
    this.assertValues(q, scala.Array[scala.Long](0, 1, 2, 3, 4, 6))
    q.removeIndex(5)
    this.assertValues(q, scala.Array[scala.Long](0, 1, 2, 3, 4))
  })
  test("indexOfTest")({
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j)
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      assertEquals(j, q.indexOf(j))
    }; j = j + 1 } }
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j)
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j)
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      assertEquals(j, q.indexOf(j))
    }; j = j + 1 } }
  })
  test("toStringTest")({
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue(1)
    assert(q.toString().equals("[]"))
    q.addLast(4)
    assert(q.toString().equals("[4]"))
    q.addLast(5)
    q.addLast(6)
    q.addLast(7)
    assert(q.toString().equals("[4, 5, 6, 7]"))
  })
  test("hashEqualsTest")({
    val q1: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    val q2: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    this.assertEqualsAndHash(q1, q2)
    q1.addFirst(1)
    assertNotEquals(q2, q1)
    q2.addFirst(1)
    this.assertEqualsAndHash(q1, q2)
    q1.clear()
    q1.addLast(1)
    q1.addLast(2)
    q2.addLast(2)
    this.assertEqualsAndHash(q1, q2);
    { var i: scala.Int = 0; while (i < 100) { {
      q1.addLast(i)
      q1.addLast(i)
      q1.removeFirst()
      assertNotEquals(q2, q1)
      q2.addLast(i)
      q2.addLast(i)
      q2.removeFirst()
      this.assertEqualsAndHash(q1, q2)
    }; i = i + 1 } }
  })
  private def assertEqualsAndHash(q1: com.badlogic.gdx.utils.LongQueue, q2: com.badlogic.gdx.utils.LongQueue): scala.Unit = {
    assertEquals(q2, q1)
    assertEquals(q2.hashCode(), q1.hashCode(), "Hash codes are not equal")
  }
  private def assertValues(q: com.badlogic.gdx.utils.LongQueue, values: scala.Array[scala.Long]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      assertEquals(q.get(i), values(i))
    }; i = i + 1 } }
  }
}