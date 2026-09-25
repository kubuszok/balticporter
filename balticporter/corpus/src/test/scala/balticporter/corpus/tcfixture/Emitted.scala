package balticporter.corpus.tcfixture

class KeptPools {
  private final val cache: java.util.HashMap[java.lang.Class[?], java.lang.Object] = new java.util.HashMap[java.lang.Class[?], java.lang.Object]()
  def create[T <: java.lang.Object](`type`: java.lang.Class[T])(using balticporter.corpus.tcfixture.Factory[T]): T = {
    {
      return scala.Predef.summon[balticporter.corpus.tcfixture.Factory[T]].create().asInstanceOf[T]
    }
  }
  def shared[T <: java.lang.Object](`type`: java.lang.Class[T])(using balticporter.corpus.tcfixture.Factory[T]): T = {
    var found: java.lang.Object = this.cache.get(`type`)
    if (found == null) {
      found = this.create(`type`)
      this.cache.put(`type`, found)
    } else ()
    return found.asInstanceOf[T]
  }
}

class Pools {
  private final val cache: java.util.HashMap[java.lang.Class[?], java.lang.Object] = new java.util.HashMap[java.lang.Class[?], java.lang.Object]()
  def create[T <: java.lang.Object](using balticporter.corpus.tcfixture.Factory[T], scala.reflect.ClassTag[T]): T = {
    {
      return scala.Predef.summon[balticporter.corpus.tcfixture.Factory[T]].create().asInstanceOf[T]
    }
  }
  def shared[T <: java.lang.Object](using balticporter.corpus.tcfixture.Factory[T], scala.reflect.ClassTag[T]): T = {
    val `type`: java.lang.Class[T] = scala.Predef.summon[scala.reflect.ClassTag[T]].runtimeClass.asInstanceOf[java.lang.Class[T]]
    var found: java.lang.Object = this.cache.get(`type`)
    if (found == null) {
      found = this.create[T]
      this.cache.put(`type`, found)
    } else ()
    return found.asInstanceOf[T]
  }
  def fresh[T <: java.lang.Object](`type`: java.lang.Class[T]): T = {
    return `type`.getDeclaredConstructor().newInstance().asInstanceOf[T]
  }
  def size(): scala.Int = {
    return this.cache.size()
  }
}

class User {
  def run(p: balticporter.corpus.tcfixture.Pools, k: balticporter.corpus.tcfixture.KeptPools, w: java.lang.Class[balticporter.corpus.tcfixture.Widget]): scala.Array[java.lang.Object] = {
    val a: balticporter.corpus.tcfixture.Widget = p.shared[balticporter.corpus.tcfixture.Widget]
    val b: balticporter.corpus.tcfixture.Widget = p.shared[balticporter.corpus.tcfixture.Widget]
    val c: balticporter.corpus.tcfixture.Widget = p.create[balticporter.corpus.tcfixture.Widget]
    val d: balticporter.corpus.tcfixture.Widget = k.shared(classOf[balticporter.corpus.tcfixture.Widget])
    val e: balticporter.corpus.tcfixture.Widget = k.create(w)
    val f: balticporter.corpus.tcfixture.Widget = p.fresh(w)
    return scala.Array[java.lang.Object](a, b, c, d, e, f)
  }
}

class Widget
