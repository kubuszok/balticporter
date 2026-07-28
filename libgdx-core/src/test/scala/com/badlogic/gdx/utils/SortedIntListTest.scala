package com.badlogic.gdx.utils

class SortedIntListTest {
  def testIteratorWithAllocation(): scala.Unit = {
    com.badlogic.gdx.utils.Collections.allocateIterators = true
    try {
      val list: com.badlogic.gdx.utils.SortedIntList[java.lang.String] = new com.badlogic.gdx.utils.SortedIntList[java.lang.String]()
      list.insert(0, "hello")
      org.junit.Assert.assertEquals(1, list.size$field)
      org.junit.Assert.assertEquals("hello", list.get(0))
      org.junit.Assert.assertEquals("hello", list.iterator().next.value)
    } finally {
      com.badlogic.gdx.utils.Collections.allocateIterators = false
    }
  }
}