package com.badlogic.gdx.utils

class Select {
  private var quickSelect: com.badlogic.gdx.utils.QuickSelect[?] = null.asInstanceOf[com.badlogic.gdx.utils.QuickSelect[?]]
  def select[T](items: scala.Array[T], comp: java.util.Comparator[T], kthLowest: scala.Int, size: scala.Int): T = {
    val idx: scala.Int = this.selectIndex(items.asInstanceOf[scala.Array[java.lang.Object]], comp, kthLowest, size)
    return items(idx)
  }
  def selectIndex[T](items: scala.Array[T], comp: java.util.Comparator[T], kthLowest: scala.Int, size: scala.Int): scala.Int = {
    if (size < 1) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("cannot select from empty array (size < 1)")
    } else {
      if (kthLowest > size) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Kth rank is larger than size. k: " + kthLowest) + ", size: ") + size)
      } else ()
    }
    var idx: scala.Int = 0
    if (kthLowest == 1) {
      idx = this.fastMin(items.asInstanceOf[scala.Array[java.lang.Object]], comp, size)
    } else {
      if (kthLowest == size) {
        idx = this.fastMax(items.asInstanceOf[scala.Array[java.lang.Object]], comp, size)
      } else {
        if (this.quickSelect == null) {
          this.quickSelect = new com.badlogic.gdx.utils.QuickSelect()
        } else ()
        idx = this.quickSelect.select(items.asInstanceOf[scala.Array[java.lang.Object]], comp, kthLowest, size)
      }
    }
    return idx
  }
  private def fastMin[T](items: scala.Array[T], comp: java.util.Comparator[T], size: scala.Int): scala.Int = {
    var lowestIdx: scala.Int = 0;
    { var i: scala.Int = 1; while (i < size) { {
      val comparison: scala.Int = comp.compare(items(i), items(lowestIdx))
      if (comparison < 0) {
        lowestIdx = i
      } else ()
    }; i = i + 1 } }
    return lowestIdx
  }
  private def fastMax[T](items: scala.Array[T], comp: java.util.Comparator[T], size: scala.Int): scala.Int = {
    var highestIdx: scala.Int = 0;
    { var i: scala.Int = 1; while (i < size) { {
      val comparison: scala.Int = comp.compare(items(i), items(highestIdx))
      if (comparison > 0) {
        highestIdx = i
      } else ()
    }; i = i + 1 } }
    return highestIdx
  }
}
object Select {
  var instance$field: Select = null.asInstanceOf[Select]
  def instance(): Select = {
    if (Select.instance$field == null) {
      Select.instance$field = new Select()
    } else ()
    return Select.instance$field
  }
}