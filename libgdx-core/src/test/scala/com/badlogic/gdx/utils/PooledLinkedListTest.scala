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
    org.junit.Assert.assertEquals(3, this.list.size())
    this.list.iter()
    this.list.next()
    this.list.remove()
    org.junit.Assert.assertEquals(2, this.list.size())
  })
  testCase("iteration", {
    this.list.iter()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(1), this.list.next())
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(3), this.list.next())
    org.junit.Assert.assertNull(this.list.next())
  })
  testCase("reverseIteration", {
    this.list.iterReverse()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(3), this.list.previous())
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(2), this.list.previous())
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(1), this.list.previous())
    org.junit.Assert.assertNull(this.list.previous())
  })
  testCase("remove", {
    this.list.iter()
    this.list.next()
    this.list.remove()
    this.list.next()
    this.list.next()
    this.list.remove()
    this.list.iter()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    org.junit.Assert.assertNull(this.list.next())
  })
  testCase("removeLast", {
    this.list.iter()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(1), this.list.next())
    this.list.removeLast()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    org.junit.Assert.assertNull(this.list.next())
  })
  testCase("clear", {
    this.list.clear()
    org.junit.Assert.assertEquals(0, this.list.size())
    this.list.iter()
    org.junit.Assert.assertNull(this.list.next())
  })
}