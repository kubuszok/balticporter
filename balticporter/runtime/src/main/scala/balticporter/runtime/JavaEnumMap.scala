package balticporter.runtime

/** `java.util.EnumMap`, as Scala — a `mutable.Map` that iterates in ORDINAL order.
  *
  * ==Why a shim and not a mapping==
  * The platform survey's other rows map onto a stdlib type because one exists with the same
  * meaning. This one has none: `EnumMap` is absent from BOTH non-JVM backends, so keeping the JDK
  * type is a link error there — and every stdlib map that would replace it reproduces the
  * AVAILABILITY and not the GUARANTEE. `java.util.EnumMap` is documented to iterate in the natural
  * order of its keys (the ordinal order in which the constants are declared), and a `HashMap` does
  * not, a `LinkedHashMap` iterates in INSERTION order, and the difference between the two is a
  * silent reordering of whatever the library was iterating for. That is `CLAUDE.md` §4.4's defect
  * class arriving through a type mapping, and it is catalog row `JS-C42`.
  *
  * ==How the order is kept==
  * A `mutable.TreeMap` ordered by `ordinal`. Java's own implementation is an ordinal-indexed ARRAY,
  * which is faster and needs the key's `Class` — the token its constructor takes and which a
  * `new EnumMap<>(K.class)` call site is the only place to get. Ordering by ordinal is observably
  * the same for every operation this type has: iteration, `head`, `last`, `keySet`, `toString`.
  *
  * ==Null, which is NOT one rule but two==
  * `java.util.EnumMap` has exactly ONE gate — `isValidKey` — and it is read in two different ways.
  * The WRITER (`put`) runs `typeCheck` and throws a `NullPointerException`; every READER
  * (`get`, `containsKey`, `remove`) uses it as a FILTER and answers absent — `null`, `false`,
  * `null`, with the map untouched. So a shim that throws at a query is LOUDER than java, and that
  * is the direction that turns a caller's null-tolerant lookup into an exception three frames up.
  *
  * Both halves are EXPLICIT rather than left to the ordering, and for one reason: a comparator is
  * only consulted when there is something to compare. Left implicit, the FIRST insertion into an
  * empty map would have accepted a null and every later one rejected it, and every QUERY would
  * answer absent on an empty map and throw out of `Ordering.by` on a populated one — a divergence
  * that shows up as an ordering failure or an NPE somewhere else instead of at the call that made
  * it. Null VALUES are permitted, as java's are.
  */
final class JavaEnumMap[K <: java.lang.Enum[K], V] extends scala.collection.mutable.AbstractMap[K, V]:
  private given byOrdinal: Ordering[K] = Ordering.by((k: K) => k.ordinal)
  private val under = scala.collection.mutable.TreeMap.empty[K, V]

  /** java's `typeCheck` — the WRITER's reading of `isValidKey`. */
  private def requireKey(key: K): K =
    if key == null then throw new NullPointerException("EnumMap does not permit a null key")
    key

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

object JavaEnumMap:
  /** the COPY constructor's target — `new EnumMap<>(m)`. */
  def from[K <: java.lang.Enum[K], V](it: scala.collection.IterableOnce[(K, V)]): JavaEnumMap[K, V] =
    val m = new JavaEnumMap[K, V]
    m ++= it
    m

  /** `new EnumMap<>(K.class)` — the class token java needs for its ordinal array and this
    * implementation does not. Taken and IGNORED rather than dropped at the call site, so the
    * emitted code still reads like the java it came from. */
  def ofType[K <: java.lang.Enum[K], V](@annotation.unused cls: Class[K]): JavaEnumMap[K, V] =
    new JavaEnumMap[K, V]
