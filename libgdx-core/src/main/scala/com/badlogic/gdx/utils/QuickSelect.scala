package com.badlogic.gdx.utils

class QuickSelect[T] {
  private var array: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  private var comp: java.util.Comparator[? >: T] = null.asInstanceOf[java.util.Comparator[? >: T]]
  def select(items: scala.Array[T], comp: java.util.Comparator[T], n: scala.Int, size: scala.Int): scala.Int = {
    this.array = items
    this.comp = comp
    return this.recursiveSelect(0, size - 1, n)
  }
  private def partition(left: scala.Int, right: scala.Int, pivot: scala.Int): scala.Int = {
    val pivotValue: T = this.array(pivot)
    this.swap(right, pivot)
    var storage: scala.Int = left;
    { var i: scala.Int = left; while (i < right) { {
      if (this.comp.asInstanceOf[java.util.Comparator[java.lang.Object]].compare(this.array(i).asInstanceOf[java.lang.Object], pivotValue.asInstanceOf[java.lang.Object]) < 0) {
        this.swap(storage, i)
        storage = storage + 1
      } else ()
    }; i = i + 1 } }
    this.swap(right, storage)
    return storage
  }
  private def recursiveSelect(left: scala.Int, right: scala.Int, k: scala.Int): scala.Int = {
    if (left == right) {
      return left
    } else ()
    val pivotIndex: scala.Int = this.medianOfThreePivot(left, right)
    val pivotNewIndex: scala.Int = this.partition(left, right, pivotIndex)
    val pivotDist: scala.Int = (pivotNewIndex - left) + 1
    var result: scala.Int = 0
    if (pivotDist == k) {
      result = pivotNewIndex
    } else {
      if (k < pivotDist) {
        result = this.recursiveSelect(left, pivotNewIndex - 1, k)
      } else {
        result = this.recursiveSelect(pivotNewIndex + 1, right, k - pivotDist)
      }
    }
    return result
  }
  private def medianOfThreePivot(leftIdx: scala.Int, rightIdx: scala.Int): scala.Int = {
    val left: T = this.array(leftIdx)
    val midIdx: scala.Int = (leftIdx + rightIdx) / 2
    val mid: T = this.array(midIdx)
    val right: T = this.array(rightIdx)
    if (this.comp.asInstanceOf[java.util.Comparator[java.lang.Object]].compare(left.asInstanceOf[java.lang.Object], mid.asInstanceOf[java.lang.Object]) > 0) {
      if (this.comp.asInstanceOf[java.util.Comparator[java.lang.Object]].compare(mid.asInstanceOf[java.lang.Object], right.asInstanceOf[java.lang.Object]) > 0) {
        return midIdx
      } else {
        if (this.comp.asInstanceOf[java.util.Comparator[java.lang.Object]].compare(left.asInstanceOf[java.lang.Object], right.asInstanceOf[java.lang.Object]) > 0) {
          return rightIdx
        } else {
          return leftIdx
        }
      }
    } else {
      if (this.comp.asInstanceOf[java.util.Comparator[java.lang.Object]].compare(left.asInstanceOf[java.lang.Object], right.asInstanceOf[java.lang.Object]) > 0) {
        return leftIdx
      } else {
        if (this.comp.asInstanceOf[java.util.Comparator[java.lang.Object]].compare(mid.asInstanceOf[java.lang.Object], right.asInstanceOf[java.lang.Object]) > 0) {
          return rightIdx
        } else {
          return midIdx
        }
      }
    }
  }
  private def swap(left: scala.Int, right: scala.Int): scala.Unit = {
    val tmp: T = this.array(left)
    this.array(left) = this.array(right)
    this.array(right) = tmp
  }
}