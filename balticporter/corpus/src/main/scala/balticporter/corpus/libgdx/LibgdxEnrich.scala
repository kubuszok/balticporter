package balticporter.corpus.libgdx

import balticporter.corpus.lls.LlsEnrich
import balticporter.corpus.lls.LlsEnrich.{ArrayKind, MapKind, SetKind, spec}
import balticporter.transform.AddMembersTransform
import balticporter.transform.AddMembersTransform.MemberSpec

/** lls's added API on CORE's own collections (the ones lls does not carry), through lls's generators
  * — the ladder's "enrich" step (PROGRESS.md §13.29). The subclasses of lls's types declare the
  * factories themselves: the inherited-statics export would otherwise clash with scala's
  * constructor proxy (`E120`, three at L0). */
object LibgdxEnrich:

  private val arrays: List[ArrayKind] = List(
    ArrayKind("IntArray",     "lowlevel.util.IntArray",     "scala.Int",
      removeOne = "this.removeValue(value)", removeMany = "this.removeAll(other)"),
    ArrayKind("FloatArray",   "lowlevel.util.FloatArray",   "scala.Float",
      removeOne = "this.removeValue(value)", removeMany = "this.removeAll(other)"),
    ArrayKind("LongArray",    "lowlevel.util.LongArray",    "scala.Long",
      removeOne = "this.removeValue(value)", removeMany = "this.removeAll(other)"),
    ArrayKind("ShortArray",   "lowlevel.util.ShortArray",   "scala.Short",
      removeOne = "this.removeValue(value)", removeMany = "this.removeAll(other)"),
    ArrayKind("ByteArray",    "lowlevel.util.ByteArray",    "scala.Byte",
      removeOne = "this.removeValue(value)", removeMany = "this.removeAll(other)"),
    ArrayKind("CharArray",    "lowlevel.util.CharArray",    "scala.Char",
      removeOne = "this.removeValue(value)", removeMany = "this.removeAll(other)"),
    // BooleanArray declares no `contains`/`indexOf`/`removeValue` upstream: `-=` is refused, not invented.
    ArrayKind("BooleanArray", "lowlevel.util.BooleanArray", "scala.Boolean",
      removeMany = "this.removeAll(other)"),
  )

  /** the two object-VALUED maps wrap `get` in `Nullable`: their templates were written against lls's
    * nullable-returning API and need the nullability step first (a measured dependency, PROGRESS.md
    * §13.29 — 2 errors when added ahead of it). */
  private val nullableMaps: List[MapKind] = List(
    MapKind("IntMap", "scala.Int", "V", "lowlevel.Nullable[V]",
      tparams = "[V <: java.lang.Object]", self = "lowlevel.util.IntMap[V]",
      wrap = v => s"lowlevel.Nullable($v)", getOne = "V", removeOne = "remove"),
    MapKind("LongMap", "scala.Long", "V", "lowlevel.Nullable[V]",
      tparams = "[V <: java.lang.Object]", self = "lowlevel.util.LongMap[V]",
      wrap = v => s"lowlevel.Nullable($v)", getOne = "lowlevel.Nullable[V]", removeOne = "remove"),
  )

  private val maps: List[MapKind] = List(
    MapKind("ObjectIntMap", "K", "scala.Int", "scala.Int",
      tparams = "[K <: java.lang.Object]", self = "lowlevel.util.ObjectIntMap[K]"),
    MapKind("ObjectFloatMap", "K", "scala.Float", "scala.Float",
      tparams = "[K <: java.lang.Object]", self = "lowlevel.util.ObjectFloatMap[K]"),
    MapKind("ObjectLongMap", "K", "scala.Long", "scala.Long",
      tparams = "[K <: java.lang.Object]", self = "lowlevel.util.ObjectLongMap[K]"),
    MapKind("IntIntMap", "scala.Int", "scala.Int", "scala.Int", self = "lowlevel.util.IntIntMap"),
    MapKind("IntFloatMap", "scala.Int", "scala.Float", "scala.Float", self = "lowlevel.util.IntFloatMap"),
  )

  private val sets: List[SetKind] = List(
    SetKind("IntSet", "scala.Int", "lowlevel.util.IntSet", hasNext = "hasNext"),
  )

  /** core's subclasses of lls's types take the factories themselves (see the object's doc); with
    * the witness step on, an array-like factory supplies the `MkArray` clause. */
  private def subclassFactories(w: Boolean): List[(String, MemberSpec)] =
    val why = "lls factory on a subclass; also what keeps the inherited-statics export unambiguous (PROGRESS.md §13.29)"
    def arrayLike(owner: String, self: String, elem: String, tparams0: String, mk: String) =
      val tparams = if mk.isEmpty then tparams0 else "[T]"
      List(
        ("apply", 0, s"def apply$tparams()$mk: $self = new $self()"),
        ("apply", 1, s"def apply$tparams(capacity: scala.Int)$mk: $self = new $self(capacity)"),
        ("apply", 2, s"def apply$tparams(ordered: scala.Boolean, capacity: scala.Int)$mk: $self = new $self(ordered, capacity)"),
        ("from",  1, s"def from$tparams(values: scala.Array[$elem])$mk: $self = new $self(values)"),
      ).map((n, a, s) => spec(owner, n, a, s, why, static = true))
    def tableLike(owner: String, self: String, tparams: String) = List(
      ("apply", 0, s"def apply$tparams(): $self = new $self()"),
      ("apply", 1, s"def apply$tparams(initialCapacity: scala.Int): $self = new $self(initialCapacity)"),
      ("apply", 2, s"def apply$tparams(initialCapacity: scala.Int, loadFactor: scala.Float): $self = new $self(initialCapacity, loadFactor)"),
    ).map((n, a, s) => spec(owner, n, a, s, why, static = true))
    val amk = if w then "(using lowlevel.MkArray[T])" else ""
    arrayLike("SnapshotArray", "lowlevel.util.SnapshotArray[T]", "T", "[T <: java.lang.Object]", amk) ++
      arrayLike("DelayedRemovalArray", "lowlevel.util.DelayedRemovalArray[T]", "T", "[T <: java.lang.Object]", amk) ++
      tableLike("IdentityMap", "lowlevel.util.IdentityMap[K, V]", "[K <: java.lang.Object, V <: java.lang.Object]")

  /** @param w the witness step is on; @param n the nullability step is on (admits [[nullableMaps]]). */
  private def all(w: Boolean, n: Boolean): List[(String, MemberSpec)] =
    arrays.flatMap(LlsEnrich.arrayMembers) ++ maps.flatMap(LlsEnrich.mapMembers) ++
      (if n then nullableMaps.flatMap(LlsEnrich.mapMembers) else Nil) ++
      sets.flatMap(LlsEnrich.setMembers) ++ subclassFactories(w)

  def count: Int = all(false, true).size

  def transform(w: Boolean, n: Boolean): AddMembersTransform = new AddMembersTransform(LlsEnrich.build(all(w, n)))
