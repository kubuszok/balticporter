package com.badlogic.gdx.utils

class PooledLinkedListTest {
  private var list: com.badlogic.gdx.utils.PooledLinkedList[java.lang.Integer] = null.asInstanceOf[com.badlogic.gdx.utils.PooledLinkedList[java.lang.Integer]]
  def setUp(): scala.Unit = {
    this.list = new com.badlogic.gdx.utils.PooledLinkedList[java.lang.Integer](10)
    this.list.add(1.asInstanceOf[java.lang.Integer])
    this.list.add(2.asInstanceOf[java.lang.Integer])
    this.list.add(3.asInstanceOf[java.lang.Integer])
  }
  def size(): scala.Unit = {
    org.junit.Assert.assertEquals(3, this.list.size())
    this.list.iter()
    this.list.next()
    this.list.remove()
    org.junit.Assert.assertEquals(2, this.list.size())
  }
  def iteration(): scala.Unit = {
    this.list.iter()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(1), this.list.next())
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(3), this.list.next())
    org.junit.Assert.assertNull(this.list.next())
  }
  def reverseIteration(): scala.Unit = {
    this.list.iterReverse()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(3), this.list.previous())
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(2), this.list.previous())
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(1), this.list.previous())
    org.junit.Assert.assertNull(this.list.previous())
  }
  def remove(): scala.Unit = {
    this.list.iter()
    this.list.next()
    this.list.remove()
    this.list.next()
    this.list.next()
    this.list.remove()
    this.list.iter()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    org.junit.Assert.assertNull(this.list.next())
  }
  def removeLast(): scala.Unit = {
    this.list.iter()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(1), this.list.next())
    this.list.removeLast()
    org.junit.Assert.assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    org.junit.Assert.assertNull(this.list.next())
  }
  def clear(): scala.Unit = {
    this.list.clear()
    org.junit.Assert.assertEquals(0, this.list.size())
    this.list.iter()
    org.junit.Assert.assertNull(this.list.next())
  }
}