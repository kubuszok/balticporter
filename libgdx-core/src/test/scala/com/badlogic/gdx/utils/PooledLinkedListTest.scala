package com.badlogic.gdx.utils

class PooledLinkedListTest extends balticporter.runtime.PortedSuite {
  private var list: com.badlogic.gdx.utils.PooledLinkedList[java.lang.Integer] = null.asInstanceOf[com.badlogic.gdx.utils.PooledLinkedList[java.lang.Integer]]
  @org.junit.Before
  def setUp(): scala.Unit = {
    this.list = new com.badlogic.gdx.utils.PooledLinkedList[java.lang.Integer](10)
    this.list.add(1.asInstanceOf[java.lang.Integer])
    this.list.add(2.asInstanceOf[java.lang.Integer])
    this.list.add(3.asInstanceOf[java.lang.Integer])
  }
  testCase("size", {
    assertEquals(3, this.list.size())
    this.list.iter()
    this.list.next()
    this.list.remove()
    assertEquals(2, this.list.size())
  })
  testCase("iteration", {
    this.list.iter()
    assertEquals(java.lang.Integer.valueOf(1), this.list.next())
    assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    assertEquals(java.lang.Integer.valueOf(3), this.list.next())
    assertNull(this.list.next())
  })
  testCase("reverseIteration", {
    this.list.iterReverse()
    assertEquals(java.lang.Integer.valueOf(3), this.list.previous())
    assertEquals(java.lang.Integer.valueOf(2), this.list.previous())
    assertEquals(java.lang.Integer.valueOf(1), this.list.previous())
    assertNull(this.list.previous())
  })
  testCase("remove", {
    this.list.iter()
    this.list.next()
    this.list.remove()
    this.list.next()
    this.list.next()
    this.list.remove()
    this.list.iter()
    assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    assertNull(this.list.next())
  })
  testCase("removeLast", {
    this.list.iter()
    assertEquals(java.lang.Integer.valueOf(1), this.list.next())
    this.list.removeLast()
    assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    assertNull(this.list.next())
  })
  testCase("clear", {
    this.list.clear()
    assertEquals(0, this.list.size())
    this.list.iter()
    assertNull(this.list.next())
  })
}