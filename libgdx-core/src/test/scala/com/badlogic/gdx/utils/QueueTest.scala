package com.badlogic.gdx.utils

class QueueTest extends munit.FunSuite {
  test("addFirstAndLastTest")({
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    queue.addFirst(1.asInstanceOf[java.lang.Integer])
    queue.addLast(2.asInstanceOf[java.lang.Integer])
    queue.addFirst(3.asInstanceOf[java.lang.Integer])
    queue.addLast(4.asInstanceOf[java.lang.Integer])
    assertEquals(queue.indexOf(3.asInstanceOf[java.lang.Integer], true), 0)
    assertEquals(queue.indexOf(1.asInstanceOf[java.lang.Integer], true), 1)
    assertEquals(queue.indexOf(2.asInstanceOf[java.lang.Integer], true), 2)
    assertEquals(queue.indexOf(4.asInstanceOf[java.lang.Integer], true), 3)
  })
  test("removeLastTest")({
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    queue.addLast(1.asInstanceOf[java.lang.Integer])
    queue.addLast(2.asInstanceOf[java.lang.Integer])
    queue.addLast(3.asInstanceOf[java.lang.Integer])
    queue.addLast(4.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 4)
    assertEquals(queue.indexOf(4.asInstanceOf[java.lang.Integer], true), 3)
    assertEquals(queue.removeLast().asInstanceOf[java.lang.Object], 4.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 3)
    assertEquals(queue.indexOf(3.asInstanceOf[java.lang.Integer], true), 2)
    assertEquals(queue.removeLast().asInstanceOf[java.lang.Object], 3.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 2)
    assertEquals(queue.indexOf(2.asInstanceOf[java.lang.Integer], true), 1)
    assertEquals(queue.removeLast().asInstanceOf[java.lang.Object], 2.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 1)
    assertEquals(queue.indexOf(1.asInstanceOf[java.lang.Integer], true), 0)
    assertEquals(queue.removeLast().asInstanceOf[java.lang.Object], 1.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 0)
  })
  test("removeFirstTest")({
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    queue.addLast(1.asInstanceOf[java.lang.Integer])
    queue.addLast(2.asInstanceOf[java.lang.Integer])
    queue.addLast(3.asInstanceOf[java.lang.Integer])
    queue.addLast(4.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 4)
    assertEquals(queue.indexOf(1.asInstanceOf[java.lang.Integer], true), 0)
    assertEquals(queue.removeFirst().asInstanceOf[java.lang.Object], 1.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 3)
    assertEquals(queue.indexOf(2.asInstanceOf[java.lang.Integer], true), 0)
    assertEquals(queue.removeFirst().asInstanceOf[java.lang.Object], 2.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 2)
    assertEquals(queue.indexOf(3.asInstanceOf[java.lang.Integer], true), 0)
    assertEquals(queue.removeFirst().asInstanceOf[java.lang.Object], 3.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 1)
    assertEquals(queue.indexOf(4.asInstanceOf[java.lang.Integer], true), 0)
    assertEquals(queue.removeFirst().asInstanceOf[java.lang.Object], 4.asInstanceOf[java.lang.Integer])
    assertEquals(queue.size, 0)
  })
  test("resizableQueueTest")({
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](8)
    assert(q.size == 0, "New queue is not empty!");
    { var i: scala.Int = 0; while (i < 100) { {
      { var j: scala.Int = 0; while (j < i) { {
        try {
          q.addLast(j.asInstanceOf[java.lang.Integer])
        } catch {
          case e: java.lang.IllegalStateException => {
            fail(((("Failed to add element " + j) + " (") + i) + ")")
          }
        }
        val peeked: java.lang.Integer = q.last()
        assert(peeked.equals(j.asInstanceOf[java.lang.Integer]), ((((("peekLast shows " + peeked) + ", should be ") + j) + " (") + i) + ")")
        val size: scala.Int = q.size
        assert(size == (j + 1), ((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")")
      }; j = j + 1 } }
      if (i != 0) {
        val peek: java.lang.Integer = q.first()
        assert(peek == 0, ((("First thing is not zero but " + peek) + " (") + i) + ")")
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: java.lang.Integer = q.removeFirst()
        assert(pop == j, ((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")")
        val size: scala.Int = q.size
        assert(size == ((i - 1) - j), ((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")")
      }; j = j + 1 } }
      assert(q.size == 0, "Not empty after cycle " + i)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addLast(42.asInstanceOf[java.lang.Integer])
    }; i = i + 1 } }
    q.clear()
    assert(q.size == 0, "Clear did not clear properly")
  })
  test("resizableDequeTest")({
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](8)
    assert(q.size == 0, "New deque is not empty!");
    { var i: scala.Int = 0; while (i < 100) { {
      { var j: scala.Int = 0; while (j < i) { {
        try {
          q.addFirst(j.asInstanceOf[java.lang.Integer])
        } catch {
          case e: java.lang.IllegalStateException => {
            fail(((("Failed to add element " + j) + " (") + i) + ")")
          }
        }
        val peeked: java.lang.Integer = q.first()
        assert(peeked.equals(j.asInstanceOf[java.lang.Integer]), ((((("peek shows " + peeked) + ", should be ") + j) + " (") + i) + ")")
        val size: scala.Int = q.size
        assert(size == (j + 1), ((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")")
      }; j = j + 1 } }
      if (i != 0) {
        val peek: java.lang.Integer = q.last()
        assert(peek == 0, ((("Last thing is not zero but " + peek) + " (") + i) + ")")
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: java.lang.Integer = q.removeLast()
        assert(pop == j, ((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")")
        val size: scala.Int = q.size
        assert(size == ((i - 1) - j), ((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")")
      }; j = j + 1 } }
      assert(q.size == 0, "Not empty after cycle " + i)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addFirst(42.asInstanceOf[java.lang.Integer])
    }; i = i + 1 } }
    q.clear()
    assert(q.size == 0, "Clear did not clear properly")
  })
  test("getTest")({
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](7);
    { var i: scala.Int = 0; while (i < 5) { {
      { var j: scala.Int = 0; while (j < 4) { {
        q.addLast(j.asInstanceOf[java.lang.Integer])
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
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } }
    this.assertValues(q, scala.Array[java.lang.Integer](0.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    q.removeIndex(0)
    this.assertValues(q, scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    q.removeIndex(1)
    this.assertValues(q, scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    q.removeIndex(4)
    this.assertValues(q, scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    q.removeIndex(2)
    this.assertValues(q, scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j.asInstanceOf[java.lang.Integer])
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } }
    this.assertValues(q, scala.Array[java.lang.Integer](0.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    q.removeIndex(1)
    this.assertValues(q, scala.Array[java.lang.Integer](0.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    q.removeIndex(0)
    this.assertValues(q, scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j.asInstanceOf[java.lang.Integer])
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } }
    this.assertValues(q, scala.Array[java.lang.Integer](0.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    q.removeIndex(5)
    this.assertValues(q, scala.Array[java.lang.Integer](0.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    q.removeIndex(5)
    this.assertValues(q, scala.Array[java.lang.Integer](0.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer]))
  })
  test("indexOfTest")({
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      assertEquals(j, q.indexOf(j.asInstanceOf[java.lang.Integer], false))
    }; j = j + 1 } }
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j.asInstanceOf[java.lang.Integer])
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      assertEquals(j, q.indexOf(j.asInstanceOf[java.lang.Integer], false))
    }; j = j + 1 } }
  })
  test("iteratorTest")({
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } }
    var iter: balticporter.runtime.JavaIterator[java.lang.Integer] = q.iterator();
    { var j: scala.Int = 0; while (j <= 6) { {
      assertEquals(j, iter.next.intValue())
    }; j = j + 1 } }
    iter = q.iterator()
    iter.next
    iter.remove()
    this.assertValues(q, scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    iter.next
    iter.remove()
    this.assertValues(q, scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    iter.next
    iter.next
    iter.remove()
    this.assertValues(q, scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    iter.next
    iter.next
    iter.next
    iter.remove()
    this.assertValues(q, scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j.asInstanceOf[java.lang.Integer])
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } }
    iter = q.iterator();
    { var j: scala.Int = 0; while (j <= 6) { {
      assertEquals(j, iter.next.intValue())
    }; j = j + 1 } }
    iter = q.iterator()
    iter.next
    iter.remove()
    this.assertValues(q, scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    iter.next
    iter.remove()
    this.assertValues(q, scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    iter.next
    iter.next
    iter.remove()
    this.assertValues(q, scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer]))
    iter.next
    iter.next
    iter.next
    iter.remove()
    this.assertValues(q, scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
  })
  test("iteratorRemoveEdgeCaseTest")({
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]();
    { var i: scala.Int = 0; while (i < 100) { {
      queue.addLast(i.asInstanceOf[java.lang.Integer])
      if (i > 50) {
        queue.removeFirst()
      } else ()
    }; i = i + 1 } }
    val it: balticporter.runtime.JavaIterator[java.lang.Integer] = queue.iterator()
    while (it.hasNext) {
      it.next
      it.remove()
    }
    queue.addLast(1337.asInstanceOf[java.lang.Integer])
    var i: java.lang.Integer = queue.first()
    assertEquals(i.asInstanceOf[scala.Int].longValue(), 1337)
  })
  test("toStringTest")({
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](1)
    assert(q.toString().equals("[]"))
    q.addLast(4.asInstanceOf[java.lang.Integer])
    assert(q.toString().equals("[4]"))
    q.addLast(5.asInstanceOf[java.lang.Integer])
    q.addLast(6.asInstanceOf[java.lang.Integer])
    q.addLast(7.asInstanceOf[java.lang.Integer])
    assert(q.toString().equals("[4, 5, 6, 7]"))
  })
  test("hashEqualsTest")({
    val q1: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    val q2: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    this.assertEqualsAndHash(q1, q2)
    q1.addFirst(1.asInstanceOf[java.lang.Integer])
    assertNotEquals(q2, q1)
    q2.addFirst(1.asInstanceOf[java.lang.Integer])
    this.assertEqualsAndHash(q1, q2)
    q1.clear()
    q1.addLast(1.asInstanceOf[java.lang.Integer])
    q1.addLast(2.asInstanceOf[java.lang.Integer])
    q2.addLast(2.asInstanceOf[java.lang.Integer])
    this.assertEqualsAndHash(q1, q2);
    { var i: scala.Int = 0; while (i < 100) { {
      q1.addLast(i.asInstanceOf[java.lang.Integer])
      q1.addLast(i.asInstanceOf[java.lang.Integer])
      q1.removeFirst()
      assertNotEquals(q2, q1)
      q2.addLast(i.asInstanceOf[java.lang.Integer])
      q2.addLast(i.asInstanceOf[java.lang.Integer])
      q2.removeFirst()
      this.assertEqualsAndHash(q1, q2)
    }; i = i + 1 } }
  })
  private def assertEqualsAndHash(q1: com.badlogic.gdx.utils.Queue[?], q2: com.badlogic.gdx.utils.Queue[?]): scala.Unit = {
    assertEquals(q2, q1)
    assertEquals(q2.hashCode(), q1.hashCode(), "Hash codes are not equal")
  }
  private def assertValues(q: com.badlogic.gdx.utils.Queue[java.lang.Integer], values: scala.Array[java.lang.Integer]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      assertEquals(q.get(i), values(i))
    }; i = i + 1 } }
  }
}