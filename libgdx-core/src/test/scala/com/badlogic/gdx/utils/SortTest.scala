package com.badlogic.gdx.utils

class SortTest {
  private var sortInstance: com.badlogic.gdx.utils.Sort = null.asInstanceOf[com.badlogic.gdx.utils.Sort]
  @org.junit.Before
  def setUp(): scala.Unit = {
    this.sortInstance = com.badlogic.gdx.utils.Sort.instance()
  }
  @org.junit.Test
  def testSortArrayComparable(): scala.Unit = {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]])
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArrayWithComparator(): scala.Unit = {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    val comparator: java.util.Comparator[java.lang.Integer] = new java.util.Comparator[java.lang.Integer]() {
      @java.lang.Override
      override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
        return o1.compareTo(o2)
      }
    }
    this.sortInstance.sort[java.lang.Integer](array, comparator)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArrayWithComparatorAndRange(): scala.Unit = {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    val comparator: java.util.Comparator[java.lang.Integer] = new java.util.Comparator[java.lang.Integer]() {
      @java.lang.Override
      override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
        return o1.compareTo(o2)
      }
    }
    this.sortInstance.sort[java.lang.Integer](array, comparator, 2, 7)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArrayRange(): scala.Unit = {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]], 2, 7)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArray(): scala.Unit = {
    val array: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](array)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.items.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArrayComparableWithPreExistingComparableTimSort(): scala.Unit = {
    val comparableTimSortField: java.lang.reflect.Field = classOf[com.badlogic.gdx.utils.Sort].getDeclaredField("comparableTimSort")
    comparableTimSortField.setAccessible(true)
    comparableTimSortField.set(this.sortInstance, new com.badlogic.gdx.utils.ComparableTimSort())
    val array: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](array)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.items.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArrayComparableWithNullComparableTimSort(): scala.Unit = {
    val comparableTimSortField: java.lang.reflect.Field = classOf[com.badlogic.gdx.utils.Sort].getDeclaredField("comparableTimSort")
    comparableTimSortField.setAccessible(true)
    comparableTimSortField.set(this.sortInstance, null)
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]])
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArrayWithRangeWithNullComparableTimSort(): scala.Unit = {
    val comparableTimSortField: java.lang.reflect.Field = classOf[com.badlogic.gdx.utils.Sort].getDeclaredField("comparableTimSort")
    comparableTimSortField.setAccessible(true)
    comparableTimSortField.set(this.sortInstance, null)
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]], 2, 7)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArrayWithNullTimSort(): scala.Unit = {
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
  }
  @org.junit.Test
  def testSortArrayWithNullTimSortArray(): scala.Unit = {
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
  }
  @org.junit.Test
  def testSortArrayWithComparatorAndRangeWithNullTimSort(): scala.Unit = {
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
  }
  @org.junit.Test
  def testSortArrayWithCustomComparator(): scala.Unit = {
    val array: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    val customComparator: java.util.Comparator[java.lang.Integer] = new java.util.Comparator[java.lang.Integer]() {
      @java.lang.Override
      override def compare(o1: java.lang.Integer, o2: java.lang.Integer): scala.Int = {
        return o2.compareTo(o1)
      }
    }
    this.sortInstance.sort[java.lang.Integer](array, customComparator)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](9.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], array.items.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortEmptyArray(): scala.Unit = {
    val emptyArray: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer]()
    this.sortInstance.sort(emptyArray.asInstanceOf[scala.Array[java.lang.Object]])
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer]().asInstanceOf[scala.Array[java.lang.Object]], emptyArray.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortSingleElementArray(): scala.Unit = {
    val singleElementArray: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(singleElementArray.asInstanceOf[scala.Array[java.lang.Object]])
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], singleElementArray.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArrayWithNulls(): scala.Unit = {
    val arrayWithNulls: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], null, 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], null, 2.asInstanceOf[java.lang.Integer])
    val comparator: java.util.Comparator[java.lang.Integer] = new NullsFirstComparator()
    this.sortInstance.sort[java.lang.Integer](arrayWithNulls, comparator)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](null, null, 1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], arrayWithNulls.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test(expected = classOf[java.lang.ArrayIndexOutOfBoundsException])
  def testSortArrayRangeWithInvalidIndices(): scala.Unit = {
    val array: scala.Array[java.lang.Integer] = scala.Array[java.lang.Integer](3.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 1.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 9.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 6.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer])
    this.sortInstance.sort(array.asInstanceOf[scala.Array[java.lang.Object]], -1, 15)
  }
  @org.junit.Test
  def testSortAlreadySortedArrayComparable(): scala.Unit = {
    val sortedArray: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](sortedArray)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 3.asInstanceOf[java.lang.Integer], 4.asInstanceOf[java.lang.Integer], 5.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], sortedArray.items.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortArrayWithEqualElements(): scala.Unit = {
    val equalElementsArray: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](equalElementsArray)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer], 2.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], equalElementsArray.items.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortSingleElementArrayComparable(): scala.Unit = {
    val singleElementArray: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer]))
    this.sortInstance.sort[java.lang.Integer](singleElementArray)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer](1.asInstanceOf[java.lang.Integer]).asInstanceOf[scala.Array[java.lang.Object]], singleElementArray.items.asInstanceOf[scala.Array[java.lang.Object]])
  }
  @org.junit.Test
  def testSortEmptyArrayComparable(): scala.Unit = {
    val emptyArray: com.badlogic.gdx.utils.Array[java.lang.Integer] = new com.badlogic.gdx.utils.Array[java.lang.Integer](scala.Array[java.lang.Integer]())
    this.sortInstance.sort[java.lang.Integer](emptyArray)
    org.junit.Assert.assertArrayEquals(scala.Array[java.lang.Integer]().asInstanceOf[scala.Array[java.lang.Object]], emptyArray.items.asInstanceOf[scala.Array[java.lang.Object]])
  }
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