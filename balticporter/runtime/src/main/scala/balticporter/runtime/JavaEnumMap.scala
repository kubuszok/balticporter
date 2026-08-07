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
  * Null KEYS throw, as java's do, and the check is EXPLICIT rather than left to the ordering: a
  * comparator is only consulted when there is something to compare, so the FIRST insertion into an
  * empty map would have accepted a null and every later one rejected it — a divergence that shows
  * up as an ordering failure three operations away instead of as java's `NullPointerException` at
  * the call that made it. Null VALUES are permitted, as java's are.
  */
final class JavaEnumMap[K <: java.lang.Enum[K], V] extends scala.collection.mutable.AbstractMap[K, V]:
  private given byOrdinal: Ordering[K] = Ordering.by((k: K) => k.ordinal)
  private val under = scala.collection.mutable.TreeMap.empty[K, V]

  /** java's own contract: a null key is a `NullPointerException` at every operation that takes one.
    * `get`/`contains` are query-only and java allows a null there (it answers absent), which is why
    * only the two MUTATORS check. */
  private def requireKey(key: K): K =
    if key == null then throw new NullPointerException("EnumMap does not permit a null key")
    key

  def get(key: K): Option[V]                 = under.get(key)
  def iterator: Iterator[(K, V)]             = under.iterator
  def addOne(kv: (K, V)): this.type          = { requireKey(kv._1); under.addOne(kv); this }
  def subtractOne(key: K): this.type         = { requireKey(key); under.subtractOne(key); this }
  override def knownSize: Int                = under.knownSize
  override def clear(): Unit                 = under.clear()
  override def contains(key: K): Boolean     = under.contains(key)

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
