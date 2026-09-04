package balticporter.runtime

/** `java.util.EnumMap`, as Scala — a `mutable.Map` that iterates in ORDINAL order. */
final class JavaEnumMap[K <: java.lang.Enum[K], V] extends scala.collection.mutable.AbstractMap[K, V] {
  private given byOrdinal: Ordering[K] = Ordering.by((k: K) => k.ordinal)
  private val under = scala.collection.mutable.TreeMap.empty[K, V]

  /** java's `typeCheck` — the WRITER's reading of `isValidKey`. */
  private def requireKey(key: K): K = {
    if key == null then throw new NullPointerException("EnumMap does not permit a null key")
    key
  }

  /** …and java's `isValidKey` as the READERS use it: not a key this map can hold, so it is absent.
    * Named apart from [[requireKey]] because the two are the same question with two answers, and a
    * single helper would have to pick one. */
  private def validKey(key: K): Boolean = key != null

  def get(key: K): Option[V]                 = if validKey(key) then under.get(key) else scala.None
  def iterator: Iterator[(K, V)]             = under.iterator
  def addOne(kv: (K, V)): this.type          = { requireKey(kv._1); under.addOne(kv); this }
  // `remove(null)` is java's `null` return and NOT a throw — the map is not touched and the caller
  // is told nothing was there. `mutable.Map.remove` is derived from `get` + this, so it answers
  // `None` by construction.
  def subtractOne(key: K): this.type         = { if validKey(key) then under.subtractOne(key); this }
  override def knownSize: Int                = under.knownSize
  override def clear(): Unit                 = under.clear()
  override def contains(key: K): Boolean     = validKey(key) && under.contains(key)
}

object JavaEnumMap {
  /** the COPY constructor's target — `new EnumMap<>(m)`. */
  def from[K <: java.lang.Enum[K], V](it: scala.collection.IterableOnce[(K, V)]): JavaEnumMap[K, V] = {
    val m = new JavaEnumMap[K, V]
    m ++= it
    m
  }

  /** `new EnumMap<>(K.class)` — the class token java needs for its ordinal array and this
    * implementation does not. Taken and IGNORED rather than dropped at the call site, so the
    * emitted code still reads like the java it came from. */
  def ofType[K <: java.lang.Enum[K], V](@annotation.unused cls: Class[K]): JavaEnumMap[K, V] =
    new JavaEnumMap[K, V]
}
