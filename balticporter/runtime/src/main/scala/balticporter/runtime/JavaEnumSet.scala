package balticporter.runtime

/** `java.util.EnumSet`, as Scala — a `mutable.Set` that iterates in ORDINAL order.
  *
  * [[JavaEnumMap]]'s reasoning, one collection kind over, with one difference that decides the
  * shape: `EnumSet` has NO public constructor. Every java site is a static factory, so the port's
  * rewrites land on this companion rather than on a `new`, and the class token those factories take
  * is not decoration here — `allOf`, `range` and `complementOf` need the enum's CONSTANTS, which is
  * what `Class.getEnumConstants` is for and what java itself uses.
  */
final class JavaEnumSet[E <: java.lang.Enum[E]] extends scala.collection.mutable.AbstractSet[E]:
  private given byOrdinal: Ordering[E] = Ordering.by((e: E) => e.ordinal)
  private val under = scala.collection.mutable.TreeSet.empty[E]

  def contains(elem: E): Boolean      = under.contains(elem)
  def iterator: Iterator[E]           = under.iterator
  def addOne(elem: E): this.type      = { under.addOne(elem); this }
  def subtractOne(elem: E): this.type = { under.subtractOne(elem); this }
  override def knownSize: Int         = under.knownSize
  override def clear(): Unit          = under.clear()

object JavaEnumSet:
  /** `EnumSet.noneOf(K.class)` — empty, and the class token is what java needs and this does not. */
  def noneOf[E <: java.lang.Enum[E]](@annotation.unused cls: Class[E]): JavaEnumSet[E] =
    new JavaEnumSet[E]

  /** `EnumSet.allOf(K.class)` — every constant, in ordinal order. The token IS load-bearing. */
  def allOf[E <: java.lang.Enum[E]](cls: Class[E]): JavaEnumSet[E] =
    val s = new JavaEnumSet[E]
    cls.getEnumConstants.foreach(s.addOne)
    s

  /** `EnumSet.of(a, b, …)` — java has five fixed arities and a vararg; one repeated parameter
    * serves them all, because the arities exist only to avoid an array allocation. */
  def of[E <: java.lang.Enum[E]](elems: E*): JavaEnumSet[E] =
    val s = new JavaEnumSet[E]
    elems.foreach(s.addOne)
    s

  /** `EnumSet.copyOf(c)`. */
  def copyOf[E <: java.lang.Enum[E]](it: scala.collection.IterableOnce[E]): JavaEnumSet[E] =
    val s = new JavaEnumSet[E]
    s ++= it
    s

  /** `EnumSet.range(from, to)` — INCLUSIVE at both ends, as java's is, and an empty range where
    * `from > to` is java's `IllegalArgumentException` rather than a silently empty set. */
  def range[E <: java.lang.Enum[E]](from: E, to: E): JavaEnumSet[E] =
    if from.ordinal > to.ordinal then
      throw new IllegalArgumentException(s"$from > $to")
    val s = new JavaEnumSet[E]
    from.getDeclaringClass.getEnumConstants
      .filter(e => e.ordinal >= from.ordinal && e.ordinal <= to.ordinal)
      .foreach(s.addOne)
    s

  /** `EnumSet.complementOf(s)` — the constants NOT in `s`. Java reads the enum's identity off the
    * set itself, which is why java's own version throws on an EMPTY one: there is nothing to read
    * it from. Reproduced rather than softened. */
  def complementOf[E <: java.lang.Enum[E]](s: JavaEnumSet[E]): JavaEnumSet[E] =
    val head = s.headOption.getOrElse(
      throw new IllegalArgumentException("complementOf of an empty EnumSet: the enum type cannot be determined"))
    val out = new JavaEnumSet[E]
    head.getDeclaringClass.getEnumConstants.filterNot(s.contains).foreach(out.addOne)
    out
