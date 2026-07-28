package com.badlogic.gdx.utils

class PooledLinkedListTest extends munit.FunSuite {
  private var list: com.badlogic.gdx.utils.PooledLinkedList[java.lang.Integer] = null.asInstanceOf[com.badlogic.gdx.utils.PooledLinkedList[java.lang.Integer]]
  @org.junit.Before
  def setUp(): scala.Unit = {
    this.list = new com.badlogic.gdx.utils.PooledLinkedList[java.lang.Integer](10)
    this.list.add(1.asInstanceOf[java.lang.Integer])
    this.list.add(2.asInstanceOf[java.lang.Integer])
    this.list.add(3.asInstanceOf[java.lang.Integer])
  }
  test("size")({
    balticporter.runtime.Asserts.assertEquals(3, this.list.size())
    this.list.iter()
    this.list.next()
    this.list.remove()
    balticporter.runtime.Asserts.assertEquals(2, this.list.size())
  })
  test("iteration")({
    this.list.iter()
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.valueOf(1), this.list.next())
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.valueOf(3), this.list.next())
    balticporter.runtime.Asserts.assertNull(this.list.next())
  })
  test("reverseIteration")({
    this.list.iterReverse()
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.valueOf(3), this.list.previous())
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.valueOf(2), this.list.previous())
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.valueOf(1), this.list.previous())
    balticporter.runtime.Asserts.assertNull(this.list.previous())
  })
  test("remove")({
    this.list.iter()
    this.list.next()
    this.list.remove()
    this.list.next()
    this.list.next()
    this.list.remove()
    this.list.iter()
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    balticporter.runtime.Asserts.assertNull(this.list.next())
  })
  test("removeLast")({
    this.list.iter()
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.valueOf(1), this.list.next())
    this.list.removeLast()
    balticporter.runtime.Asserts.assertEquals(java.lang.Integer.valueOf(2), this.list.next())
    balticporter.runtime.Asserts.assertNull(this.list.next())
  })
  test("clear")({
    this.list.clear()
    balticporter.runtime.Asserts.assertEquals(0, this.list.size())
    this.list.iter()
    balticporter.runtime.Asserts.assertNull(this.list.next())
  })
}