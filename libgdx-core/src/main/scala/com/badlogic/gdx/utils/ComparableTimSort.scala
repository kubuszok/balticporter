package com.badlogic.gdx.utils

class ComparableTimSort {
  private var a: scala.Array[java.lang.Object] = null.asInstanceOf[scala.Array[java.lang.Object]]
  private var minGallop: scala.Int = ComparableTimSort.MIN_GALLOP
  private var tmp: scala.Array[java.lang.Object] = null.asInstanceOf[scala.Array[java.lang.Object]]
  private var tmpCount: scala.Int = 0
  private var stackSize: scala.Int = 0
  private var runBase: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  private var runLen: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  private def this(a: scala.Array[java.lang.Object]) = {
    this()
    this.a = a
    val len: scala.Int = a.length
    val newArray: scala.Array[java.lang.Object] = new scala.Array[java.lang.Object](if (len < (2 * ComparableTimSort.INITIAL_TMP_STORAGE_LENGTH)) len >>> 1 else ComparableTimSort.INITIAL_TMP_STORAGE_LENGTH)
    this.tmp = newArray
    val stackLen: scala.Int = if (len < 120) 5 else if (len < 1542) 10 else if (len < 119151) 19 else 40
    this.runBase = new scala.Array[scala.Int](stackLen)
    this.runLen = new scala.Array[scala.Int](stackLen)
  }
  this.tmp = new scala.Array[java.lang.Object](ComparableTimSort.INITIAL_TMP_STORAGE_LENGTH)
  this.runBase = new scala.Array[scala.Int](40)
  this.runLen = new scala.Array[scala.Int](40)
  def doSort(a: scala.Array[java.lang.Object], lo$arg: scala.Int, hi: scala.Int): scala.Unit = {
    var lo: scala.Int = lo$arg
    this.stackSize = 0
    ComparableTimSort.rangeCheck(a.length, lo, hi)
    var nRemaining: scala.Int = hi - lo
    if (nRemaining < 2) {
      return
    } else ()
    if (nRemaining < ComparableTimSort.MIN_MERGE) {
      val initRunLen: scala.Int = ComparableTimSort.countRunAndMakeAscending(a, lo, hi)
      ComparableTimSort.binarySort(a, lo, hi, lo + initRunLen)
      return
    } else ()
    this.a = a
    this.tmpCount = 0
    val minRun: scala.Int = ComparableTimSort.minRunLength(nRemaining)
    while ({ {
      var runLen: scala.Int = ComparableTimSort.countRunAndMakeAscending(a, lo, hi)
      if (runLen < minRun) {
        val force: scala.Int = if (nRemaining <= minRun) nRemaining else minRun
        ComparableTimSort.binarySort(a, lo, lo + force, lo + runLen)
        runLen = force
      } else ()
      this.pushRun(lo, runLen)
      this.mergeCollapse()
      lo = lo + runLen
      nRemaining = nRemaining - runLen
    }; nRemaining != 0 }) ()
    if (ComparableTimSort.DEBUG) {
      assert(lo == hi)
    } else ()
    this.mergeForceCollapse()
    if (ComparableTimSort.DEBUG) {
      assert(this.stackSize == 1)
    } else ()
    this.a = null
    val tmp: scala.Array[java.lang.Object] = this.tmp;
    { var i: scala.Int = 0; val n: scala.Int = this.tmpCount; while (i < n) { {
      tmp(i) = null
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
      if ((n > 0) && (this.runLen(n - 1) <= (this.runLen(n) + this.runLen(n + 1)))) {
        if (this.runLen(n - 1) < this.runLen(n + 1)) {
          n = n - 1
        } else ()
        this.mergeAt(n)
      } else {
        if (this.runLen(n) <= this.runLen(n + 1)) {
          this.mergeAt(n)
        } else {
          /* break */ ()
        }
      }
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
    if (ComparableTimSort.DEBUG) {
      assert(this.stackSize >= 2)
    } else ()
    if (ComparableTimSort.DEBUG) {
      assert(i >= 0)
    } else ()
    if (ComparableTimSort.DEBUG) {
      assert((i == (this.stackSize - 2)) || (i == (this.stackSize - 3)))
    } else ()
    var base1: scala.Int = this.runBase(i)
    var len1: scala.Int = this.runLen(i)
    val base2: scala.Int = this.runBase(i + 1)
    var len2: scala.Int = this.runLen(i + 1)
    if (ComparableTimSort.DEBUG) {
      assert((len1 > 0) && (len2 > 0))
    } else ()
    if (ComparableTimSort.DEBUG) {
      assert((base1 + len1) == base2)
    } else ()
    this.runLen(i) = len1 + len2
    if (i == (this.stackSize - 3)) {
      this.runBase(i + 1) = this.runBase(i + 2)
      this.runLen(i + 1) = this.runLen(i + 2)
    } else ()
    this.stackSize = this.stackSize - 1
    val k: scala.Int = ComparableTimSort.gallopRight(this.a(base2).asInstanceOf[java.lang.Comparable[java.lang.Object]].asInstanceOf[java.lang.Comparable[java.lang.Object]], this.a, base1, len1, 0)
    if (ComparableTimSort.DEBUG) {
      assert(k >= 0)
    } else ()
    base1 = base1 + k
    len1 = len1 - k
    if (len1 == 0) {
      return
    } else ()
    len2 = ComparableTimSort.gallopLeft(this.a((base1 + len1) - 1).asInstanceOf[java.lang.Comparable[java.lang.Object]].asInstanceOf[java.lang.Comparable[java.lang.Object]], this.a, base2, len2, len2 - 1)
    if (ComparableTimSort.DEBUG) {
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
    if (ComparableTimSort.DEBUG) {
      assert(((len1 > 0) && (len2 > 0)) && ((base1 + len1) == base2))
    } else ()
    val a: scala.Array[java.lang.Object] = this.a
    val tmp: scala.Array[java.lang.Object] = this.ensureCapacity(len1)
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
    var minGallop: scala.Int = this.minGallop
    while (true) {
      var count1: scala.Int = 0
      var count2: scala.Int = 0
      while ({ {
        if (ComparableTimSort.DEBUG) {
          assert((len1 > 1) && (len2 > 0))
        } else ()
        if (a(cursor2).asInstanceOf[java.lang.Comparable[?]].compareTo(tmp(cursor1)) < 0) {
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
        if (ComparableTimSort.DEBUG) {
          assert((len1 > 1) && (len2 > 0))
        } else ()
        count1 = ComparableTimSort.gallopRight(a(cursor2).asInstanceOf[java.lang.Comparable[?]].asInstanceOf[java.lang.Comparable[java.lang.Object]], tmp, cursor1, len1, 0)
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
        count2 = ComparableTimSort.gallopLeft(tmp(cursor1).asInstanceOf[java.lang.Comparable[?]].asInstanceOf[java.lang.Comparable[java.lang.Object]], a, cursor2, len2, 0)
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
      }; (count1 >= ComparableTimSort.MIN_GALLOP) | (count2 >= ComparableTimSort.MIN_GALLOP) }) ()
      if (minGallop < 0) {
        minGallop = 0
      } else ()
      minGallop = minGallop + 2
    }
    this.minGallop = if (minGallop < 1) 1 else minGallop
    if (len1 == 1) {
      if (ComparableTimSort.DEBUG) {
        assert(len2 > 0)
      } else ()
      java.lang.System.arraycopy(a, cursor2, a, dest, len2)
      a(dest + len2) = tmp(cursor1)
    } else {
      if (len1 == 0) {
        throw new java.lang.IllegalArgumentException("Comparison method violates its general contract!")
      } else {
        if (ComparableTimSort.DEBUG) {
          assert(len2 == 0)
        } else ()
        if (ComparableTimSort.DEBUG) {
          assert(len1 > 1)
        } else ()
        java.lang.System.arraycopy(tmp, cursor1, a, dest, len1)
      }
    }
  }
  private def mergeHi(base1: scala.Int, len1$arg: scala.Int, base2: scala.Int, len2$arg: scala.Int): scala.Unit = {
    var len1: scala.Int = len1$arg
    var len2: scala.Int = len2$arg
    if (ComparableTimSort.DEBUG) {
      assert(((len1 > 0) && (len2 > 0)) && ((base1 + len1) == base2))
    } else ()
    val a: scala.Array[java.lang.Object] = this.a
    val tmp: scala.Array[java.lang.Object] = this.ensureCapacity(len2)
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
    var minGallop: scala.Int = this.minGallop
    while (true) {
      var count1: scala.Int = 0
      var count2: scala.Int = 0
      while ({ {
        if (ComparableTimSort.DEBUG) {
          assert((len1 > 0) && (len2 > 1))
        } else ()
        if (tmp(cursor2).asInstanceOf[java.lang.Comparable[?]].compareTo(a(cursor1)) < 0) {
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
        if (ComparableTimSort.DEBUG) {
          assert((len1 > 0) && (len2 > 1))
        } else ()
        count1 = len1 - ComparableTimSort.gallopRight(tmp(cursor2).asInstanceOf[java.lang.Comparable[?]].asInstanceOf[java.lang.Comparable[java.lang.Object]], a, base1, len1, len1 - 1)
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
        count2 = len2 - ComparableTimSort.gallopLeft(a(cursor1).asInstanceOf[java.lang.Comparable[?]].asInstanceOf[java.lang.Comparable[java.lang.Object]], tmp, 0, len2, len2 - 1)
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
      }; (count1 >= ComparableTimSort.MIN_GALLOP) | (count2 >= ComparableTimSort.MIN_GALLOP) }) ()
      if (minGallop < 0) {
        minGallop = 0
      } else ()
      minGallop = minGallop + 2
    }
    this.minGallop = if (minGallop < 1) 1 else minGallop
    if (len2 == 1) {
      if (ComparableTimSort.DEBUG) {
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
        if (ComparableTimSort.DEBUG) {
          assert(len1 == 0)
        } else ()
        if (ComparableTimSort.DEBUG) {
          assert(len2 > 0)
        } else ()
        java.lang.System.arraycopy(tmp, 0, a, dest - (len2 - 1), len2)
      }
    }
  }
  private def ensureCapacity(minCapacity: scala.Int): scala.Array[java.lang.Object] = {
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
      val newArray: scala.Array[java.lang.Object] = new scala.Array[java.lang.Object](newSize)
      this.tmp = newArray
    } else ()
    return this.tmp
  }
}
object ComparableTimSort {
  private final val MIN_MERGE: scala.Int = 32
  private final val MIN_GALLOP: scala.Int = 7
  private final val INITIAL_TMP_STORAGE_LENGTH: scala.Int = 256
  private final val DEBUG: scala.Boolean = false
  def sort(a: scala.Array[java.lang.Object]): scala.Unit = {
    ComparableTimSort.sort(a, 0, a.length)
  }
  def sort(a: scala.Array[java.lang.Object], lo$arg: scala.Int, hi: scala.Int): scala.Unit = {
    var lo: scala.Int = lo$arg
    ComparableTimSort.rangeCheck(a.length, lo, hi)
    var nRemaining: scala.Int = hi - lo
    if (nRemaining < 2) {
      return
    } else ()
    if (nRemaining < ComparableTimSort.MIN_MERGE) {
      val initRunLen: scala.Int = ComparableTimSort.countRunAndMakeAscending(a, lo, hi)
      ComparableTimSort.binarySort(a, lo, hi, lo + initRunLen)
      return
    } else ()
    val ts: ComparableTimSort = new ComparableTimSort(a)
    val minRun: scala.Int = ComparableTimSort.minRunLength(nRemaining)
    while ({ {
      var runLen: scala.Int = ComparableTimSort.countRunAndMakeAscending(a, lo, hi)
      if (runLen < minRun) {
        val force: scala.Int = if (nRemaining <= minRun) nRemaining else minRun
        ComparableTimSort.binarySort(a, lo, lo + force, lo + runLen)
        runLen = force
      } else ()
      ts.pushRun(lo, runLen)
      ts.mergeCollapse()
      lo = lo + runLen
      nRemaining = nRemaining - runLen
    }; nRemaining != 0 }) ()
    if (ComparableTimSort.DEBUG) {
      assert(lo == hi)
    } else ()
    ts.mergeForceCollapse()
    if (ComparableTimSort.DEBUG) {
      assert(ts.stackSize == 1)
    } else ()
  }
  private def binarySort(a: scala.Array[java.lang.Object], lo: scala.Int, hi: scala.Int, start$arg: scala.Int): scala.Unit = {
    var start: scala.Int = start$arg
    if (ComparableTimSort.DEBUG) {
      assert((lo <= start) && (start <= hi))
    } else ()
    if (start == lo) {
      start = start + 1
    } else ();
    { ; while (start < hi) { {
      val pivot: java.lang.Comparable[java.lang.Object] = a(start).asInstanceOf[java.lang.Comparable[?]].asInstanceOf[java.lang.Comparable[java.lang.Object]]
      var left: scala.Int = lo
      var right: scala.Int = start
      if (ComparableTimSort.DEBUG) {
        assert(left <= right)
      } else ()
      while (left < right) {
        val mid: scala.Int = (left + right) >>> 1
        if (pivot.compareTo(a(mid)) < 0) {
          right = mid
        } else {
          left = mid + 1
        }
      }
      if (ComparableTimSort.DEBUG) {
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
  private def countRunAndMakeAscending(a: scala.Array[java.lang.Object], lo: scala.Int, hi: scala.Int): scala.Int = {
    if (ComparableTimSort.DEBUG) {
      assert(lo < hi)
    } else ()
    var runHi: scala.Int = lo + 1
    if (runHi == hi) {
      return 1
    } else ()
    if (a({ runHi += 1; runHi }).asInstanceOf[java.lang.Comparable[?]].compareTo(a(lo)) < 0) {
      while ((runHi < hi) && (a(runHi).asInstanceOf[java.lang.Comparable[?]].compareTo(a(runHi - 1)) < 0)) {
        runHi = runHi + 1
      }
      ComparableTimSort.reverseRange(a, lo, runHi)
    } else {
      while ((runHi < hi) && (a(runHi).asInstanceOf[java.lang.Comparable[?]].compareTo(a(runHi - 1)) >= 0)) {
        runHi = runHi + 1
      }
    }
    return runHi - lo
  }
  private def reverseRange(a: scala.Array[java.lang.Object], lo: scala.Int, hi$arg: scala.Int): scala.Unit = {
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
    if (ComparableTimSort.DEBUG) {
      assert(n >= 0)
    } else ()
    var r: scala.Int = 0
    while (n >= ComparableTimSort.MIN_MERGE) {
      r = r | (n & 1)
      n = n >> 1
    }
    return n + r
  }
  private def gallopLeft(key: java.lang.Comparable[java.lang.Object], a: scala.Array[java.lang.Object], base: scala.Int, len: scala.Int, hint: scala.Int): scala.Int = {
    if (ComparableTimSort.DEBUG) {
      assert(((len > 0) && (hint >= 0)) && (hint < len))
    } else ()
    var lastOfs: scala.Int = 0
    var ofs: scala.Int = 1
    if (key.compareTo(a(base + hint)) > 0) {
      val maxOfs: scala.Int = len - hint
      while ((ofs < maxOfs) && (key.compareTo(a((base + hint) + ofs)) > 0)) {
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
      while ((ofs < maxOfs) && (key.compareTo(a((base + hint) - ofs)) <= 0)) {
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
    if (ComparableTimSort.DEBUG) {
      assert((((-1) <= lastOfs) && (lastOfs < ofs)) && (ofs <= len))
    } else ()
    lastOfs = lastOfs + 1
    while (lastOfs < ofs) {
      val m: scala.Int = lastOfs + ((ofs - lastOfs) >>> 1)
      if (key.compareTo(a(base + m)) > 0) {
        lastOfs = m + 1
      } else {
        ofs = m
      }
    }
    if (ComparableTimSort.DEBUG) {
      assert(lastOfs == ofs)
    } else ()
    return ofs
  }
  private def gallopRight(key: java.lang.Comparable[java.lang.Object], a: scala.Array[java.lang.Object], base: scala.Int, len: scala.Int, hint: scala.Int): scala.Int = {
    if (ComparableTimSort.DEBUG) {
      assert(((len > 0) && (hint >= 0)) && (hint < len))
    } else ()
    var ofs: scala.Int = 1
    var lastOfs: scala.Int = 0
    if (key.compareTo(a(base + hint)) < 0) {
      val maxOfs: scala.Int = hint + 1
      while ((ofs < maxOfs) && (key.compareTo(a((base + hint) - ofs)) < 0)) {
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
      while ((ofs < maxOfs) && (key.compareTo(a((base + hint) + ofs)) >= 0)) {
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
    if (ComparableTimSort.DEBUG) {
      assert((((-1) <= lastOfs) && (lastOfs < ofs)) && (ofs <= len))
    } else ()
    lastOfs = lastOfs + 1
    while (lastOfs < ofs) {
      val m: scala.Int = lastOfs + ((ofs - lastOfs) >>> 1)
      if (key.compareTo(a(base + m)) < 0) {
        ofs = m
      } else {
        lastOfs = m + 1
      }
    }
    if (ComparableTimSort.DEBUG) {
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