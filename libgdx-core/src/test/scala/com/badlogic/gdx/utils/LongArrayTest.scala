package com.badlogic.gdx.utils

class LongArrayTest {
  @org.junit.Test
  def addTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(3)
    longArray1.add(3)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](3), longArray1.toArray())
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray2.add(1, 2)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2), longArray2.toArray())
    val longArray3: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray3.addAll(longArray2)
    org.junit.Assert.assertArrayEquals(longArray2.toArray(), longArray3.toArray())
    longArray3.addAll(longArray1)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 3), longArray3.toArray())
    longArray3.addAll(scala.Array[scala.Long](4, 5, 6, 2, 8, 10, 1, 6, 2, 3, 30, 31, 25, 20))
    org.junit.Assert.assertEquals(17, longArray3.size)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 6, 2, 8, 10, 1, 6, 2, 3, 30, 31, 25, 20), longArray3.toArray())
    val longArray4: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray4.addAll(scala.Array[scala.Long](4, 5, 6, 2, 21, 45, 78), 3, 3)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](2, 21, 45), longArray4.toArray())
  }
  @org.junit.Test
  def getTest(): scala.Unit = {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray.add(3, 4, 5, 1)
    org.junit.Assert.assertEquals(3, longArray.get(0))
    try {
      longArray.get(9)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  }
  @org.junit.Test
  def setTest(): scala.Unit = {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](3, 4, 5, 7))
    longArray.set(1, 51)
    org.junit.Assert.assertEquals(51, longArray.get(1))
    try {
      longArray.set(5, 8)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  }
  @org.junit.Test
  def incrTest(): scala.Unit = {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](3, 4, 5, 1, 56, 32))
    longArray.incr(3, 45)
    org.junit.Assert.assertEquals(46, longArray.get(3))
    longArray.incr(3)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](6, 7, 8, 49, 59, 35), longArray.toArray())
    try {
      longArray.incr(28, 4)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  }
  @org.junit.Test
  def mulTest(): scala.Unit = {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](3, 4, 5, 1, 56, 32))
    longArray.mul(1, 3)
    org.junit.Assert.assertEquals(12, longArray.get(1))
    longArray.mul(2)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](6, 24, 10, 2, 112, 64), longArray.toArray())
    try {
      longArray.mul(17, 8)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  }
  @org.junit.Test
  def insertTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray1.addAll(scala.Array[scala.Long](1, 3, 4, 5, 6))
    longArray1.insert(1, 2)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 6), longArray1.toArray())
    longArray1.insertRange(2, 3)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 3, 4, 5, 6), longArray1.toArray())
    try {
      longArray1.insertRange(400, 4)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(false, 16)
    longArray2.addAll(scala.Array[scala.Long](1, 3, 4, 5, 6))
    longArray2.insert(1, 2)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 5, 6, 3), longArray2.toArray())
    try {
      longArray2.insert(2783, 3)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  }
  @org.junit.Test
  def swapTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 3, 4, 5, 6))
    longArray1.swap(1, 4)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 6, 4, 5, 3), longArray1.toArray())
    try {
      longArray1.swap(100, 3)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    try {
      longArray1.swap(3, 100)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  }
  @org.junit.Test
  def containsTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 3, 4, 5, 6))
    org.junit.Assert.assertTrue(longArray1.contains(3))
    org.junit.Assert.assertFalse(longArray1.contains(100))
  }
  @org.junit.Test
  def indexOfTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 3, 4, 5, 6, 6, 3, 9, 68000, 68000))
    org.junit.Assert.assertEquals(-1, longArray1.indexOf(100))
    org.junit.Assert.assertEquals(1, longArray1.indexOf(3))
    org.junit.Assert.assertEquals(9, longArray1.lastIndexOf(68000))
    org.junit.Assert.assertEquals(-1, longArray1.lastIndexOf(100))
  }
  @org.junit.Test
  def removeTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = com.badlogic.gdx.utils.LongArray.`with`(scala.Array[scala.Long](1, 3, 4, 5, 6, 6, 3, 9))
    org.junit.Assert.assertTrue(longArray1.removeValue(3))
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 4, 5, 6, 6, 3, 9), longArray1.toArray())
    org.junit.Assert.assertEquals(7, longArray1.size)
    org.junit.Assert.assertFalse(longArray1.removeValue(99))
    org.junit.Assert.assertEquals(4, longArray1.removeIndex(1))
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 5, 6, 6, 3, 9), longArray1.toArray())
    org.junit.Assert.assertEquals(6, longArray1.size)
    try {
      longArray1.removeIndex(56)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray2.addAll(scala.Array[scala.Long](1, 10, 25, 2, 23, 345))
    longArray2.removeRange(2, 5)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 10), longArray2.toArray())
    try {
      longArray2.removeRange(3, 4)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    try {
      longArray2.removeRange(1, 0)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    val longArray3: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray3.addAll(scala.Array[scala.Long](1, 10, 25, 35, 50, 40))
    var toBeRemoved: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 25, 35))
    org.junit.Assert.assertTrue(longArray3.removeAll(toBeRemoved))
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](10, 50, 40), longArray3.toArray())
    org.junit.Assert.assertFalse(longArray3.removeAll(toBeRemoved))
    toBeRemoved = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](10, 30, 22))
    org.junit.Assert.assertTrue(longArray3.removeAll(toBeRemoved))
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](50, 40), longArray3.toArray())
  }
  @org.junit.Test
  def popPeekFirstTest(): scala.Unit = {
    val longArray: com.badlogic.gdx.utils.LongArray = com.badlogic.gdx.utils.LongArray.`with`(scala.Array[scala.Long](1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
    val emptyLongArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    org.junit.Assert.assertEquals(1, longArray.first())
    org.junit.Assert.assertEquals(10, longArray.peek())
    org.junit.Assert.assertEquals(10, longArray.pop())
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 6, 7, 8, 9), longArray.toArray())
    try {
      val first: scala.Long = emptyLongArray.first()
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IllegalStateException => {
        ()
      }
    }
    try {
      val last: scala.Long = emptyLongArray.pop()
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IllegalStateException => {
        ()
      }
    }
    try {
      val last: scala.Long = emptyLongArray.peek()
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IllegalStateException => {
        ()
      }
    }
  }
  @org.junit.Test
  def emptyTest(): scala.Unit = {
    org.junit.Assert.assertTrue(new com.badlogic.gdx.utils.LongArray().isEmpty())
    org.junit.Assert.assertFalse(new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1)).isEmpty())
    org.junit.Assert.assertFalse(new com.badlogic.gdx.utils.LongArray().notEmpty())
    org.junit.Assert.assertTrue(new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1)).notEmpty())
  }
  @org.junit.Test
  def clearTest(): scala.Unit = {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1))
    longArray.clear()
    org.junit.Assert.assertTrue(longArray.isEmpty())
  }
  @org.junit.Test
  def shrinkTest(): scala.Unit = {
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray.add(1, 2, 3)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 3), longArray.shrink())
    org.junit.Assert.assertEquals(3, longArray.items.length)
  }
  @org.junit.Test
  def ensureCapacityTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 0, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 3))
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 0, 0, 0, 0, 0), longArray2.ensureCapacity(2))
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 0, 10, 389, 8, 392, 4, 27346, 2, 234, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), longArray1.ensureCapacity(18))
    try {
      longArray1.ensureCapacity(-6)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IllegalArgumentException => {
        ()
      }
    }
  }
  @org.junit.Test
  def setSizeTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    longArray1.setSize(23)
    org.junit.Assert.assertEquals(23, longArray1.size)
    longArray1.setSize(10)
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1), longArray1.toArray())
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 3))
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 0, 0, 0, 0, 0), longArray2.setSize(5))
    try {
      longArray1.setSize(-3)
      org.junit.Assert.fail()
    } catch {
      case e: java.lang.IllegalArgumentException => {
        ()
      }
    }
  }
  @org.junit.Test
  def resizeTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12, 0, 0), longArray1.resize(23))
  }
  @org.junit.Test
  def sortAndReverseTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = com.badlogic.gdx.utils.LongArray.`with`(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    longArray1.sort()
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](1, 1, 2, 2, 2, 4, 4, 6, 8, 10, 12, 32, 53, 53, 89, 90, 234, 389, 392, 564, 27346), longArray1.toArray())
    longArray1.reverse()
    org.junit.Assert.assertArrayEquals(scala.Array[scala.Long](27346, 564, 392, 389, 234, 90, 89, 53, 53, 32, 12, 10, 8, 6, 4, 4, 2, 2, 2, 1, 1), longArray1.toArray())
  }
  @org.junit.Test
  def equalsTest(): scala.Unit = {
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray1.add(1, 2)
    longArray2.add(1, 2)
    org.junit.Assert.assertTrue(longArray1.equals(longArray2))
    val o: com.badlogic.gdx.utils.ArrayMap[java.lang.Integer, java.lang.Integer] = new com.badlogic.gdx.utils.ArrayMap[java.lang.Integer, java.lang.Integer]()
    org.junit.Assert.assertFalse(longArray1.equals(o))
    val longArray3: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(false, 16)
    longArray3.add(1, 2)
    org.junit.Assert.assertFalse(longArray1.equals(longArray3))
    val longArray4: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(true, 12)
    longArray4.add(1, 2)
    org.junit.Assert.assertTrue(longArray1.equals(longArray4))
    longArray1.add(3)
    org.junit.Assert.assertFalse(longArray1.equals(longArray2))
  }
}