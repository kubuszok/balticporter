package com.badlogic.gdx.utils

class QueueTest {
  def addFirstAndLastTest(): scala.Unit = {
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    queue.addFirst(1.asInstanceOf[java.lang.Integer])
    queue.addLast(2.asInstanceOf[java.lang.Integer])
    queue.addFirst(3.asInstanceOf[java.lang.Integer])
    queue.addLast(4.asInstanceOf[java.lang.Integer])
    org.junit.Assert.assertEquals(0, queue.indexOf(3.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(1, queue.indexOf(1.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(2, queue.indexOf(2.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(3, queue.indexOf(4.asInstanceOf[java.lang.Integer], true))
  }
  def removeLastTest(): scala.Unit = {
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    queue.addLast(1.asInstanceOf[java.lang.Integer])
    queue.addLast(2.asInstanceOf[java.lang.Integer])
    queue.addLast(3.asInstanceOf[java.lang.Integer])
    queue.addLast(4.asInstanceOf[java.lang.Integer])
    org.junit.Assert.assertEquals(4, queue.size)
    org.junit.Assert.assertEquals(3, queue.indexOf(4.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(4.asInstanceOf[java.lang.Integer], queue.removeLast().asInstanceOf[java.lang.Object])
    org.junit.Assert.assertEquals(3, queue.size)
    org.junit.Assert.assertEquals(2, queue.indexOf(3.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(3.asInstanceOf[java.lang.Integer], queue.removeLast().asInstanceOf[java.lang.Object])
    org.junit.Assert.assertEquals(2, queue.size)
    org.junit.Assert.assertEquals(1, queue.indexOf(2.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(2.asInstanceOf[java.lang.Integer], queue.removeLast().asInstanceOf[java.lang.Object])
    org.junit.Assert.assertEquals(1, queue.size)
    org.junit.Assert.assertEquals(0, queue.indexOf(1.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(1.asInstanceOf[java.lang.Integer], queue.removeLast().asInstanceOf[java.lang.Object])
    org.junit.Assert.assertEquals(0, queue.size)
  }
  def removeFirstTest(): scala.Unit = {
    val queue: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    queue.addLast(1.asInstanceOf[java.lang.Integer])
    queue.addLast(2.asInstanceOf[java.lang.Integer])
    queue.addLast(3.asInstanceOf[java.lang.Integer])
    queue.addLast(4.asInstanceOf[java.lang.Integer])
    org.junit.Assert.assertEquals(4, queue.size)
    org.junit.Assert.assertEquals(0, queue.indexOf(1.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(1.asInstanceOf[java.lang.Integer], queue.removeFirst().asInstanceOf[java.lang.Object])
    org.junit.Assert.assertEquals(3, queue.size)
    org.junit.Assert.assertEquals(0, queue.indexOf(2.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(2.asInstanceOf[java.lang.Integer], queue.removeFirst().asInstanceOf[java.lang.Object])
    org.junit.Assert.assertEquals(2, queue.size)
    org.junit.Assert.assertEquals(0, queue.indexOf(3.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(3.asInstanceOf[java.lang.Integer], queue.removeFirst().asInstanceOf[java.lang.Object])
    org.junit.Assert.assertEquals(1, queue.size)
    org.junit.Assert.assertEquals(0, queue.indexOf(4.asInstanceOf[java.lang.Integer], true))
    org.junit.Assert.assertEquals(4.asInstanceOf[java.lang.Integer], queue.removeFirst().asInstanceOf[java.lang.Object])
    org.junit.Assert.assertEquals(0, queue.size)
  }
  def resizableQueueTest(): scala.Unit = {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](8)
    org.junit.Assert.assertTrue("New queue is not empty!", q.size == 0);
    { var i: scala.Int = 0; while (i < 100) { {
      { var j: scala.Int = 0; while (j < i) { {
        try {
          q.addLast(j.asInstanceOf[java.lang.Integer])
        } catch {
          case e: java.lang.IllegalStateException => {
            org.junit.Assert.fail(((("Failed to add element " + j) + " (") + i) + ")")
          }
        }
        val peeked: java.lang.Integer = q.last()
        org.junit.Assert.assertTrue(((((("peekLast shows " + peeked) + ", should be ") + j) + " (") + i) + ")", peeked.equals(j.asInstanceOf[java.lang.Integer]))
        val size: scala.Int = q.size
        org.junit.Assert.assertTrue(((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")", size == (j + 1))
      }; j = j + 1 } }
      if (i != 0) {
        val peek: java.lang.Integer = q.first()
        org.junit.Assert.assertTrue(((("First thing is not zero but " + peek) + " (") + i) + ")", peek == 0)
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: java.lang.Integer = q.removeFirst()
        org.junit.Assert.assertTrue(((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")", pop == j)
        val size: scala.Int = q.size
        org.junit.Assert.assertTrue(((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")", size == ((i - 1) - j))
      }; j = j + 1 } }
      org.junit.Assert.assertTrue("Not empty after cycle " + i, q.size == 0)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addLast(42.asInstanceOf[java.lang.Integer])
    }; i = i + 1 } }
    q.clear()
    org.junit.Assert.assertTrue("Clear did not clear properly", q.size == 0)
  }
  def resizableDequeTest(): scala.Unit = {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](8)
    org.junit.Assert.assertTrue("New deque is not empty!", q.size == 0);
    { var i: scala.Int = 0; while (i < 100) { {
      { var j: scala.Int = 0; while (j < i) { {
        try {
          q.addFirst(j.asInstanceOf[java.lang.Integer])
        } catch {
          case e: java.lang.IllegalStateException => {
            org.junit.Assert.fail(((("Failed to add element " + j) + " (") + i) + ")")
          }
        }
        val peeked: java.lang.Integer = q.first()
        org.junit.Assert.assertTrue(((((("peek shows " + peeked) + ", should be ") + j) + " (") + i) + ")", peeked.equals(j.asInstanceOf[java.lang.Integer]))
        val size: scala.Int = q.size
        org.junit.Assert.assertTrue(((((("Size should be " + (j + 1)) + " but is ") + size) + " (") + i) + ")", size == (j + 1))
      }; j = j + 1 } }
      if (i != 0) {
        val peek: java.lang.Integer = q.last()
        org.junit.Assert.assertTrue(((("Last thing is not zero but " + peek) + " (") + i) + ")", peek == 0)
      } else ();
      { var j: scala.Int = 0; while (j < i) { {
        val pop: java.lang.Integer = q.removeLast()
        org.junit.Assert.assertTrue(((((("Popped should be " + j) + " but is ") + pop) + " (") + i) + ")", pop == j)
        val size: scala.Int = q.size
        org.junit.Assert.assertTrue(((((("Size should be " + ((i - 1) - j)) + " but is ") + size) + " (") + i) + ")", size == ((i - 1) - j))
      }; j = j + 1 } }
      org.junit.Assert.assertTrue("Not empty after cycle " + i, q.size == 0)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < 56) { {
      q.addFirst(42.asInstanceOf[java.lang.Integer])
    }; i = i + 1 } }
    q.clear()
    org.junit.Assert.assertTrue("Clear did not clear properly", q.size == 0)
  }
  def getTest(): scala.Unit = {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](7);
    { var i: scala.Int = 0; while (i < 5) { {
      { var j: scala.Int = 0; while (j < 4) { {
        q.addLast(j.asInstanceOf[java.lang.Integer])
      }; j = j + 1 } }
      org.junit.Assert.assertEquals(("get(0) is not equal to peek (" + i) + ")", q.get(0), q.first())
      org.junit.Assert.assertEquals(("get(size-1) is not equal to peekLast (" + i) + ")", q.get(q.size - 1), q.last());
      { var j: scala.Int = 0; while (j < 4) { {
        org.junit.Assert.assertTrue(q.get(j) == j)
      }; j = j + 1 } };
      { var j: scala.Int = 0; while (j < (4 - 1)) { {
        q.removeFirst()
        org.junit.Assert.assertEquals(("get(0) is not equal to peek (" + i) + ")", q.get(0), q.first())
      }; j = j + 1 } }
      q.removeFirst()
      assert(q.size == 0)
      try {
        q.get(0)
        org.junit.Assert.fail("get() on empty queue did not throw")
      } catch {
        case ignore: java.lang.IndexOutOfBoundsException => {
          ()
        }
      }
    }; i = i + 1 } }
  }
  def removeTest(): scala.Unit = {
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
  }
  def indexOfTest(): scala.Unit = {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      org.junit.Assert.assertEquals(q.indexOf(j.asInstanceOf[java.lang.Integer], false), j)
    }; j = j + 1 } }
    q.clear();
    { var j: scala.Int = 2; while (j >= 0) { {
      q.addFirst(j.asInstanceOf[java.lang.Integer])
    }; j = j - 1 } };
    { var j: scala.Int = 3; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } };
    { var j: scala.Int = 0; while (j <= 6) { {
      org.junit.Assert.assertEquals(q.indexOf(j.asInstanceOf[java.lang.Integer], false), j)
    }; j = j + 1 } }
  }
  def iteratorTest(): scala.Unit = {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]();
    { var j: scala.Int = 0; while (j <= 6) { {
      q.addLast(j.asInstanceOf[java.lang.Integer])
    }; j = j + 1 } }
    var iter: balticporter.runtime.JavaIterator[java.lang.Integer] = q.iterator();
    { var j: scala.Int = 0; while (j <= 6) { {
      org.junit.Assert.assertEquals(iter.next.intValue(), j)
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
      org.junit.Assert.assertEquals(iter.next.intValue(), j)
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
  }
  def iteratorRemoveEdgeCaseTest(): scala.Unit = {
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
    org.junit.Assert.assertEquals(1337, i.asInstanceOf[scala.Int].longValue())
  }
  def toStringTest(): scala.Unit = {
    val q: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer](1)
    org.junit.Assert.assertTrue(q.toString().equals("[]"))
    q.addLast(4.asInstanceOf[java.lang.Integer])
    org.junit.Assert.assertTrue(q.toString().equals("[4]"))
    q.addLast(5.asInstanceOf[java.lang.Integer])
    q.addLast(6.asInstanceOf[java.lang.Integer])
    q.addLast(7.asInstanceOf[java.lang.Integer])
    org.junit.Assert.assertTrue(q.toString().equals("[4, 5, 6, 7]"))
  }
  def hashEqualsTest(): scala.Unit = {
    val q1: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    val q2: com.badlogic.gdx.utils.Queue[java.lang.Integer] = new com.badlogic.gdx.utils.Queue[java.lang.Integer]()
    this.assertEqualsAndHash(q1, q2)
    q1.addFirst(1.asInstanceOf[java.lang.Integer])
    org.junit.Assert.assertNotEquals(q1, q2)
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
      org.junit.Assert.assertNotEquals(q1, q2)
      q2.addLast(i.asInstanceOf[java.lang.Integer])
      q2.addLast(i.asInstanceOf[java.lang.Integer])
      q2.removeFirst()
      this.assertEqualsAndHash(q1, q2)
    }; i = i + 1 } }
  }
  private def assertEqualsAndHash(q1: com.badlogic.gdx.utils.Queue[?], q2: com.badlogic.gdx.utils.Queue[?]): scala.Unit = {
    org.junit.Assert.assertEquals(q1, q2)
    org.junit.Assert.assertEquals("Hash codes are not equal", q1.hashCode(), q2.hashCode())
  }
  private def assertValues(q: com.badlogic.gdx.utils.Queue[java.lang.Integer], values: scala.Array[java.lang.Integer]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = values.length; while (i < n) { {
      org.junit.Assert.assertEquals(values(i), q.get(i))
    }; i = i + 1 } }
  }
}