package com.badlogic.gdx.utils

class SortedIntListTest extends balticporter.runtime.PortedSuite {
  testCase("testIteratorWithAllocation", {
    com.badlogic.gdx.utils.Collections.allocateIterators = true
    try {
      val list: com.badlogic.gdx.utils.SortedIntList[java.lang.String] = new com.badlogic.gdx.utils.SortedIntList[java.lang.String]()
      list.insert(0, "hello")
      balticporter.runtime.Asserts.assertEquals(1, list.size$field)
      balticporter.runtime.Asserts.assertEquals("hello", list.get(0))
      balticporter.runtime.Asserts.assertEquals("hello", list.iterator().next.value)
    } finally {
      com.badlogic.gdx.utils.Collections.allocateIterators = false
    }
  })
}