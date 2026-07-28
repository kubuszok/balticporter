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
    assertEquals(this.list.size(), 3)
    this.list.iter()
    this.list.next()
    this.list.remove()
    assertEquals(this.list.size(), 2)
  })
  test("iteration")({
    this.list.iter()
    assertEquals(this.list.next(), java.lang.Integer.valueOf(1))
    assertEquals(this.list.next(), java.lang.Integer.valueOf(2))
    assertEquals(this.list.next(), java.lang.Integer.valueOf(3))
    assertEquals(this.list.next(), null)
  })
  test("reverseIteration")({
    this.list.iterReverse()
    assertEquals(this.list.previous(), java.lang.Integer.valueOf(3))
    assertEquals(this.list.previous(), java.lang.Integer.valueOf(2))
    assertEquals(this.list.previous(), java.lang.Integer.valueOf(1))
    assertEquals(this.list.previous(), null)
  })
  test("remove")({
    this.list.iter()
    this.list.next()
    this.list.remove()
    this.list.next()
    this.list.next()
    this.list.remove()
    this.list.iter()
    assertEquals(this.list.next(), java.lang.Integer.valueOf(2))
    assertEquals(this.list.next(), null)
  })
  test("removeLast")({
    this.list.iter()
    assertEquals(this.list.next(), java.lang.Integer.valueOf(1))
    this.list.removeLast()
    assertEquals(this.list.next(), java.lang.Integer.valueOf(2))
    assertEquals(this.list.next(), null)
  })
  test("clear")({
    this.list.clear()
    assertEquals(this.list.size(), 0)
    this.list.iter()
    assertEquals(this.list.next(), null)
  })
}