package com.badlogic.gdx.utils

class MixedPutRemoveTest extends munit.FunSuite {
  test("testLongMapPut")({
    val gdxMap: com.badlogic.gdx.utils.LongMap[java.lang.Integer] = new com.badlogic.gdx.utils.LongMap[java.lang.Integer]()
    val jdkMap: scala.collection.mutable.HashMap[java.lang.Long, java.lang.Integer] = new scala.collection.mutable.HashMap[java.lang.Long, java.lang.Integer]()
    var stateA: scala.Long = 0L
    var stateB: scala.Long = 1L
    var gdxRepeats: scala.Int = 0
    var jdkRepeats: scala.Int = 0
    var item: scala.Long = 0L;
    { var i: scala.Int = 0; while (i < 1048576) { {
      stateA = stateA + -4126379630918253789L
      item = (stateA ^ (stateA >>> 31)) * {
        stateB = stateB + -7046029254386353130L
        stateB
      }
      item = item & (item >>> 24)
      if (gdxMap.put(item, i.asInstanceOf[java.lang.Integer]) != null) {
        gdxRepeats = gdxRepeats + 1
      } else ()
      if (jdkMap.put(item.asInstanceOf[java.lang.Long], i.asInstanceOf[java.lang.Integer]).getOrElse(null.asInstanceOf[java.lang.Integer]) != null) {
        jdkRepeats = jdkRepeats + 1
      } else ()
      assertEquals(jdkMap.size, gdxMap.size)
    }; i = i + 1 } }
    assertEquals(jdkRepeats, gdxRepeats)
  })
  test("testLongMapMix")({
    val gdxMap: com.badlogic.gdx.utils.LongMap[java.lang.Integer] = new com.badlogic.gdx.utils.LongMap[java.lang.Integer]()
    val jdkMap: scala.collection.mutable.HashMap[java.lang.Long, java.lang.Integer] = new scala.collection.mutable.HashMap[java.lang.Long, java.lang.Integer]()
    var stateA: scala.Long = 0L
    var stateB: scala.Long = 1L
    var gdxRemovals: scala.Int = 0
    var jdkRemovals: scala.Int = 0
    var item: scala.Long = 0L;
    { var i: scala.Int = 0; while (i < 1048576) { {
      stateA = stateA + -4126379630918253789L
      item = (stateA ^ (stateA >>> 31)) * {
        stateB = stateB + -7046029254386353130L
        stateB
      }
      item = item & (item >>> 24)
      if (gdxMap.remove(item) == null) {
        gdxMap.put(item, i.asInstanceOf[java.lang.Integer])
      } else {
        gdxRemovals = gdxRemovals + 1
      }
      if (jdkMap.remove(item.asInstanceOf[java.lang.Long]).getOrElse(null.asInstanceOf[java.lang.Integer]) == null) {
        jdkMap.put(item.asInstanceOf[java.lang.Long], i.asInstanceOf[java.lang.Integer]).getOrElse(null.asInstanceOf[java.lang.Integer])
      } else {
        jdkRemovals = jdkRemovals + 1
      }
      assertEquals(jdkMap.size, gdxMap.size)
    }; i = i + 1 } }
    assertEquals(jdkRemovals, gdxRemovals)
  })
  test("testLongMapIterator")({
    val gdxMap: com.badlogic.gdx.utils.LongMap[java.lang.Long] = new com.badlogic.gdx.utils.LongMap[java.lang.Long]()
    var stateA: scala.Long = 0L
    var stateB: scala.Long = 1L
    var temp: scala.Long = 0L
    var actualSize: scala.Int = 0
    var item: scala.Long = 0L;
    { var i: scala.Int = 0; while (i < 65536) { {
      stateA = stateA + -4126379630918253789L
      item = (stateA ^ (stateA >>> 31)) * {
        stateB = stateB + -7046029254386353130L
        stateB
      }
      item = item & (item >>> 24)
      if (gdxMap.put(item, item.asInstanceOf[java.lang.Long]) == null) {
        actualSize = actualSize + 1
      } else ()
      if ((actualSize % 6) == 5) {
        val it: balticporter.runtime.JavaIterator[java.lang.Long] = gdxMap.values().iterator();
        { var n: scala.Int = (item & 3).asInstanceOf[scala.Int] + 1; while (n > 0) { {
          it.next
        }; n = n - 1 } }
        it.remove()
        actualSize = actualSize - 1;
        { var j: scala.Int = 0; while (j < 2) { {
          stateA = stateA + -4126379630918253789L
          item = (stateA ^ (stateA >>> 31)) * {
            stateB = stateB + -7046029254386353130L
            stateB
          }
          item = item & (item >>> 24)
          if (gdxMap.put(item, item.asInstanceOf[java.lang.Long]) == null) {
            actualSize = actualSize + 1
          } else ()
        }; j = j + 1 } }
      } else ()
      assertEquals(actualSize, gdxMap.size)
    }; i = i + 1 } }
    for (ent <- gdxMap) {
      assertEquals(ent.value.longValue(), ent.key)
    }
  })
  test("testIntMapPut")({
    val gdxMap: com.badlogic.gdx.utils.IntMap[java.lang.Integer] = new com.badlogic.gdx.utils.IntMap[java.lang.Integer]()
    val jdkMap: scala.collection.mutable.HashMap[java.lang.Integer, java.lang.Integer] = new scala.collection.mutable.HashMap[java.lang.Integer, java.lang.Integer]()
    var stateA: scala.Long = 0L
    var stateB: scala.Long = 1L
    var temp: scala.Long = 0L
    var gdxRepeats: scala.Int = 0
    var jdkRepeats: scala.Int = 0
    var item: scala.Int = 0;
    { var i: scala.Int = 0; while (i < 1048576) { {
      stateA = stateA + -4126379630918253789L
      temp = (stateA ^ (stateA >>> 31)) * {
        stateB = stateB + -7046029254386353130L
        stateB
      }
      item = (temp & (temp >>> 24)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      if (gdxMap.put(item, i.asInstanceOf[java.lang.Integer]) != null) {
        gdxRepeats = gdxRepeats + 1
      } else ()
      if (jdkMap.put(item.asInstanceOf[java.lang.Integer], i.asInstanceOf[java.lang.Integer]).getOrElse(null.asInstanceOf[java.lang.Integer]) != null) {
        jdkRepeats = jdkRepeats + 1
      } else ()
      assertEquals(jdkMap.size, gdxMap.size)
    }; i = i + 1 } }
    assertEquals(jdkRepeats, gdxRepeats)
  })
  test("testIntMapMix")({
    val gdxMap: com.badlogic.gdx.utils.IntMap[java.lang.Integer] = new com.badlogic.gdx.utils.IntMap[java.lang.Integer]()
    val jdkMap: scala.collection.mutable.HashMap[java.lang.Integer, java.lang.Integer] = new scala.collection.mutable.HashMap[java.lang.Integer, java.lang.Integer]()
    var stateA: scala.Long = 0L
    var stateB: scala.Long = 1L
    var temp: scala.Long = 0L
    var gdxRemovals: scala.Int = 0
    var jdkRemovals: scala.Int = 0
    var item: scala.Int = 0;
    { var i: scala.Int = 0; while (i < 1048576) { {
      stateA = stateA + -4126379630918253789L
      temp = (stateA ^ (stateA >>> 31)) * {
        stateB = stateB + -7046029254386353130L
        stateB
      }
      item = (temp & (temp >>> 24)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      if (gdxMap.remove(item) == null) {
        gdxMap.put(item, i.asInstanceOf[java.lang.Integer])
      } else {
        gdxRemovals = gdxRemovals + 1
      }
      if (jdkMap.remove(item.asInstanceOf[java.lang.Integer]).getOrElse(null.asInstanceOf[java.lang.Integer]) == null) {
        jdkMap.put(item.asInstanceOf[java.lang.Integer], i.asInstanceOf[java.lang.Integer]).getOrElse(null.asInstanceOf[java.lang.Integer])
      } else {
        jdkRemovals = jdkRemovals + 1
      }
      assertEquals(jdkMap.size, gdxMap.size)
    }; i = i + 1 } }
    assertEquals(jdkRemovals, gdxRemovals)
  })
  test("testIntMapIterator")({
    val gdxMap: com.badlogic.gdx.utils.IntMap[java.lang.Integer] = new com.badlogic.gdx.utils.IntMap[java.lang.Integer]()
    var stateA: scala.Long = 0L
    var stateB: scala.Long = 1L
    var temp: scala.Long = 0L
    val gdxRemovals: scala.Int = 0
    var actualSize: scala.Int = 0
    var item: scala.Int = 0;
    { var i: scala.Int = 0; while (i < 65536) { {
      stateA = stateA + -4126379630918253789L
      temp = (stateA ^ (stateA >>> 31)) * {
        stateB = stateB + -7046029254386353130L
        stateB
      }
      item = (temp & (temp >>> 24)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      if (gdxMap.put(item, item.asInstanceOf[java.lang.Integer]) == null) {
        actualSize = actualSize + 1
      } else ()
      if ((actualSize % 6) == 5) {
        val it: balticporter.runtime.JavaIterator[java.lang.Integer] = gdxMap.values().iterator();
        { var n: scala.Int = (temp & 3).asInstanceOf[scala.Int] + 1; while (n > 0) { {
          it.next
        }; n = n - 1 } }
        it.remove()
        actualSize = actualSize - 1;
        { var j: scala.Int = 0; while (j < 2) { {
          stateA = stateA + -4126379630918253789L
          temp = (stateA ^ (stateA >>> 31)) * {
            stateB = stateB + -7046029254386353130L
            stateB
          }
          item = (temp & (temp >>> 24)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
          if (gdxMap.put(item, item.asInstanceOf[java.lang.Integer]) == null) {
            actualSize = actualSize + 1
          } else ()
        }; j = j + 1 } }
      } else ()
      assertEquals(actualSize, gdxMap.size)
    }; i = i + 1 } }
    for (ent <- gdxMap) {
      assertEquals(ent.value.intValue(), ent.key)
    }
  })
  test("testObjectMapPut")({
    val gdxMap: com.badlogic.gdx.utils.ObjectMap[java.lang.Integer, java.lang.Integer] = new com.badlogic.gdx.utils.ObjectMap[java.lang.Integer, java.lang.Integer]()
    val jdkMap: scala.collection.mutable.HashMap[java.lang.Integer, java.lang.Integer] = new scala.collection.mutable.HashMap[java.lang.Integer, java.lang.Integer]()
    var stateA: scala.Long = 0L
    var stateB: scala.Long = 1L
    var temp: scala.Long = 0L
    var gdxRepeats: scala.Int = 0
    var jdkRepeats: scala.Int = 0
    var item: scala.Int = 0;
    { var i: scala.Int = 0; while (i < 1048576) { {
      stateA = stateA + -4126379630918253789L
      temp = (stateA ^ (stateA >>> 31)) * {
        stateB = stateB + -7046029254386353130L
        stateB
      }
      item = (temp & (temp >>> 24)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      if (gdxMap.put(item.asInstanceOf[java.lang.Integer], i.asInstanceOf[java.lang.Integer]) != null) {
        gdxRepeats = gdxRepeats + 1
      } else ()
      if (jdkMap.put(item.asInstanceOf[java.lang.Integer], i.asInstanceOf[java.lang.Integer]).getOrElse(null.asInstanceOf[java.lang.Integer]) != null) {
        jdkRepeats = jdkRepeats + 1
      } else ()
      assertEquals(jdkMap.size, gdxMap.size)
    }; i = i + 1 } }
    assertEquals(jdkRepeats, gdxRepeats)
  })
  test("testObjectMapMix")({
    val gdxMap: com.badlogic.gdx.utils.ObjectMap[java.lang.Integer, java.lang.Integer] = new com.badlogic.gdx.utils.ObjectMap[java.lang.Integer, java.lang.Integer]()
    val jdkMap: scala.collection.mutable.HashMap[java.lang.Integer, java.lang.Integer] = new scala.collection.mutable.HashMap[java.lang.Integer, java.lang.Integer]()
    var stateA: scala.Long = 0L
    var stateB: scala.Long = 1L
    var temp: scala.Long = 0L
    var gdxRemovals: scala.Int = 0
    var jdkRemovals: scala.Int = 0
    var item: scala.Int = 0;
    { var i: scala.Int = 0; while (i < 1048576) { {
      stateA = stateA + -4126379630918253789L
      temp = (stateA ^ (stateA >>> 31)) * {
        stateB = stateB + -7046029254386353130L
        stateB
      }
      item = (temp & (temp >>> 24)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      if (gdxMap.remove(item.asInstanceOf[java.lang.Integer]) == null) {
        gdxMap.put(item.asInstanceOf[java.lang.Integer], i.asInstanceOf[java.lang.Integer])
      } else {
        gdxRemovals = gdxRemovals + 1
      }
      if (jdkMap.remove(item.asInstanceOf[java.lang.Integer]).getOrElse(null.asInstanceOf[java.lang.Integer]) == null) {
        jdkMap.put(item.asInstanceOf[java.lang.Integer], i.asInstanceOf[java.lang.Integer]).getOrElse(null.asInstanceOf[java.lang.Integer])
      } else {
        jdkRemovals = jdkRemovals + 1
      }
      assertEquals(jdkMap.size, gdxMap.size)
    }; i = i + 1 } }
    assertEquals(jdkRemovals, gdxRemovals)
  })
}