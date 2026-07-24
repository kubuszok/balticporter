package com.badlogic.gdx.utils

class Sort {
  private var timSort: com.badlogic.gdx.utils.TimSort[?] = null.asInstanceOf[com.badlogic.gdx.utils.TimSort[?]]
  private var comparableTimSort: com.badlogic.gdx.utils.ComparableTimSort = null.asInstanceOf[com.badlogic.gdx.utils.ComparableTimSort]
  def sort[T <: java.lang.Comparable[?]](a: com.badlogic.gdx.utils.Array[T]): scala.Unit = {
    if (this.comparableTimSort == null) {
      this.comparableTimSort = new com.badlogic.gdx.utils.ComparableTimSort()
    } else ()
    this.comparableTimSort.doSort(a.items, 0, a.size)
  }
  def sort(a: scala.Array[java.lang.Object]): scala.Unit = {
    if (this.comparableTimSort == null) {
      this.comparableTimSort = new com.badlogic.gdx.utils.ComparableTimSort()
    } else ()
    this.comparableTimSort.doSort(a, 0, a.length)
  }
  def sort(a: scala.Array[java.lang.Object], fromIndex: scala.Int, toIndex: scala.Int): scala.Unit = {
    if (this.comparableTimSort == null) {
      this.comparableTimSort = new com.badlogic.gdx.utils.ComparableTimSort()
    } else ()
    this.comparableTimSort.doSort(a, fromIndex, toIndex)
  }
  def sort[T](a: com.badlogic.gdx.utils.Array[T], c: java.util.Comparator[? >: T]): scala.Unit = {
    if (this.timSort == null) {
      this.timSort = new com.badlogic.gdx.utils.TimSort()
    } else ()
    this.timSort.doSort(a.items, c, 0, a.size)
  }
  def sort[T](a: scala.Array[T], c: java.util.Comparator[? >: T]): scala.Unit = {
    if (this.timSort == null) {
      this.timSort = new com.badlogic.gdx.utils.TimSort()
    } else ()
    this.timSort.doSort(a, c, 0, a.length)
  }
  def sort[T](a: scala.Array[T], c: java.util.Comparator[? >: T], fromIndex: scala.Int, toIndex: scala.Int): scala.Unit = {
    if (this.timSort == null) {
      this.timSort = new com.badlogic.gdx.utils.TimSort()
    } else ()
    this.timSort.doSort(a, c, fromIndex, toIndex)
  }
}
object Sort {
  var instance$field: Sort = null.asInstanceOf[Sort]
  def instance(): Sort = {
    if (Sort.instance$field == null) {
      Sort.instance$field = new Sort()
    } else ()
    return Sort.instance$field
  }
}