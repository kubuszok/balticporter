package com.badlogic.gdx.utils

class TimSort[T] {
  private var a: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  private var c: java.util.Comparator[? >: T] = null.asInstanceOf[java.util.Comparator[? >: T]]
  private var minGallop: scala.Int = TimSort.MIN_GALLOP
  private var tmp: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  private var tmpCount: scala.Int = 0
  private var stackSize: scala.Int = 0
  private var runBase: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  private var runLen: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  private def this(a: scala.Array[T], c: java.util.Comparator[? >: T]) = {
    this()
    this.a = a
    this.c = c
    val len: scala.Int = a.length
    val newArray: scala.Array[T] = new scala.Array[java.lang.Object](if (len < (2 * TimSort.INITIAL_TMP_STORAGE_LENGTH)) len >>> 1 else TimSort.INITIAL_TMP_STORAGE_LENGTH).asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
    this.tmp = newArray
    val stackLen: scala.Int = if (len < 120) 5 else if (len < 1542) 10 else if (len < 119151) 19 else 40
    this.runBase = new scala.Array[scala.Int](stackLen)
    this.runLen = new scala.Array[scala.Int](stackLen)
  }
  this.tmp = new scala.Array[java.lang.Object](TimSort.INITIAL_TMP_STORAGE_LENGTH).asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
  this.runBase = new scala.Array[scala.Int](40)
  this.runLen = new scala.Array[scala.Int](40)
  def doSort(a: scala.Array[T], c: java.util.Comparator[T], lo$arg: scala.Int, hi: scala.Int): scala.Unit = {
    var lo: scala.Int = lo$arg
    this.stackSize = 0
    TimSort.rangeCheck(a.length, lo, hi)
    var nRemaining: scala.Int = hi - lo
    if (nRemaining < 2) {
      return
    } else ()
    if (nRemaining < TimSort.MIN_MERGE) {
      val initRunLen: scala.Int = TimSort.countRunAndMakeAscending(a, lo, hi, c)
      TimSort.binarySort(a, lo, hi, lo + initRunLen, c)
      return
    } else ()
    this.a = a
    this.c = c
    this.tmpCount = 0
    val minRun: scala.Int = TimSort.minRunLength(nRemaining)
    while ({ {
      var runLen: scala.Int = TimSort.countRunAndMakeAscending(a, lo, hi, c)
      if (runLen < minRun) {
        val force: scala.Int = if (nRemaining <= minRun) nRemaining else minRun
        TimSort.binarySort(a, lo, lo + force, lo + runLen, c)
        runLen = force
      } else ()
      this.pushRun(lo, runLen)
      this.mergeCollapse()
      lo = lo + runLen
      nRemaining = nRemaining - runLen
    }; nRemaining != 0 }) ()
    if (TimSort.DEBUG) {
      assert(lo == hi)
    } else ()
    this.mergeForceCollapse()
    if (TimSort.DEBUG) {
      assert(this.stackSize == 1)
    } else ()
    this.a = null
    this.c = null
    val tmp: scala.Array[T] = this.tmp;
    { var i: scala.Int = 0; val n: scala.Int = this.tmpCount; while (i < n) { {
      tmp(i) = null.asInstanceOf[T]
    }; i = i + 1 } }
  }
  private def pushRun(runBase: scala.Int, runLen: scala.Int): scala.Unit = {
    this.runBase(this.stackSize) = runBase
    this.runLen(this.stackSize) = runLen
    this.stackSize = this.stackSize + 1
  }
  private def mergeCollapse(): scala.Unit = {
    while (this.stackSize > 1) {
      var n: scala.Int = this.stackSize - 2
      if (((n >= 1) && (this.runLen(n - 1) <= (this.runLen(n) + this.runLen(n + 1)))) || ((n >= 2) && (this.runLen(n - 2) <= (this.runLen(n) + this.runLen(n - 1))))) {
        if (this.runLen(n - 1) < this.runLen(n + 1)) {
          n = n - 1
        } else ()
      } else {
        if (this.runLen(n) > this.runLen(n + 1)) {
          /* break */ ()
        } else ()
      }
      this.mergeAt(n)
    }
  }
  private def mergeForceCollapse(): scala.Unit = {
    while (this.stackSize > 1) {
      var n: scala.Int = this.stackSize - 2
      if ((n > 0) && (this.runLen(n - 1) < this.runLen(n + 1))) {
        n = n - 1
      } else ()
      this.mergeAt(n)
    }
  }
  private def mergeAt(i: scala.Int): scala.Unit = {
    if (TimSort.DEBUG) {
      assert(this.stackSize >= 2)
    } else ()
    if (TimSort.DEBUG) {
      assert(i >= 0)
    } else ()
    if (TimSort.DEBUG) {
      assert((i == (this.stackSize - 2)) || (i == (this.stackSize - 3)))
    } else ()
    var base1: scala.Int = this.runBase(i)
    var len1: scala.Int = this.runLen(i)
    val base2: scala.Int = this.runBase(i + 1)
    var len2: scala.Int = this.runLen(i + 1)
    if (TimSort.DEBUG) {
      assert((len1 > 0) && (len2 > 0))
    } else ()
    if (TimSort.DEBUG) {
      assert((base1 + len1) == base2)
    } else ()
    this.runLen(i) = len1 + len2
    if (i == (this.stackSize - 3)) {
      this.runBase(i + 1) = this.runBase(i + 2)
      this.runLen(i + 1) = this.runLen(i + 2)
    } else ()
    this.stackSize = this.stackSize - 1
    val k: scala.Int = TimSort.gallopRight(this.a(base2), this.a, base1, len1, 0, this.c)
    if (TimSort.DEBUG) {
      assert(k >= 0)
    } else ()
    base1 = base1 + k
    len1 = len1 - k
    if (len1 == 0) {
      return
    } else ()
    len2 = TimSort.gallopLeft(this.a((base1 + len1) - 1), this.a, base2, len2, len2 - 1, this.c)
    if (TimSort.DEBUG) {
      assert(len2 >= 0)
    } else ()
    if (len2 == 0) {
      return
    } else ()
    if (len1 <= len2) {
      this.mergeLo(base1, len1, base2, len2)
    } else {
      this.mergeHi(base1, len1, base2, len2)
    }
  }
  private def mergeLo(base1: scala.Int, len1$arg: scala.Int, base2: scala.Int, len2$arg: scala.Int): scala.Unit = {
    var len1: scala.Int = len1$arg
    var len2: scala.Int = len2$arg
    if (TimSort.DEBUG) {
      assert(((len1 > 0) && (len2 > 0)) && ((base1 + len1) == base2))
    } else ()
    val a: scala.Array[T] = this.a
    val tmp: scala.Array[T] = this.ensureCapacity(len1).asInstanceOf[scala.Array[T]]
    java.lang.System.arraycopy(a, base1, tmp, 0, len1)
    var cursor1: scala.Int = 0
    var cursor2: scala.Int = base2
    var dest: scala.Int = base1
    a({ dest += 1; dest }) = a({ cursor2 += 1; cursor2 })
    if ({ len2 -= 1; len2 } == 0) {
      java.lang.System.arraycopy(tmp, cursor1, a, dest, len1)
      return
    } else ()
    if (len1 == 1) {
      java.lang.System.arraycopy(a, cursor2, a, dest, len2)
      a(dest + len2) = tmp(cursor1)
      return
    } else ()
    val c: java.util.Comparator[? >: T] = this.c
    var minGallop: scala.Int = this.minGallop
    while (true) {
      var count1: scala.Int = 0
      var count2: scala.Int = 0
      while ({ {
        if (TimSort.DEBUG) {
          assert((len1 > 1) && (len2 > 0))
        } else ()
        if (c.compare(a(cursor2), tmp(cursor1)) < 0) {
          a({ dest += 1; dest }) = a({ cursor2 += 1; cursor2 })
          count2 = count2 + 1
          count1 = 0
          if ({ len2 -= 1; len2 } == 0) {
            /* break */ ()
          } else ()
        } else {
          a({ dest += 1; dest }) = tmp({ cursor1 += 1; cursor1 })
          count1 = count1 + 1
          count2 = 0
          if ({ len1 -= 1; len1 } == 1) {
            /* break */ ()
          } else ()
        }
      }; (count1 | count2) < minGallop }) ()
      while ({ {
        if (TimSort.DEBUG) {
          assert((len1 > 1) && (len2 > 0))
        } else ()
        count1 = TimSort.gallopRight(a(cursor2), tmp, cursor1, len1, 0, c)
        if (count1 != 0) {
          java.lang.System.arraycopy(tmp, cursor1, a, dest, count1)
          dest = dest + count1
          cursor1 = cursor1 + count1
          len1 = len1 - count1
          if (len1 <= 1) {
            /* break */ ()
          } else ()
        } else ()
        a({ dest += 1; dest }) = a({ cursor2 += 1; cursor2 })
        if ({ len2 -= 1; len2 } == 0) {
          /* break */ ()
        } else ()
        count2 = TimSort.gallopLeft(tmp(cursor1), a, cursor2, len2, 0, c)
        if (count2 != 0) {
          java.lang.System.arraycopy(a, cursor2, a, dest, count2)
          dest = dest + count2
          cursor2 = cursor2 + count2
          len2 = len2 - count2
          if (len2 == 0) {
            /* break */ ()
          } else ()
        } else ()
        a({ dest += 1; dest }) = tmp({ cursor1 += 1; cursor1 })
        if ({ len1 -= 1; len1 } == 1) {
          /* break */ ()
        } else ()
        minGallop = minGallop - 1
      }; (count1 >= TimSort.MIN_GALLOP) | (count2 >= TimSort.MIN_GALLOP) }) ()
      if (minGallop < 0) {
        minGallop = 0
      } else ()
      minGallop = minGallop + 2
    }
    this.minGallop = if (minGallop < 1) 1 else minGallop
    if (len1 == 1) {
      if (TimSort.DEBUG) {
        assert(len2 > 0)
      } else ()
      java.lang.System.arraycopy(a, cursor2, a, dest, len2)
      a(dest + len2) = tmp(cursor1)
    } else {
      if (len1 == 0) {
        throw new java.lang.IllegalArgumentException("Comparison method violates its general contract!")
      } else {
        if (TimSort.DEBUG) {
          assert(len2 == 0)
        } else ()
        if (TimSort.DEBUG) {
          assert(len1 > 1)
        } else ()
        java.lang.System.arraycopy(tmp, cursor1, a, dest, len1)
      }
    }
  }
  private def mergeHi(base1: scala.Int, len1$arg: scala.Int, base2: scala.Int, len2$arg: scala.Int): scala.Unit = {
    var len1: scala.Int = len1$arg
    var len2: scala.Int = len2$arg
    if (TimSort.DEBUG) {
      assert(((len1 > 0) && (len2 > 0)) && ((base1 + len1) == base2))
    } else ()
    val a: scala.Array[T] = this.a
    val tmp: scala.Array[T] = this.ensureCapacity(len2).asInstanceOf[scala.Array[T]]
    java.lang.System.arraycopy(a, base2, tmp, 0, len2)
    var cursor1: scala.Int = (base1 + len1) - 1
    var cursor2: scala.Int = len2 - 1
    var dest: scala.Int = (base2 + len2) - 1
    a({ dest -= 1; dest }) = a({ cursor1 -= 1; cursor1 })
    if ({ len1 -= 1; len1 } == 0) {
      java.lang.System.arraycopy(tmp, 0, a, dest - (len2 - 1), len2)
      return
    } else ()
    if (len2 == 1) {
      dest = dest - len1
      cursor1 = cursor1 - len1
      java.lang.System.arraycopy(a, cursor1 + 1, a, dest + 1, len1)
      a(dest) = tmp(cursor2)
      return
    } else ()
    val c: java.util.Comparator[? >: T] = this.c
    var minGallop: scala.Int = this.minGallop
    while (true) {
      var count1: scala.Int = 0
      var count2: scala.Int = 0
      while ({ {
        if (TimSort.DEBUG) {
          assert((len1 > 0) && (len2 > 1))
        } else ()
        if (c.compare(tmp(cursor2), a(cursor1)) < 0) {
          a({ dest -= 1; dest }) = a({ cursor1 -= 1; cursor1 })
          count1 = count1 + 1
          count2 = 0
          if ({ len1 -= 1; len1 } == 0) {
            /* break */ ()
          } else ()
        } else {
          a({ dest -= 1; dest }) = tmp({ cursor2 -= 1; cursor2 })
          count2 = count2 + 1
          count1 = 0
          if ({ len2 -= 1; len2 } == 1) {
            /* break */ ()
          } else ()
        }
      }; (count1 | count2) < minGallop }) ()
      while ({ {
        if (TimSort.DEBUG) {
          assert((len1 > 0) && (len2 > 1))
        } else ()
        count1 = len1 - TimSort.gallopRight(tmp(cursor2), a, base1, len1, len1 - 1, c)
        if (count1 != 0) {
          dest = dest - count1
          cursor1 = cursor1 - count1
          len1 = len1 - count1
          java.lang.System.arraycopy(a, cursor1 + 1, a, dest + 1, count1)
          if (len1 == 0) {
            /* break */ ()
          } else ()
        } else ()
        a({ dest -= 1; dest }) = tmp({ cursor2 -= 1; cursor2 })
        if ({ len2 -= 1; len2 } == 1) {
          /* break */ ()
        } else ()
        count2 = len2 - TimSort.gallopLeft(a(cursor1), tmp, 0, len2, len2 - 1, c)
        if (count2 != 0) {
          dest = dest - count2
          cursor2 = cursor2 - count2
          len2 = len2 - count2
          java.lang.System.arraycopy(tmp, cursor2 + 1, a, dest + 1, count2)
          if (len2 <= 1) {
            /* break */ ()
          } else ()
        } else ()
        a({ dest -= 1; dest }) = a({ cursor1 -= 1; cursor1 })
        if ({ len1 -= 1; len1 } == 0) {
          /* break */ ()
        } else ()
        minGallop = minGallop - 1
      }; (count1 >= TimSort.MIN_GALLOP) | (count2 >= TimSort.MIN_GALLOP) }) ()
      if (minGallop < 0) {
        minGallop = 0
      } else ()
      minGallop = minGallop + 2
    }
    this.minGallop = if (minGallop < 1) 1 else minGallop
    if (len2 == 1) {
      if (TimSort.DEBUG) {
        assert(len1 > 0)
      } else ()
      dest = dest - len1
      cursor1 = cursor1 - len1
      java.lang.System.arraycopy(a, cursor1 + 1, a, dest + 1, len1)
      a(dest) = tmp(cursor2)
    } else {
      if (len2 == 0) {
        throw new java.lang.IllegalArgumentException("Comparison method violates its general contract!")
      } else {
        if (TimSort.DEBUG) {
          assert(len1 == 0)
        } else ()
        if (TimSort.DEBUG) {
          assert(len2 > 0)
        } else ()
        java.lang.System.arraycopy(tmp, 0, a, dest - (len2 - 1), len2)
      }
    }
  }
  private def ensureCapacity(minCapacity: scala.Int): scala.Array[T] = {
    this.tmpCount = java.lang.Math.max(this.tmpCount, minCapacity)
    if (this.tmp.length < minCapacity) {
      var newSize: scala.Int = minCapacity
      newSize = newSize | (newSize >> 1)
      newSize = newSize | (newSize >> 2)
      newSize = newSize | (newSize >> 4)
      newSize = newSize | (newSize >> 8)
      newSize = newSize | (newSize >> 16)
      newSize = newSize + 1
      if (newSize < 0) {
        newSize = minCapacity
      } else {
        newSize = java.lang.Math.min(newSize, this.a.length >>> 1)
      }
      val newArray: scala.Array[T] = new scala.Array[java.lang.Object](newSize).asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
      this.tmp = newArray
    } else ()
    return this.tmp
  }
}
object TimSort {
  private final val MIN_MERGE: scala.Int = 32
  private final val MIN_GALLOP: scala.Int = 7
  private final val INITIAL_TMP_STORAGE_LENGTH: scala.Int = 256
  private final val DEBUG: scala.Boolean = false
  def sort[T](a: scala.Array[T], c: java.util.Comparator[? >: T]): scala.Unit = {
    TimSort.sort(a, 0, a.length, c)
  }
  def sort[T](a: scala.Array[T], lo$arg: scala.Int, hi: scala.Int, c: java.util.Comparator[? >: T]): scala.Unit = {
    var lo: scala.Int = lo$arg
    if (c == null) {
      java.util.Arrays.sort(a.asInstanceOf[scala.Array[java.lang.Object]], lo, hi)
      return
    } else ()
    TimSort.rangeCheck(a.length, lo, hi)
    var nRemaining: scala.Int = hi - lo
    if (nRemaining < 2) {
      return
    } else ()
    if (nRemaining < TimSort.MIN_MERGE) {
      val initRunLen: scala.Int = TimSort.countRunAndMakeAscending(a, lo, hi, c)
      TimSort.binarySort(a, lo, hi, lo + initRunLen, c)
      return
    } else ()
    val ts: TimSort[T] = new TimSort[T](a, c)
    val minRun: scala.Int = TimSort.minRunLength(nRemaining)
    while ({ {
      var runLen: scala.Int = TimSort.countRunAndMakeAscending(a, lo, hi, c)
      if (runLen < minRun) {
        val force: scala.Int = if (nRemaining <= minRun) nRemaining else minRun
        TimSort.binarySort(a, lo, lo + force, lo + runLen, c)
        runLen = force
      } else ()
      ts.pushRun(lo, runLen)
      ts.mergeCollapse()
      lo = lo + runLen
      nRemaining = nRemaining - runLen
    }; nRemaining != 0 }) ()
    if (TimSort.DEBUG) {
      assert(lo == hi)
    } else ()
    ts.mergeForceCollapse()
    if (TimSort.DEBUG) {
      assert(ts.stackSize == 1)
    } else ()
  }
  private def binarySort[T](a: scala.Array[T], lo: scala.Int, hi: scala.Int, start$arg: scala.Int, c: java.util.Comparator[? >: T]): scala.Unit = {
    var start: scala.Int = start$arg
    if (TimSort.DEBUG) {
      assert((lo <= start) && (start <= hi))
    } else ()
    if (start == lo) {
      start = start + 1
    } else ();
    { ; while (start < hi) { {
      val pivot: T = a(start)
      var left: scala.Int = lo
      var right: scala.Int = start
      if (TimSort.DEBUG) {
        assert(left <= right)
      } else ()
      while (left < right) {
        val mid: scala.Int = (left + right) >>> 1
        if (c.compare(pivot, a(mid)) < 0) {
          right = mid
        } else {
          left = mid + 1
        }
      }
      if (TimSort.DEBUG) {
        assert(left == right)
      } else ()
      val n: scala.Int = start - left
      n match {
        case 2 => {
          a(left + 2) = a(left + 1)
          a(left + 1) = a(left)
        }
        case 1 => {
          a(left + 1) = a(left)
        }
        case _ => {
          java.lang.System.arraycopy(a, left, a, left + 1, n)
        }
      }
      a(left) = pivot
    }; start = start + 1 } }
  }
  private def countRunAndMakeAscending[T](a: scala.Array[T], lo: scala.Int, hi: scala.Int, c: java.util.Comparator[? >: T]): scala.Int = {
    if (TimSort.DEBUG) {
      assert(lo < hi)
    } else ()
    var runHi: scala.Int = lo + 1
    if (runHi == hi) {
      return 1
    } else ()
    if (c.compare(a({ runHi += 1; runHi }), a(lo)) < 0) {
      while ((runHi < hi) && (c.compare(a(runHi), a(runHi - 1)) < 0)) {
        runHi = runHi + 1
      }
      TimSort.reverseRange(a, lo, runHi)
    } else {
      while ((runHi < hi) && (c.compare(a(runHi), a(runHi - 1)) >= 0)) {
        runHi = runHi + 1
      }
    }
    return runHi - lo
  }
  private def reverseRange(a: scala.Array[java.lang.Object], lo$arg: scala.Int, hi$arg: scala.Int): scala.Unit = {
    var lo: scala.Int = lo$arg
    var hi: scala.Int = hi$arg
    hi = hi - 1
    while (lo < hi) {
      val t: java.lang.Object = a(lo)
      a({ lo += 1; lo }) = a(hi)
      a({ hi -= 1; hi }) = t
    }
  }
  private def minRunLength(n$arg: scala.Int): scala.Int = {
    var n: scala.Int = n$arg
    if (TimSort.DEBUG) {
      assert(n >= 0)
    } else ()
    var r: scala.Int = 0
    while (n >= TimSort.MIN_MERGE) {
      r = r | (n & 1)
      n = n >> 1
    }
    return n + r
  }
  private def gallopLeft[T](key: T, a: scala.Array[T], base: scala.Int, len: scala.Int, hint: scala.Int, c: java.util.Comparator[? >: T]): scala.Int = {
    if (TimSort.DEBUG) {
      assert(((len > 0) && (hint >= 0)) && (hint < len))
    } else ()
    var lastOfs: scala.Int = 0
    var ofs: scala.Int = 1
    if (c.compare(key, a(base + hint)) > 0) {
      val maxOfs: scala.Int = len - hint
      while ((ofs < maxOfs) && (c.compare(key, a((base + hint) + ofs)) > 0)) {
        lastOfs = ofs
        ofs = (ofs << 1) + 1
        if (ofs <= 0) {
          ofs = maxOfs
        } else ()
      }
      if (ofs > maxOfs) {
        ofs = maxOfs
      } else ()
      lastOfs = lastOfs + hint
      ofs = ofs + hint
    } else {
      val maxOfs: scala.Int = hint + 1
      while ((ofs < maxOfs) && (c.compare(key, a((base + hint) - ofs)) <= 0)) {
        lastOfs = ofs
        ofs = (ofs << 1) + 1
        if (ofs <= 0) {
          ofs = maxOfs
        } else ()
      }
      if (ofs > maxOfs) {
        ofs = maxOfs
      } else ()
      val tmp: scala.Int = lastOfs
      lastOfs = hint - ofs
      ofs = hint - tmp
    }
    if (TimSort.DEBUG) {
      assert((((-1) <= lastOfs) && (lastOfs < ofs)) && (ofs <= len))
    } else ()
    lastOfs = lastOfs + 1
    while (lastOfs < ofs) {
      val m: scala.Int = lastOfs + ((ofs - lastOfs) >>> 1)
      if (c.compare(key, a(base + m)) > 0) {
        lastOfs = m + 1
      } else {
        ofs = m
      }
    }
    if (TimSort.DEBUG) {
      assert(lastOfs == ofs)
    } else ()
    return ofs
  }
  private def gallopRight[T](key: T, a: scala.Array[T], base: scala.Int, len: scala.Int, hint: scala.Int, c: java.util.Comparator[? >: T]): scala.Int = {
    if (TimSort.DEBUG) {
      assert(((len > 0) && (hint >= 0)) && (hint < len))
    } else ()
    var ofs: scala.Int = 1
    var lastOfs: scala.Int = 0
    if (c.compare(key, a(base + hint)) < 0) {
      val maxOfs: scala.Int = hint + 1
      while ((ofs < maxOfs) && (c.compare(key, a((base + hint) - ofs)) < 0)) {
        lastOfs = ofs
        ofs = (ofs << 1) + 1
        if (ofs <= 0) {
          ofs = maxOfs
        } else ()
      }
      if (ofs > maxOfs) {
        ofs = maxOfs
      } else ()
      val tmp: scala.Int = lastOfs
      lastOfs = hint - ofs
      ofs = hint - tmp
    } else {
      val maxOfs: scala.Int = len - hint
      while ((ofs < maxOfs) && (c.compare(key, a((base + hint) + ofs)) >= 0)) {
        lastOfs = ofs
        ofs = (ofs << 1) + 1
        if (ofs <= 0) {
          ofs = maxOfs
        } else ()
      }
      if (ofs > maxOfs) {
        ofs = maxOfs
      } else ()
      lastOfs = lastOfs + hint
      ofs = ofs + hint
    }
    if (TimSort.DEBUG) {
      assert((((-1) <= lastOfs) && (lastOfs < ofs)) && (ofs <= len))
    } else ()
    lastOfs = lastOfs + 1
    while (lastOfs < ofs) {
      val m: scala.Int = lastOfs + ((ofs - lastOfs) >>> 1)
      if (c.compare(key, a(base + m)) < 0) {
        ofs = m
      } else {
        lastOfs = m + 1
      }
    }
    if (TimSort.DEBUG) {
      assert(lastOfs == ofs)
    } else ()
    return ofs
  }
  private def rangeCheck(arrayLen: scala.Int, fromIndex: scala.Int, toIndex: scala.Int): scala.Unit = {
    if (fromIndex > toIndex) {
      throw new java.lang.IllegalArgumentException(((("fromIndex(" + fromIndex) + ") > toIndex(") + toIndex) + ")")
    } else ()
    if (fromIndex < 0) {
      throw new java.lang.ArrayIndexOutOfBoundsException(fromIndex)
    } else ()
    if (toIndex > arrayLen) {
      throw new java.lang.ArrayIndexOutOfBoundsException(toIndex)
    } else ()
  }
}