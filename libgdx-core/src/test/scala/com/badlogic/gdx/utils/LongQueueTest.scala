package com.badlogic.gdx.utils

class LongQueueTest extends balticporter.runtime.PortedSuite {
  testCase("addFirstAndLastTest", {
    val queue: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    queue.addFirst(1)
    queue.addLast(2)
    queue.addFirst(3)
    queue.addLast(4)
    balticporter.runtime.Asserts.assertEquals(0, queue.indexOf(3))
    balticporter.runtime.Asserts.assertEquals(1, queue.indexOf(1))
    balticporter.runtime.Asserts.assertEquals(2, queue.indexOf(2))
    balticporter.runtime.Asserts.assertEquals(3, queue.indexOf(4))
  })
  testCase("removeLastTest", {
    val queue: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    queue.addLast(1)
    queue.addLast(2)
    queue.addLast(3)
    queue.addLast(4)
    balticporter.runtime.Asserts.assertEquals(4, queue.size)
    balticporter.runtime.Asserts.assertEquals(3, queue.indexOf(4))
    balticporter.runtime.Asserts.assertEquals(4, queue.removeLast())
    balticporter.runtime.Asserts.assertEquals(3, queue.size)
    balticporter.runtime.Asserts.assertEquals(2, queue.indexOf(3))
    balticporter.runtime.Asserts.assertEquals(3, queue.removeLast())
    balticporter.runtime.Asserts.assertEquals(2, queue.size)
    balticporter.runtime.Asserts.assertEquals(1, queue.indexOf(2))
    balticporter.runtime.Asserts.assertEquals(2, queue.removeLast())
    balticporter.runtime.Asserts.assertEquals(1, queue.size)
    balticporter.runtime.Asserts.assertEquals(0, queue.indexOf(1))
    balticporter.runtime.Asserts.assertEquals(1, queue.removeLast())
    balticporter.runtime.Asserts.assertEquals(0, queue.size)
  })
  testCase("removeFirstTest", {
    val queue: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    queue.addLast(1)
    queue.addLast(2)
    queue.addLast(3)
    queue.addLast(4)
    balticporter.runtime.Asserts.assertEquals(4, queue.size)
    balticporter.runtime.Asserts.assertEquals(0, queue.indexOf(1))
    balticporter.runtime.Asserts.assertEquals(1, queue.removeFirst())
    balticporter.runtime.Asserts.assertEquals(3, queue.size)
    balticporter.runtime.Asserts.assertEquals(0, queue.indexOf(2))
    balticporter.runtime.Asserts.assertEquals(2, queue.removeFirst())
    balticporter.runtime.Asserts.assertEquals(2, queue.size)
    balticporter.runtime.Asserts.assertEquals(0, queue.indexOf(3))
    balticporter.runtime.Asserts.assertEquals(3, queue.removeFirst())
    balticporter.runtime.Asserts.assertEquals(1, queue.size)
    balticporter.runtime.Asserts.assertEquals(0, queue.indexOf(4))
    balticporter.runtime.Asserts.assertEquals(4, queue.removeFirst())
    balticporter.runtime.Asserts.assertEquals(0, queue.size)
  })
  testCase("resizableQueueTest", {
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue(8)
    balticporter.runtime.Asserts.assertTrue("New queue is not empty!", q.size == 0);
    { var i: scala.Int = 0; while (i < 100) { {
      { var j: scala.Int = 0; while (j < i) { {
        try {
          q.addLast(j)
        } catch {
          case e: java.lang.IllegalStateException => {
            balticporter.runtime.Asserts.fail(((("Failed to add element " + j) + " (") + i) + ")")
          }
        }
        val peeked: scala.Long = q.last()
        balticporter.runtime.Asserts.assertTrue(((((("peekLast shows " + peeked) + ", should be ") + j) + " (") + i) + ")", peeked == j)
        val size: scala.Int = q.size
        balticporter.runtime.Asserts.assertTrue(((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")", size == (j + 1))
      }; j = j + 1 } }
      if (i != 0) {
        val peek: scala.Long = q.first()
        balticporter.runtime.Asserts.assertTrue(((("First thing is not zero but " + peek) + " (") + i) + ")", peek == 0)
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: scala.Long = q.removeFirst()
        balticporter.runtime.Asserts.assertTrue(((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")", pop == j)
        val size: scala.Int = q.size
        balticporter.runtime.Asserts.assertTrue(((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")", size == ((i - 1) - j))
      }; j = j + 1 } }
      balticporter.runtime.Asserts.assertTrue("Not empty after cycle " + i, q.size == 0)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addLast(42)
    }; i = i + 1 } }
    q.clear()
    balticporter.runtime.Asserts.assertTrue("Clear did not clear properly", q.size == 0)
  })
  testCase("resizableDequeTest", {
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue(8)
    balticporter.runtime.Asserts.assertTrue("New deque is not empty!", q.size == 0);
    { var i: scala.Int = 0; while (i < 100) { {
      { var j: scala.Int = 0; while (j < i) { {
        try {
          q.addFirst(j)
        } catch {
          case e: java.lang.IllegalStateException => {
            balticporter.runtime.Asserts.fail(((("Failed to add element " + j) + " (") + i) + ")")
          }
        }
        val peeked: scala.Long = q.first()
        balticporter.runtime.Asserts.assertTrue(((((("peek shows " + peeked) + ", should be ") + j) + " (") + i) + ")", peeked == j)
        val size: scala.Int = q.size
        balticporter.runtime.Asserts.assertTrue(((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")", size == (j + 1))
      }; j = j + 1 } }
      if (i != 0) {
        val peek: scala.Long = q.last()
        balticporter.runtime.Asserts.assertTrue(((("Last thing is not zero but " + peek) + " (") + i) + ")", peek == 0)
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: scala.Long = q.removeLast()
        balticporter.runtime.Asserts.assertTrue(((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")", pop == j)
        val size: scala.Int = q.size
        balticporter.runtime.Asserts.assertTrue(((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")", size == ((i - 1) - j))
      }; j = j + 1 } }
      balticporter.runtime.Asserts.assertTrue("Not empty after cycle " + i, q.size == 0)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addFirst(42)
    }; i = i + 1 } }
    q.clear()
    balticporter.runtime.Asserts.assertTrue("Clear did not clear properly", q.size == 0)
  })
  testCase("getTest", {
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue(7);
    { var i: scala.Int = 0; while (i < 5) { {
      { var j: scala.Int = 0; while (j < 4) { {
        q.addLast(j)
      }; j = j + 1 } }
      balticporter.runtime.Asserts.assertEquals(("get(0) is not equal to peek (" + i) + ")", q.get(0), q.first())
      balticporter.runtime.Asserts.assertEquals(("get(size-1) is not equal to peekLast (" + i) + ")", q.get(q.size - 1), q.last());
      { var j: scala.Int = 0; while (j < 4) { {
        balticporter.runtime.Asserts.assertTrue(q.get(j) == j)
      }; j = j + 1 } };
      { var j: scala.Int = 0; while (j < (4 - 1)) { {
        q.removeFirst()
        balticporter.runtime.Asserts.assertEquals(("get(0) is not equal to peek (" + i) + ")", q.get(0), q.first())
      }; j = j + 1 } }
      q.removeFirst()
      assert(q.size == 0)
      try {
        q.get(0)
        balticporter.runtime.Asserts.fail("get() on empty queue did not throw")
      } catch {
        case ignore: java.lang.IndexOutOfBoundsException => {
          ()
        }
      }
    }; i = i + 1 } }
  })
  testCase("removeTest", {
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
  testCase("indexOfTest", {
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j)
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      balticporter.runtime.Asserts.assertEquals(q.indexOf(j), j)
    }; j = j + 1 } }
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j)
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j)
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      balticporter.runtime.Asserts.assertEquals(q.indexOf(j), j)
    }; j = j + 1 } }
  })
  testCase("toStringTest", {
    val q: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue(1)
    balticporter.runtime.Asserts.assertTrue(q.toString().equals("[]"))
    q.addLast(4)
    balticporter.runtime.Asserts.assertTrue(q.toString().equals("[4]"))
    q.addLast(5)
    q.addLast(6)
    q.addLast(7)
    balticporter.runtime.Asserts.assertTrue(q.toString().equals("[4, 5, 6, 7]"))
  })
  testCase("hashEqualsTest", {
    val q1: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    val q2: com.badlogic.gdx.utils.LongQueue = new com.badlogic.gdx.utils.LongQueue()
    this.assertEqualsAndHash(q1, q2)
    q1.addFirst(1)
    balticporter.runtime.Asserts.assertNotEquals(q1, q2)
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
      balticporter.runtime.Asserts.assertNotEquals(q1, q2)
      q2.addLast(i)
      q2.addLast(i)
      q2.removeFirst()
      this.assertEqualsAndHash(q1, q2)
    }; i = i + 1 } }
  })
  private def assertEqualsAndHash(q1: com.badlogic.gdx.utils.LongQueue, q2: com.badlogic.gdx.utils.LongQueue): scala.Unit = {
    balticporter.runtime.Asserts.assertEquals(q1, q2)
    balticporter.runtime.Asserts.assertEquals("Hash codes are not equal", q1.hashCode(), q2.hashCode())
  }
  private def assertValues(q: com.badlogic.gdx.utils.LongQueue, values: scala.Array[scala.Long]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      balticporter.runtime.Asserts.assertEquals(values(i), q.get(i))
    }; i = i + 1 } }
  }
}