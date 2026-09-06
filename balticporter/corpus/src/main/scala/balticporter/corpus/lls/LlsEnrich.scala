package balticporter.corpus.lls

import balticporter.tir.Reason
import balticporter.transform.AddMembersTransform
import balticporter.transform.AddMembersTransform.MemberSpec

/** lls's ADDED API as a §1(c) VALUE: the members and factories the hand port put on the twelve
  * types it ported, generated from ONE template per KIND (array / map / set) and applied by
  * analogy to every sibling in the utilities family (`PROGRESS.md` §13.29). A member whose body
  * would need `MkArray` or reflection is not here — that is a different rung. */
object LlsEnrich:

  private val Pkg = "com.badlogic.gdx.utils"

  private[corpus] def spec(owner: String, name: String, arity: Int, src: String, why: String,
                   static: Boolean = false): (String, MemberSpec) =
    s"$Pkg.$owner" -> MemberSpec(name, arity, src,
      Reason.Configured("add-members", s"$Pkg.$owner#$name"), Some(why), static)

  // ---------------------------------------------------------------------------------------------
  // KIND 1 — array-like. `items`/`size`/`get`/`set`/`add` are the emitted surface every one of
  // libGDX's eight resizable arrays shares; the template is written once against it.
  // ---------------------------------------------------------------------------------------------

  /** @param owner upstream simple name @param self emitted type, applied @param elem element type
    * @param tparams type-parameter clause of the added members (empty for the primitive arrays)
    * @param removeOne `removeValue` at arity 1 exists @param removeMany `removeAll` at arity 1 */
  private[corpus] case class ArrayKind(owner: String, self: String, elem: String, tparams: String = "",
                               removeOne: String = "", removeMany: String = "",
                               /** the FACTORY's witness clause when the `witness` rung is on; the
                                 * emitted constructors take one and a factory must supply it. */
                               mk: String = "")

  private[corpus] def arrayMembers(k: ArrayKind): List[(String, MemberSpec)] =
    val E = k.elem
    val why = "lls collection API on the emitted array surface (PROGRESS.md §13.29)"
    val common = List(
      ("apply", 1,
        s"def apply(index: scala.Int): $E = this.get(index)"),
      ("update", 2,
        s"def update(index: scala.Int, value: $E): scala.Unit = this.set(index, value)"),
      ("nonEmpty", 0,
        "def nonEmpty: scala.Boolean = this.size != 0"),
      ("last", 0,
        s"def last: $E = { if (this.size == 0) { throw new java.lang.IndexOutOfBoundsException(\"Array is empty.\") } else () ; this.items(this.size - 1) }"),
      ("foreach", 1,
        s"def foreach(f: $E => scala.Unit): scala.Unit = { var i: scala.Int = 0; while (i < this.size) { f(this.items(i)); i += 1 } }"),
      ("indexWhere", 1,
        s"def indexWhere(p: $E => scala.Boolean): scala.Int = { var i: scala.Int = 0; var r: scala.Int = -1; while (i < this.size && r < 0) { if (p(this.items(i))) { r = i } else () ; i += 1 }; r }"),
      ("exists", 1,
        s"def exists(p: $E => scala.Boolean): scala.Boolean = this.indexWhere(p) >= 0"),
      ("forall", 1,
        s"def forall(p: $E => scala.Boolean): scala.Boolean = this.indexWhere((x: $E) => !p(x)) < 0"),
      ("find", 1,
        s"def find(p: $E => scala.Boolean): lowlevel.Nullable[$E] = { val i: scala.Int = this.indexWhere(p); if (i < 0) lowlevel.Nullable.empty[$E] else lowlevel.Nullable(this.items(i)) }"),
      ("count", 1,
        s"def count(p: $E => scala.Boolean): scala.Int = { var c: scala.Int = 0; var i: scala.Int = 0; while (i < this.size) { if (p(this.items(i))) { c += 1 } else () ; i += 1 }; c }"),
      ("$plus$eq", 1,
        s"@scala.annotation.targetName(\"plusEquals\") def +=(value: $E): scala.Unit = this.add(value)"),
    )
    val removes =
      (if k.removeOne.isEmpty then Nil else List(("$minus$eq", 1,
        s"@scala.annotation.targetName(\"minusEquals\") def -=(value: $E): scala.Unit = { ${k.removeOne}; () }"))) ++
      (if k.removeMany.isEmpty then Nil else List(("$minus$minus$eq", 1,
        s"@scala.annotation.targetName(\"minusMinusEquals\") def --=(other: ${k.self}): scala.Unit = { ${k.removeMany}; () }")))
    // java's own public constructors STAY; these are additions beside them (the maintainer keeps
    // java's shape, so no private constructor and no `MkArray` mint site).
    val factories = List(
      ("apply", 0,
        s"def apply${k.tparams}()${k.mk}: ${k.self} = new ${k.self}()"),
      ("apply", 1,
        s"def apply${k.tparams}(capacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(capacity)"),
      ("apply", 2,
        s"def apply${k.tparams}(ordered: scala.Boolean, capacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(ordered, capacity)"),
      ("from", 1,
        s"def from${k.tparams}(values: scala.Array[$E])${k.mk}: ${k.self} = new ${k.self}(values)"),
    )
    (common ++ removes).map((n, a, s) => spec(k.owner, n, a, s, why)) ++
      factories.map((n, a, s) => spec(k.owner, n, a, s, why, static = true))

  /** `Array` alone carries java's `identity` FLAG on nine members. lls spelled the two settings as
    * two names; both are ADDITIONS — java's flag-taking members stay (`PROGRESS.md` §13.29). */
  private def refArrayExtras: List[(String, MemberSpec)] =
    val S   = "lowlevel.util.DynamicArray[? <: T]"
    val why = "lls's flag-free / ByRef pair for java's `identity` argument (PROGRESS.md §13.29)"
    val pairs = List(
      ("contains",     1, (id: String) => s"(value: T): scala.Boolean = this.contains(lowlevel.Nullable(value), $id)"),
      ("containsAll",  1, (id: String) => s"(values: $S): scala.Boolean = this.containsAll(values, $id)"),
      ("containsAny",  1, (id: String) => s"(values: $S): scala.Boolean = this.containsAny(values, $id)"),
      ("indexOf",      1, (id: String) => s"(value: T): scala.Int = this.indexOf(lowlevel.Nullable(value), $id)"),
      ("lastIndexOf",  1, (id: String) => s"(value: T): scala.Int = this.lastIndexOf(lowlevel.Nullable(value), $id)"),
      ("removeValue",  1, (id: String) => s"(value: T): scala.Boolean = this.removeValue(lowlevel.Nullable(value), $id)"),
      ("removeAll",    1, (id: String) => s"(array: $S): scala.Boolean = this.removeAll(array, $id)"),
      ("replaceFirst", 2, (id: String) => s"(value: T, replacement: T): scala.Boolean = this.replaceFirst(lowlevel.Nullable(value), $id, replacement)"),
      ("replaceAll",   2, (id: String) => s"(value: T, replacement: T): scala.Int = this.replaceAll(lowlevel.Nullable(value), $id, lowlevel.Nullable(replacement))"),
    )
    pairs.flatMap { (name, arity, body) =>
      List(
        spec("Array", name, arity, s"def $name${body("false")}", why),
        spec("Array", name + "ByRef", arity, s"def ${name}ByRef${body("true")}", why),
      )
    } :+ spec("Array", "preserveOrder", 0,
      "def preserveOrder: scala.Boolean = this.ordered",
      "lls's name for java's `ordered` flag, added beside it (PROGRESS.md §13.29)")

  // ---------------------------------------------------------------------------------------------
  // KIND 2 — map-like. The three iterator accessors (`keys`/`values`/`entries`) are the emitted
  // surface; a SUBCLASS that overrides them (OrderedMap) inherits the right traversal order.
  // ---------------------------------------------------------------------------------------------

  /** @param entryValue the `Entry.value` type as emitted (`Nullable[V]` on an object-valued map)
    * @param wrap how a plain value reaches `put` @param getOne `get` at arity 1, if there is one
    * @param removeOne `remove`/`removeKey` at arity 1, if there is one */
  private[corpus] case class MapKind(owner: String, key: String, value: String, entryValue: String,
                             tparams: String = "", self: String = "", wrap: String => String = identity,
                             getOne: String = "", removeOne: String = "",
                             indexed: Boolean = false, capacityCtor: Boolean = false,
                             /** see [[ArrayKind.mk]]. */
                             mk: String = "")

  private[corpus] def mapMembers(k: MapKind): List[(String, MemberSpec)] =
    // three separate vals, not a tuple pattern: an UPPERCASE name on the left of a pattern
    // definition is a constant pattern, and `val (K, V, EV) = …` binds nothing.
    val K   = k.key
    val V   = k.value
    val EV  = k.entryValue
    val why = "lls collection API on the emitted map surface (PROGRESS.md §13.29)"
    // ONE traversal accessor for all three: `entries()` is the only iterator every emitted map
    // family implements as a `JavaIterator` (`Keys` on the int-keyed maps carries a FIELD instead),
    // and a subclass that overrides it — `OrderedMap` — supplies the right order for free.
    def walk(body: String) =
      if k.indexed then s"{ var i: scala.Int = 0; while (i < this.size) { $body; i += 1 } }"
      else s"{ val it = this.entries(); while (it.hasNext()) { val e = it.next(); $body } }"
    def at(what: String) = if k.indexed then s"this.get${what}At(i)" else s"e.${what.toLowerCase}"
    val core = List(
      ("nonEmpty", 0, "def nonEmpty: scala.Boolean = this.size != 0"),
      ("foreachKey", 1,
        s"def foreachKey(f: $K => scala.Unit): scala.Unit = ${walk(s"f(${at("Key")})")}"),
      ("foreachValue", 1,
        s"def foreachValue(f: $EV => scala.Unit): scala.Unit = ${walk(s"f(${at("Value")})")}"),
      ("foreachEntry", 1,
        s"def foreachEntry(f: ($K, $EV) => scala.Unit): scala.Unit = ${walk(s"f(${at("Key")}, ${at("Value")})")}"),
      ("update", 2,
        s"def update(key: $K, value: $V): scala.Unit = { this.put(key, ${k.wrap("value")}); () }"),
      ("$plus$eq", 1,
        s"@scala.annotation.targetName(\"plusEquals\") def +=(kv: ($K, $V)): scala.Unit = this.update(kv._1, kv._2)"),
    )
    val optional =
      (if k.getOne.isEmpty then Nil
       else List(("apply", 1, s"def apply(key: $K): ${k.getOne} = this.get(key)"))) ++
      (if k.removeOne.isEmpty then Nil
       else List(("$minus$eq", 1,
         s"@scala.annotation.targetName(\"minusEquals\") def -=(key: $K): scala.Unit = { this.${k.removeOne}(key); () }")))
    val factories =
      if k.self.isEmpty then Nil
      else if k.capacityCtor then List(
        ("apply", 0, s"def apply${k.tparams}()${k.mk}: ${k.self} = new ${k.self}()"),
        ("apply", 1, s"def apply${k.tparams}(capacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(capacity)"),
        ("apply", 2, s"def apply${k.tparams}(ordered: scala.Boolean, capacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(ordered, capacity)"),
      )
      else List(
        ("apply", 0, s"def apply${k.tparams}()${k.mk}: ${k.self} = new ${k.self}()"),
        ("apply", 1, s"def apply${k.tparams}(initialCapacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(initialCapacity)"),
        ("apply", 2, s"def apply${k.tparams}(initialCapacity: scala.Int, loadFactor: scala.Float)${k.mk}: ${k.self} = new ${k.self}(initialCapacity, loadFactor)"),
      )
    (core ++ optional).map((n, a, s) => spec(k.owner, n, a, s, why)) ++
      factories.map((n, a, s) => spec(k.owner, n, a, s, why, static = true))

  // ---------------------------------------------------------------------------------------------
  // KIND 3 — set-like. `iterator()` is the only traversal the emitted surface offers, and
  // `OrderedSet` overrides it, so the inherited members follow the subclass's order.
  // ---------------------------------------------------------------------------------------------

  /** @param hasNext `hasNext()` on the object sets, a `hasNext` FIELD on `IntSet` (the emitter
    * renames the field only where a method of the same name exists). */
  private[corpus] case class SetKind(owner: String, elem: String, self: String, tparams: String = "",
                             hasNext: String = "hasNext()", mk: String = "")

  private[corpus] def setMembers(k: SetKind): List[(String, MemberSpec)] =
    val E   = k.elem
    val hn  = k.hasNext
    val why = "lls collection API on the emitted set surface (PROGRESS.md §13.29)"
    val core = List(
      ("nonEmpty", 0, "def nonEmpty: scala.Boolean = this.size != 0"),
      ("foreach", 1,
        s"def foreach(f: $E => scala.Unit): scala.Unit = { val it = this.iterator(); while (it.$hn) { f(it.next()) } }"),
      ("exists", 1,
        s"def exists(p: $E => scala.Boolean): scala.Boolean = { var r: scala.Boolean = false; val it = this.iterator(); while (it.$hn && !r) { if (p(it.next())) { r = true } else () }; r }"),
      ("forall", 1,
        s"def forall(p: $E => scala.Boolean): scala.Boolean = { var r: scala.Boolean = true; val it = this.iterator(); while (it.$hn && r) { if (!p(it.next())) { r = false } else () }; r }"),
      ("count", 1,
        s"def count(p: $E => scala.Boolean): scala.Int = { var c: scala.Int = 0; val it = this.iterator(); while (it.$hn) { if (p(it.next())) { c += 1 } else () }; c }"),
      ("$plus$eq", 1,
        s"@scala.annotation.targetName(\"plusEquals\") def +=(key: $E): scala.Unit = { this.add(key); () }"),
      ("$minus$eq", 1,
        s"@scala.annotation.targetName(\"minusEquals\") def -=(key: $E): scala.Unit = { this.remove(key); () }"),
    )
    val factories = List(
      ("apply", 0, s"def apply${k.tparams}()${k.mk}: ${k.self} = new ${k.self}()"),
      ("apply", 1, s"def apply${k.tparams}(initialCapacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(initialCapacity)"),
      ("apply", 2, s"def apply${k.tparams}(initialCapacity: scala.Int, loadFactor: scala.Float)${k.mk}: ${k.self} = new ${k.self}(initialCapacity, loadFactor)"),
    )
    core.map((n, a, s) => spec(k.owner, n, a, s, why)) ++
      factories.map((n, a, s) => spec(k.owner, n, a, s, why, static = true))

  // ---------------------------------------------------------------------------------------------
  // The population. Only the ROOT of each override component is enriched: `OrderedMap`,
  // `OrderedMap` and `OrderedSet` INHERIT the members
  // (adding them there would owe an `override` the mechanism cannot spell).
  // ---------------------------------------------------------------------------------------------

  private def arrays(w: Boolean): List[ArrayKind] = List(
    // `Array` is a WITNESS SUBJECT: with the rung on its element type loses java's implicit
    // `<: java.lang.Object` bound and its constructors take the type class, so every factory
    // written here must lose the bound and supply the clause too (PROGRESS.md §13.29).
    ArrayKind("Array", "lowlevel.util.DynamicArray[T]", "T", if w then "[T]" else "[T <: java.lang.Object]",
      removeOne  = "this.removeValue(lowlevel.Nullable(value), false)",
      removeMany = "this.removeAll(other, false)",
      mk         = if w then "(using lowlevel.MkArray[T])" else ""),
  )

  private def maps(w: Boolean): List[MapKind] = List(
    MapKind("ObjectMap", "K", "V", "lowlevel.Nullable[V]",
      tparams = "[K <: java.lang.Object, V <: java.lang.Object]",
      self = "lowlevel.util.ObjectMap[K, V]", wrap = v => s"lowlevel.Nullable($v)",
      getOne = "lowlevel.Nullable[V]", removeOne = "remove"),
    MapKind("ArrayMap", "K", "V", "V",
      tparams = if w then "[K, V]" else "[K <: java.lang.Object, V <: java.lang.Object]",
      self = "lowlevel.util.ArrayMap[K, V]", getOne = "lowlevel.Nullable[V]",
      removeOne = "removeKey", indexed = true, capacityCtor = true,
      mk = if w then "(using lowlevel.MkArray[K], lowlevel.MkArray[V])" else ""),
  )

  private val sets: List[SetKind] = List(
    SetKind("ObjectSet", "T", "lowlevel.util.ObjectSet[T]", "[T <: java.lang.Object]"),
  )

  /** The two SUBCLASSES get the factories too — and must. A companion factory is a static, so the
    * `export Parent.*` that reproduces java's static inheritance (`JS-C3`) delivers the parent's
    * into the subclass, where scala's own CONSTRUCTOR PROXY `apply` is a second definition with
    * matching parameter types (`E120`). Declaring them here excludes the parent's and suppresses
    * the proxy, and the factory answers with the SUBCLASS's type, which is what a caller wants. */
  private def subclassFactories(): List[(String, MemberSpec)] =
    val why = "lls factory on a subclass; also what keeps the inherited-statics export unambiguous (PROGRESS.md §13.29)"
    def tableLike(owner: String, self: String, tparams: String) = List(
      ("apply", 0, s"def apply$tparams(): $self = new $self()"),
      ("apply", 1, s"def apply$tparams(initialCapacity: scala.Int): $self = new $self(initialCapacity)"),
      ("apply", 2, s"def apply$tparams(initialCapacity: scala.Int, loadFactor: scala.Float): $self = new $self(initialCapacity, loadFactor)"),
    ).map((n, a, s) => spec(owner, n, a, s, why, static = true))
    tableLike("OrderedMap", "lowlevel.util.OrderedMap[K, V]", "[K <: java.lang.Object, V <: java.lang.Object]") ++
      tableLike("OrderedSet", "lowlevel.util.OrderedSet[T]", "[T <: java.lang.Object]")

  /** ArrayMap's own flag-free overloads — the same shape as `Array`'s, on the two members that
    * carry java's `identity` argument here. */
  private val arrayMapExtras: List[(String, MemberSpec)] = List(
    spec("ArrayMap", "containsValue", 1,
      "def containsValue(value: V): scala.Boolean = this.containsValue(value, false)",
      "lls's flag-free spelling of java's identity argument (PROGRESS.md §13.29)"),
    spec("ArrayMap", "indexOfValue", 1,
      "def indexOfValue(value: V): scala.Int = this.indexOfValue(value, false)",
      "lls's flag-free spelling of java's identity argument (PROGRESS.md §13.29)"),
  )

  private def all(w: Boolean): List[(String, MemberSpec)] =
    arrays(w).flatMap(arrayMembers) ++ refArrayExtras ++
      maps(w).flatMap(mapMembers) ++ arrayMapExtras ++ sets.flatMap(setMembers) ++
      subclassFactories()

  /** owner FQN -> specs, the shape `AddMembersTransform` takes — for any port's table (the
    * ladder's core collections use the same generators, `LibgdxEnrich`). */
  def build(pairs: List[(String, MemberSpec)]): Map[String, List[MemberSpec]] =
    pairs.groupBy(_._1).map((owner, ms) => owner -> ms.map(_._2)).toMap

  /** owner FQN -> specs, the shape `AddMembersTransform` takes. @param w the `witness` rung is on. */
  def members(w: Boolean): Map[String, List[MemberSpec]] = build(all(w))

  /** how many members this rung adds, for the lane's report. */
  def count: Int = all(false).size

  def transform(w: Boolean = false): AddMembersTransform = new AddMembersTransform(members(w))
