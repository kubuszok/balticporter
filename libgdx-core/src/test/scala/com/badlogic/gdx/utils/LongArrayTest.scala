package com.badlogic.gdx.utils

class LongArrayTest extends balticporter.runtime.PortedSuite {
  testCase("addTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(3)
    longArray1.add(3)
    assertArrayEquals(scala.Array[scala.Long](3), longArray1.toArray())
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray2.add(1, 2)
    assertArrayEquals(scala.Array[scala.Long](1, 2), longArray2.toArray())
    val longArray3: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray3.addAll(longArray2)
    assertArrayEquals(longArray2.toArray(), longArray3.toArray())
    longArray3.addAll(longArray1)
    assertArrayEquals(scala.Array[scala.Long](1, 2, 3), longArray3.toArray())
    longArray3.addAll(scala.Array[scala.Long](4, 5, 6, 2, 8, 10, 1, 6, 2, 3, 30, 31, 25, 20))
    assertEquals(17, longArray3.size)
    assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 6, 2, 8, 10, 1, 6, 2, 3, 30, 31, 25, 20), longArray3.toArray())
    val longArray4: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray4.addAll(scala.Array[scala.Long](4, 5, 6, 2, 21, 45, 78), 3, 3)
    assertArrayEquals(scala.Array[scala.Long](2, 21, 45), longArray4.toArray())
  })
  testCase("getTest", {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray.add(3, 4, 5, 1)
    assertEquals(3, longArray.get(0))
    try {
      longArray.get(9)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  testCase("setTest", {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](3, 4, 5, 7))
    longArray.set(1, 51)
    assertEquals(51, longArray.get(1))
    try {
      longArray.set(5, 8)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  testCase("incrTest", {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](3, 4, 5, 1, 56, 32))
    longArray.incr(3, 45)
    assertEquals(46, longArray.get(3))
    longArray.incr(3)
    assertArrayEquals(scala.Array[scala.Long](6, 7, 8, 49, 59, 35), longArray.toArray())
    try {
      longArray.incr(28, 4)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  testCase("mulTest", {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](3, 4, 5, 1, 56, 32))
    longArray.mul(1, 3)
    assertEquals(12, longArray.get(1))
    longArray.mul(2)
    assertArrayEquals(scala.Array[scala.Long](6, 24, 10, 2, 112, 64), longArray.toArray())
    try {
      longArray.mul(17, 8)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  testCase("insertTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray1.addAll(scala.Array[scala.Long](1, 3, 4, 5, 6))
    longArray1.insert(1, 2)
    assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 6), longArray1.toArray())
    longArray1.insertRange(2, 3)
    assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 3, 4, 5, 6), longArray1.toArray())
    try {
      longArray1.insertRange(400, 4)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(false, 16)
    longArray2.addAll(scala.Array[scala.Long](1, 3, 4, 5, 6))
    longArray2.insert(1, 2)
    assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 5, 6, 3), longArray2.toArray())
    try {
      longArray2.insert(2783, 3)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  testCase("swapTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 3, 4, 5, 6))
    longArray1.swap(1, 4)
    assertArrayEquals(scala.Array[scala.Long](1, 6, 4, 5, 3), longArray1.toArray())
    try {
      longArray1.swap(100, 3)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    try {
      longArray1.swap(3, 100)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  testCase("containsTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 3, 4, 5, 6))
    assertTrue(longArray1.contains(3))
    assertFalse(longArray1.contains(100))
  })
  testCase("indexOfTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 3, 4, 5, 6, 6, 3, 9, 68000, 68000))
    assertEquals(-1, longArray1.indexOf(100))
    assertEquals(1, longArray1.indexOf(3))
    assertEquals(9, longArray1.lastIndexOf(68000))
    assertEquals(-1, longArray1.lastIndexOf(100))
  })
  testCase("removeTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = com.badlogic.gdx.utils.LongArray.`with`(scala.Array[scala.Long](1, 3, 4, 5, 6, 6, 3, 9))
    assertTrue(longArray1.removeValue(3))
    assertArrayEquals(scala.Array[scala.Long](1, 4, 5, 6, 6, 3, 9), longArray1.toArray())
    assertEquals(7, longArray1.size)
    assertFalse(longArray1.removeValue(99))
    assertEquals(4, longArray1.removeIndex(1))
    assertArrayEquals(scala.Array[scala.Long](1, 5, 6, 6, 3, 9), longArray1.toArray())
    assertEquals(6, longArray1.size)
    try {
      longArray1.removeIndex(56)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray2.addAll(scala.Array[scala.Long](1, 10, 25, 2, 23, 345))
    longArray2.removeRange(2, 5)
    assertArrayEquals(scala.Array[scala.Long](1, 10), longArray2.toArray())
    try {
      longArray2.removeRange(3, 4)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    try {
      longArray2.removeRange(1, 0)
      fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    val longArray3: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray3.addAll(scala.Array[scala.Long](1, 10, 25, 35, 50, 40))
    var toBeRemoved: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 25, 35))
    assertTrue(longArray3.removeAll(toBeRemoved))
    assertArrayEquals(scala.Array[scala.Long](10, 50, 40), longArray3.toArray())
    assertFalse(longArray3.removeAll(toBeRemoved))
    toBeRemoved = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](10, 30, 22))
    assertTrue(longArray3.removeAll(toBeRemoved))
    assertArrayEquals(scala.Array[scala.Long](50, 40), longArray3.toArray())
  })
  testCase("popPeekFirstTest", {
    val longArray: com.badlogic.gdx.utils.LongArray = com.badlogic.gdx.utils.LongArray.`with`(scala.Array[scala.Long](1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
    val emptyLongArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    assertEquals(1, longArray.first())
    assertEquals(10, longArray.peek())
    assertEquals(10, longArray.pop())
    assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 6, 7, 8, 9), longArray.toArray())
    try {
      val first: scala.Long = emptyLongArray.first()
      fail()
    } catch {
      case e: java.lang.IllegalStateException => {
        ()
      }
    }
    try {
      val last: scala.Long = emptyLongArray.pop()
      fail()
    } catch {
      case e: java.lang.IllegalStateException => {
        ()
      }
    }
    try {
      val last: scala.Long = emptyLongArray.peek()
      fail()
    } catch {
      case e: java.lang.IllegalStateException => {
        ()
      }
    }
  })
  testCase("emptyTest", {
    assertTrue(new com.badlogic.gdx.utils.LongArray().isEmpty())
    assertFalse(new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1)).isEmpty())
    assertFalse(new com.badlogic.gdx.utils.LongArray().notEmpty())
    assertTrue(new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1)).notEmpty())
  })
  testCase("clearTest", {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1))
    longArray.clear()
    assertTrue(longArray.isEmpty())
  })
  testCase("shrinkTest", {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray.add(1, 2, 3)
    assertArrayEquals(scala.Array[scala.Long](1, 2, 3), longArray.shrink())
    assertEquals(3, longArray.items.length)
  })
  testCase("ensureCapacityTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 0, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 3))
    assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 0, 0, 0, 0, 0), longArray2.ensureCapacity(2))
    assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 0, 10, 389, 8, 392, 4, 27346, 2, 234, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), longArray1.ensureCapacity(18))
    try {
      longArray1.ensureCapacity(-6)
      fail()
    } catch {
      case e: java.lang.IllegalArgumentException => {
        ()
      }
    }
  })
  testCase("setSizeTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    longArray1.setSize(23)
    assertEquals(23, longArray1.size)
    longArray1.setSize(10)
    assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1), longArray1.toArray())
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 3))
    assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 0, 0, 0, 0, 0), longArray2.setSize(5))
    try {
      longArray1.setSize(-3)
      fail()
    } catch {
      case e: java.lang.IllegalArgumentException => {
        ()
      }
    }
  })
  testCase("resizeTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12, 0, 0), longArray1.resize(23))
  })
  testCase("sortAndReverseTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = com.badlogic.gdx.utils.LongArray.`with`(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    longArray1.sort()
    assertArrayEquals(scala.Array[scala.Long](1, 1, 2, 2, 2, 4, 4, 6, 8, 10, 12, 32, 53, 53, 89, 90, 234, 389, 392, 564, 27346), longArray1.toArray())
    longArray1.reverse()
    assertArrayEquals(scala.Array[scala.Long](27346, 564, 392, 389, 234, 90, 89, 53, 53, 32, 12, 10, 8, 6, 4, 4, 2, 2, 2, 1, 1), longArray1.toArray())
  })
  testCase("equalsTest", {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray1.add(1, 2)
    longArray2.add(1, 2)
    assertTrue(longArray1.equals(longArray2))
    val o: com.badlogic.gdx.utils.ArrayMap[java.lang.Integer, java.lang.Integer] = new com.badlogic.gdx.utils.ArrayMap[java.lang.Integer, java.lang.Integer]()
    assertFalse(longArray1.equals(o))
    val longArray3: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(false, 16)
    longArray3.add(1, 2)
    assertFalse(longArray1.equals(longArray3))
    val longArray4: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(true, 12)
    longArray4.add(1, 2)
    assertTrue(longArray1.equals(longArray4))
    longArray1.add(3)
    assertFalse(longArray1.equals(longArray2))
  })
}