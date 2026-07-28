package com.badlogic.gdx.utils

class SortedIntListTest extends balticporter.runtime.PortedSuite {
  testCase("testIteratorWithAllocation", {
    com.badlogic.gdx.utils.Collections.allocateIterators = true
    try {
      val list: com.badlogic.gdx.utils.SortedIntList[java.lang.String] = new com.badlogic.gdx.utils.SortedIntList[java.lang.String]()
      list.insert(0, "hello")
      assertEquals(1, list.size$field)
      assertEquals("hello", list.get(0))
      assertEquals("hello", list.iterator().next.value)
    } finally {
      com.badlogic.gdx.utils.Collections.allocateIterators = false
    }
  })
}