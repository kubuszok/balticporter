package com.badlogic.gdx.utils

class SortTest extends balticporter.runtime.PortedSuite {
  private var sortInstance: com.badlogic.gdx.utils.Sort = null.asInstanceOf[com.badlogic.gdx.utils.Sort]
  @org.junit.Before
  def setUp(): scala.Unit = {
    this.sortInstance = com.badlogic.gdx.utils.Sort.instance()
  }
  testCase("testSortArrayComparable", {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]])
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayWithComparator", {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    val comparator: java.util.Comparator[java.lang.Integer] = new java.util.Comparator[java.lang.Integer]() {
      @java.lang.Override
      override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
        return o1.compareTo(o2)
      }
    }
    this.sortInstance.sort[java.lang.Integer](array, comparator)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayWithComparatorAndRange", {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    val comparator: java.util.Comparator[java.lang.Integer] = new java.util.Comparator[java.lang.Integer]() {
      @java.lang.Override
      override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
        return o1.compareTo(o2)
      }
    }
    this.sortInstance.sort[java.lang.Integer](array, comparator, 2, 7)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayRange", {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]], 2, 7)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArray", {
    val array: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](array)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.items.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayComparableWithPreExistingComparableTimSort", {
    val comparableTimSortField: java.lang.reflect.Field = classOf[com.badlogic.gdx.utils.Sort].getDeclaredField("comparableTimSort")
    comparableTimSortField.setAccessible(true)
    comparableTimSortField.set(this.sortInstance, new com.badlogic.gdx.utils.ComparableTimSort())
    val array: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](array)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.items.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayComparableWithNullComparableTimSort", {
    val comparableTimSortField: java.lang.reflect.Field = classOf[com.badlogic.gdx.utils.Sort].getDeclaredField("comparableTimSort")
    comparableTimSortField.setAccessible(true)
    comparableTimSortField.set(this.sortInstance, null)
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]])
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayWithRangeWithNullComparableTimSort", {
    val comparableTimSortField: java.lang.reflect.Field = classOf[com.badlogic.gdx.utils.Sort].getDeclaredField("comparableTimSort")
    comparableTimSortField.setAccessible(true)
    comparableTimSortField.set(this.sortInstance, null)
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]], 2, 7)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayWithNullTimSort", {
    val timSortField: java.lang.reflect.Field = classOf[com.badlogic.gdx.utils.Sort].getDeclaredField("timSort")
    timSortField.setAccessible(true)
    timSortField.set(this.sortInstance, null)
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    val comparator: java.util.Comparator[java.lang.Integer] = new java.util.Comparator[java.lang.Integer]() {
      @java.lang.Override
      override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
        return o1.compareTo(o2)
      }
    }
    this.sortInstance.sort[java.lang.Integer](array, comparator)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayWithNullTimSortArray", {
    val timSortField: java.lang.reflect.Field = classOf[com.badlogic.gdx.utils.Sort].getDeclaredField("timSort")
    timSortField.setAccessible(true)
    timSortField.set(this.sortInstance, null)
    val array: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    val comparator: java.util.Comparator[java.lang.Integer] = new java.util.Comparator[java.lang.Integer]() {
      @java.lang.Override
      override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
        return o1.compareTo(o2)
      }
    }
    this.sortInstance.sort[java.lang.Integer](array, comparator)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.items.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayWithComparatorAndRangeWithNullTimSort", {
    val timSortField: java.lang.reflect.Field = classOf[com.badlogic.gdx.utils.Sort].getDeclaredField("timSort")
    timSortField.setAccessible(true)
    timSortField.set(this.sortInstance, null)
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    val comparator: java.util.Comparator[java.lang.Integer] = new java.util.Comparator[java.lang.Integer]() {
      @java.lang.Override
      override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
        return o1.compareTo(o2)
      }
    }
    this.sortInstance.sort[java.lang.Integer](array, comparator, 2, 7)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayWithCustomComparator", {
    val array: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    val customComparator: java.util.Comparator[java.lang.Integer] = new java.util.Comparator[java.lang.Integer]() {
      @java.lang.Override
      override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
        return o2.compareTo(o1)
      }
    }
    this.sortInstance.sort[java.lang.Integer](array, customComparator)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](9.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.items.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortEmptyArray", {
    val emptyArray: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer]()
    this.sortInstance.sort(emptyArray.asInstanceOf[scala.Array[java.lang.Object]])
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer]().asInstanceOf[scala.Array[java.lang.Object]], emptyArray.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortSingleElementArray", {
    val singleElementArray: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(singleElementArray.asInstanceOf[scala.Array[java.lang.Object]])
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], singleElementArray.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayWithNulls", {
    val arrayWithNulls: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], null, 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], null, 2.asInstanceOf[java.lang.Integer])
    val comparator: java.util.Comparator[java.lang.Integer] = new NullsFirstComparator()
    this.sortInstance.sort[java.lang.Integer](arrayWithNulls, comparator)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](null, null, 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], arrayWithNulls.asInstanceOf[scala.Array[java.lang.Object]])
  })
  @org.junit.Test(expected = classOf[java.lang.ArrayIndexOutOfBoundsException])
  def testSortArrayRangeWithInvalidIndices(): scala.Unit = {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]], -1, 15)
  }
  testCase("testSortAlreadySortedArrayComparable", {
    val sortedArray: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](sortedArray)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], sortedArray.items.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortArrayWithEqualElements", {
    val equalElementsArray: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](equalElementsArray)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], equalElementsArray.items.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortSingleElementArrayComparable", {
    val singleElementArray: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](singleElementArray)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], singleElementArray.items.asInstanceOf[scala.Array[java.lang.Object]])
  })
  testCase("testSortEmptyArrayComparable", {
    val emptyArray: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer]())
    this.sortInstance.sort[java.lang.Integer](emptyArray)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer]().asInstanceOf[scala.Array[java.lang.Object]], emptyArray.items.asInstanceOf[scala.Array[java.lang.Object]])
  })
  class NullsFirstComparator extends java.util.Comparator[java.lang.Integer] {
    @java.lang.Override
    override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
      if ((o1 == null) && (o2 == null)) {
        return 0
      } else {
        if (o1 == null) {
          return -1
        } else {
          if (o2 == null) {
            return 1
          } else {
            return o1.compareTo(o2)
          }
        }
      }
    }
  }
}