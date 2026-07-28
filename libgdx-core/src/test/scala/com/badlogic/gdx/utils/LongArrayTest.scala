package com.badlogic.gdx.utils

class LongArrayTest extends munit.FunSuite {
  test("addTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(3)
    longArray1.add(3)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](3), longArray1.toArray())
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray2.add(1, 2)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2), longArray2.toArray())
    val longArray3: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray3.addAll(longArray2)
    balticporter.runtime.Asserts.assertArrayEquals(longArray2.toArray(), longArray3.toArray())
    longArray3.addAll(longArray1)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 3), longArray3.toArray())
    longArray3.addAll(scala.Array[scala.Long](4, 5, 6, 2, 8, 10, 1, 6, 2, 3, 30, 31, 25, 20))
    assertEquals(longArray3.size, 17)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 6, 2, 8, 10, 1, 6, 2, 3, 30, 31, 25, 20), longArray3.toArray())
    val longArray4: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray4.addAll(scala.Array[scala.Long](4, 5, 6, 2, 21, 45, 78), 3, 3)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](2, 21, 45), longArray4.toArray())
  })
  test("getTest")({
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray.add(3, 4, 5, 1)
    assertEquals(longArray.get(0), 3)
    try {
      longArray.get(9)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  test("setTest")({
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](3, 4, 5, 7))
    longArray.set(1, 51)
    assertEquals(longArray.get(1), 51)
    try {
      longArray.set(5, 8)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  test("incrTest")({
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](3, 4, 5, 1, 56, 32))
    longArray.incr(3, 45)
    assertEquals(longArray.get(3), 46)
    longArray.incr(3)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](6, 7, 8, 49, 59, 35), longArray.toArray())
    try {
      longArray.incr(28, 4)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  test("mulTest")({
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](3, 4, 5, 1, 56, 32))
    longArray.mul(1, 3)
    assertEquals(longArray.get(1), 12)
    longArray.mul(2)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](6, 24, 10, 2, 112, 64), longArray.toArray())
    try {
      longArray.mul(17, 8)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  test("insertTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray1.addAll(scala.Array[scala.Long](1, 3, 4, 5, 6))
    longArray1.insert(1, 2)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 6), longArray1.toArray())
    longArray1.insertRange(2, 3)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 3, 4, 5, 6), longArray1.toArray())
    try {
      longArray1.insertRange(400, 4)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(false, 16)
    longArray2.addAll(scala.Array[scala.Long](1, 3, 4, 5, 6))
    longArray2.insert(1, 2)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 5, 6, 3), longArray2.toArray())
    try {
      longArray2.insert(2783, 3)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  test("swapTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 3, 4, 5, 6))
    longArray1.swap(1, 4)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 6, 4, 5, 3), longArray1.toArray())
    try {
      longArray1.swap(100, 3)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    try {
      longArray1.swap(3, 100)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
  })
  test("containsTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 3, 4, 5, 6))
    assert(longArray1.contains(3))
    assertEquals(longArray1.contains(100), false)
  })
  test("indexOfTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 3, 4, 5, 6, 6, 3, 9, 68000, 68000))
    assertEquals(longArray1.indexOf(100), -1)
    assertEquals(longArray1.indexOf(3), 1)
    assertEquals(longArray1.lastIndexOf(68000), 9)
    assertEquals(longArray1.lastIndexOf(100), -1)
  })
  test("removeTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = com.badlogic.gdx.utils.LongArray.`with`(scala.Array[scala.Long](1, 3, 4, 5, 6, 6, 3, 9))
    assert(longArray1.removeValue(3))
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 4, 5, 6, 6, 3, 9), longArray1.toArray())
    assertEquals(longArray1.size, 7)
    assertEquals(longArray1.removeValue(99), false)
    assertEquals(longArray1.removeIndex(1), 4)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 5, 6, 6, 3, 9), longArray1.toArray())
    assertEquals(longArray1.size, 6)
    try {
      longArray1.removeIndex(56)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray2.addAll(scala.Array[scala.Long](1, 10, 25, 2, 23, 345))
    longArray2.removeRange(2, 5)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 10), longArray2.toArray())
    try {
      longArray2.removeRange(3, 4)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    try {
      longArray2.removeRange(1, 0)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IndexOutOfBoundsException => {
        ()
      }
    }
    val longArray3: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray3.addAll(scala.Array[scala.Long](1, 10, 25, 35, 50, 40))
    var toBeRemoved: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 25, 35))
    assert(longArray3.removeAll(toBeRemoved))
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](10, 50, 40), longArray3.toArray())
    assertEquals(longArray3.removeAll(toBeRemoved), false)
    toBeRemoved = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](10, 30, 22))
    assert(longArray3.removeAll(toBeRemoved))
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](50, 40), longArray3.toArray())
  })
  test("popPeekFirstTest")({
    val longArray: com.badlogic.gdx.utils.LongArray = com.badlogic.gdx.utils.LongArray.`with`(scala.Array[scala.Long](1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
    val emptyLongArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    assertEquals(longArray.first(), 1)
    assertEquals(longArray.peek(), 10)
    assertEquals(longArray.pop(), 10)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 4, 5, 6, 7, 8, 9), longArray.toArray())
    try {
      val first: scala.Long = emptyLongArray.first()
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IllegalStateException => {
        ()
      }
    }
    try {
      val last: scala.Long = emptyLongArray.pop()
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IllegalStateException => {
        ()
      }
    }
    try {
      val last: scala.Long = emptyLongArray.peek()
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IllegalStateException => {
        ()
      }
    }
  })
  test("emptyTest")({
    assert(new com.badlogic.gdx.utils.LongArray().isEmpty())
    assertEquals(new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1)).isEmpty(), false)
    assertEquals(new com.badlogic.gdx.utils.LongArray().notEmpty(), false)
    assert(new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1)).notEmpty())
  })
  test("clearTest")({
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1))
    longArray.clear()
    assert(longArray.isEmpty())
  })
  test("shrinkTest")({
    val longArray: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray.add(1, 2, 3)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 3), longArray.shrink())
    assertEquals(longArray.items.length, 3)
  })
  test("ensureCapacityTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 0, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 3))
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 0, 0, 0, 0, 0), longArray2.ensureCapacity(2))
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 0, 10, 389, 8, 392, 4, 27346, 2, 234, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), longArray1.ensureCapacity(18))
    try {
      longArray1.ensureCapacity(-6)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IllegalArgumentException => {
        ()
      }
    }
  })
  test("setSizeTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    longArray1.setSize(23)
    assertEquals(longArray1.size, 23)
    longArray1.setSize(10)
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1), longArray1.toArray())
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 3))
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 3, 0, 0, 0, 0, 0), longArray2.setSize(5))
    try {
      longArray1.setSize(-3)
      balticporter.runtime.Asserts.fail()
    } catch {
      case e: java.lang.IllegalArgumentException => {
        ()
      }
    }
  })
  test("resizeTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12, 0, 0), longArray1.resize(23))
  })
  test("sortAndReverseTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = com.badlogic.gdx.utils.LongArray.`with`(scala.Array[scala.Long](1, 2, 4, 6, 32, 53, 564, 53, 2, 1, 89, 90, 10, 389, 8, 392, 4, 27346, 2, 234, 12))
    longArray1.sort()
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](1, 1, 2, 2, 2, 4, 4, 6, 8, 10, 12, 32, 53, 53, 89, 90, 234, 389, 392, 564, 27346), longArray1.toArray())
    longArray1.reverse()
    balticporter.runtime.Asserts.assertArrayEquals(scala.Array[scala.Long](27346, 564, 392, 389, 234, 90, 89, 53, 53, 32, 12, 10, 8, 6, 4, 4, 2, 2, 2, 1, 1), longArray1.toArray())
  })
  test("equalsTest")({
    val longArray1: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    val longArray2: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray()
    longArray1.add(1, 2)
    longArray2.add(1, 2)
    assert(longArray1.equals(longArray2))
    val o: com.badlogic.gdx.utils.ArrayMap[java.lang.Integer, java.lang.Integer] = new com.badlogic.gdx.utils.ArrayMap[java.lang.Integer, java.lang.Integer]()
    assertEquals(longArray1.equals(o), false)
    val longArray3: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(false, 16)
    longArray3.add(1, 2)
    assertEquals(longArray1.equals(longArray3), false)
    val longArray4: com.badlogic.gdx.utils.LongArray = new com.badlogic.gdx.utils.LongArray(true, 12)
    longArray4.add(1, 2)
    assert(longArray1.equals(longArray4))
    longArray1.add(3)
    assertEquals(longArray1.equals(longArray2), false)
  })
}