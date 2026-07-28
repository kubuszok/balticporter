package com.badlogic.gdx.utils

class SortedIntListTest extends munit.FunSuite {
  test("testIteratorWithAllocation")({
    com.badlogic.gdx.utils.Collections.allocateIterators = true
    try {
      val list: com.badlogic.gdx.utils.SortedIntList[java.lang.String] = new com.badlogic.gdx.utils.SortedIntList[java.lang.String]()
      list.insert(0, "hello")
      assertEquals(list.size$field, 1)
      assertEquals(list.get(0), "hello")
      assertEquals(list.iterator().next.value, "hello")
    } finally {
      com.badlogic.gdx.utils.Collections.allocateIterators = false
    }
  })
}