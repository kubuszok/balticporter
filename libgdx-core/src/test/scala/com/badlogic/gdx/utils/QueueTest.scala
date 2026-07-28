package com.badlogic.gdx.utils

class QueueTest extends balticporter.runtime.PortedSuite {
  testCase("addFirstAndLastTest", {
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    queue.addFirst(1.asInstanceOf[java.lang.Integer])
    queue.addLast(2.asInstanceOf[java.lang.Integer])
    queue.addFirst(3.asInstanceOf[java.lang.Integer])
    queue.addLast(4.asInstanceOf[java.lang.Integer])
    assertEquals(0, queue.indexOf(3.asInstanceOf[java.lang.Integer], true))
    assertEquals(1, queue.indexOf(1.asInstanceOf[java.lang.Integer], true))
    assertEquals(2, queue.indexOf(2.asInstanceOf[java.lang.Integer], true))
    assertEquals(3, queue.indexOf(4.asInstanceOf[java.lang.Integer], true))
  })
  testCase("removeLastTest", {
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    queue.addLast(1.asInstanceOf[java.lang.Integer])
    queue.addLast(2.asInstanceOf[java.lang.Integer])
    queue.addLast(3.asInstanceOf[java.lang.Integer])
    queue.addLast(4.asInstanceOf[java.lang.Integer])
    assertEquals(4, queue.size)
    assertEquals(3, queue.indexOf(4.asInstanceOf[java.lang.Integer], true))
    assertEquals(4.asInstanceOf[java.lang.Integer], queue.removeLast().asInstanceOf[java.lang.Object])
    assertEquals(3, queue.size)
    assertEquals(2, queue.indexOf(3.asInstanceOf[java.lang.Integer], true))
    assertEquals(3.asInstanceOf[java.lang.Integer], queue.removeLast().asInstanceOf[java.lang.Object])
    assertEquals(2, queue.size)
    assertEquals(1, queue.indexOf(2.asInstanceOf[java.lang.Integer], true))
    assertEquals(2.asInstanceOf[java.lang.Integer], queue.removeLast().asInstanceOf[java.lang.Object])
    assertEquals(1, queue.size)
    assertEquals(0, queue.indexOf(1.asInstanceOf[java.lang.Integer], true))
    assertEquals(1.asInstanceOf[java.lang.Integer], queue.removeLast().asInstanceOf[java.lang.Object])
    assertEquals(0, queue.size)
  })
  testCase("removeFirstTest", {
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    queue.addLast(1.asInstanceOf[java.lang.Integer])
    queue.addLast(2.asInstanceOf[java.lang.Integer])
    queue.addLast(3.asInstanceOf[java.lang.Integer])
    queue.addLast(4.asInstanceOf[java.lang.Integer])
    assertEquals(4, queue.size)
    assertEquals(0, queue.indexOf(1.asInstanceOf[java.lang.Integer], true))
    assertEquals(1.asInstanceOf[java.lang.Integer], queue.removeFirst().asInstanceOf[java.lang.Object])
    assertEquals(3, queue.size)
    assertEquals(0, queue.indexOf(2.asInstanceOf[java.lang.Integer], true))
    assertEquals(2.asInstanceOf[java.lang.Integer], queue.removeFirst().asInstanceOf[java.lang.Object])
    assertEquals(2, queue.size)
    assertEquals(0, queue.indexOf(3.asInstanceOf[java.lang.Integer], true))
    assertEquals(3.asInstanceOf[java.lang.Integer], queue.removeFirst().asInstanceOf[java.lang.Object])
    assertEquals(1, queue.size)
    assertEquals(0, queue.indexOf(4.asInstanceOf[java.lang.Integer], true))
    assertEquals(4.asInstanceOf[java.lang.Integer], queue.removeFirst().asInstanceOf[java.lang.Object])
    assertEquals(0, queue.size)
  })
  testCase("resizableQueueTest", {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](8)
    assertTrue("New queue is not empty!", q.size == 0);
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
        assertTrue(((((("peekLast shows " + peeked) + ", should be ") + j) + " (") + i) + ")", peeked.equals(j.asInstanceOf[java.lang.Integer]))
        val size: scala.Int = q.size
        assertTrue(((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")", size == (j + 1))
      }; j = j + 1 } }
      if (i != 0) {
        val peek: java.lang.Integer = q.first()
        assertTrue(((("First thing is not zero but " + peek) + " (") + i) + ")", peek == 0)
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: java.lang.Integer = q.removeFirst()
        assertTrue(((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")", pop == j)
        val size: scala.Int = q.size
        assertTrue(((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")", size == ((i - 1) - j))
      }; j = j + 1 } }
      assertTrue("Not empty after cycle " + i, q.size == 0)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addLast(42.asInstanceOf[java.lang.Integer])
    }; i = i + 1 } }
    q.clear()
    assertTrue("Clear did not clear properly", q.size == 0)
  })
  testCase("resizableDequeTest", {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](8)
    assertTrue("New deque is not empty!", q.size == 0);
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
        assertTrue(((((("peek shows " + peeked) + ", should be ") + j) + " (") + i) + ")", peeked.equals(j.asInstanceOf[java.lang.Integer]))
        val size: scala.Int = q.size
        assertTrue(((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")", size == (j + 1))
      }; j = j + 1 } }
      if (i != 0) {
        val peek: java.lang.Integer = q.last()
        assertTrue(((("Last thing is not zero but " + peek) + " (") + i) + ")", peek == 0)
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: java.lang.Integer = q.removeLast()
        assertTrue(((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")", pop == j)
        val size: scala.Int = q.size
        assertTrue(((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")", size == ((i - 1) - j))
      }; j = j + 1 } }
      assertTrue("Not empty after cycle " + i, q.size == 0)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addFirst(42.asInstanceOf[java.lang.Integer])
    }; i = i + 1 } }
    q.clear()
    assertTrue("Clear did not clear properly", q.size == 0)
  })
  testCase("getTest", {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](7);
    { var i: scala.Int = 0; while (i < 5) { {
      { var j: scala.Int = 0; while (j < 4) { {
        q.addLast(j.asInstanceOf[java.lang.Integer])
      }; j = j + 1 } }
      assertEquals(("get(0) is not equal to peek (" + i) + ")", q.get(0), q.first())
      assertEquals(("get(size-1) is not equal to peekLast (" + i) + ")", q.get(q.size - 1), q.last());
      { var j: scala.Int = 0; while (j < 4) { {
        assertTrue(q.get(j) == j)
      }; j = j + 1 } };
      { var j: scala.Int = 0; while (j < (4 - 1)) { {
        q.removeFirst()
        assertEquals(("get(0) is not equal to peek (" + i) + ")", q.get(0), q.first())
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
  testCase("removeTest", {
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
  testCase("indexOfTest", {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      assertEquals(q.indexOf(j.asInstanceOf[java.lang.Integer], false), j)
    }; j = j + 1 } }
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j.asInstanceOf[java.lang.Integer])
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      assertEquals(q.indexOf(j.asInstanceOf[java.lang.Integer], false), j)
    }; j = j + 1 } }
  })
  testCase("iteratorTest", {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } }
    var iter: balticporter.runtime.JavaIterator[java.lang.Integer] = q.iterator();
    { var j: scala.Int = 0; while (j <= 6) { {
      assertEquals(iter.next.intValue(), j)
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
      assertEquals(iter.next.intValue(), j)
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
  testCase("iteratorRemoveEdgeCaseTest", {
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
    assertEquals(1337, i.asInstanceOf[scala.Int].longValue())
  })
  testCase("toStringTest", {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](1)
    assertTrue(q.toString().equals("[]"))
    q.addLast(4.asInstanceOf[java.lang.Integer])
    assertTrue(q.toString().equals("[4]"))
    q.addLast(5.asInstanceOf[java.lang.Integer])
    q.addLast(6.asInstanceOf[java.lang.Integer])
    q.addLast(7.asInstanceOf[java.lang.Integer])
    assertTrue(q.toString().equals("[4, 5, 6, 7]"))
  })
  testCase("hashEqualsTest", {
    val q1: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    val q2: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    this.assertEqualsAndHash(q1, q2)
    q1.addFirst(1.asInstanceOf[java.lang.Integer])
    assertNotEquals(q1, q2)
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
      assertNotEquals(q1, q2)
      q2.addLast(i.asInstanceOf[java.lang.Integer])
      q2.addLast(i.asInstanceOf[java.lang.Integer])
      q2.removeFirst()
      this.assertEqualsAndHash(q1, q2)
    }; i = i + 1 } }
  })
  private def assertEqualsAndHash(q1: com.badlogic.gdx.utils.Queue[?], q2: com.badlogic.gdx.utils.Queue[?]): scala.Unit = {
    assertEquals(q1, q2)
    assertEquals("Hash codes are not equal", q1.hashCode(), q2.hashCode())
  }
  private def assertValues(q: com.badlogic.gdx.utils.Queue[java.lang.Integer], values: scala.Array[java.lang.Integer]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      assertEquals(values(i), q.get(i))
    }; i = i + 1 } }
  }
}