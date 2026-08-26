package balticporter.runtime

/** `java.util.Collections`' statics, and the `java.util.Map.Entry` statics that follow from mapping
  * `Map.Entry` to `Tuple2` — as Scala, with java's own semantics.
  *
  * ==Why these need a home at all==
  * A static utility has no receiver, so nothing in `CollectionsTransform` that keys on the
  * receiver's collection kind can see it, and the call reaches the compiler VERBATIM against the
  * real JDK class — where the argument has been retyped and no longer fits. `Collections.sort` is
  * the shape: `Required: java.util.List[T]` against a `Buffer` the port itself produced.
  *
  * ==Why an object and not more methods on [[JavaCollection]]==
  * Because these are not about `JavaCollection`. Their arguments are ordinary scala collections;
  * they exist because a JDK *utility class* has no counterpart, not because a JDK *collection type*
  * has the wrong shape. Naming them for what they mirror is what makes the next one obvious to add.
  *
  * ==What is deliberately NOT here==
  * Nothing whose java semantics this object would have to GUESS. The bar is stated on each member
  * that came close: `asList` refuses the array-aliasing form outright rather than copying it, and
  * the `unmodifiable*` family below is offered only because the runtime can express java's answer
  * exactly — a read-only VIEW — which the STDLIB cannot. Where a copy or the identity is the only
  * available shape, neither is offered: both compile and both are wrong, and an unmapped static
  * fails to compile under the JDK's own name, which is the honest outcome (ENGINE-LIMITS M6).
  */
object JavaCollections {

  /** `java.util.Collections.sort(list, cmp)` — IN PLACE, as java's is.
    *
    * `sortInPlace` and not `sorted`: java mutates the argument and returns nothing, and every caller
    * reads the list afterwards through the same reference. A sorted COPY would compile, return the
    * right answer to nobody, and leave the original in its original order — a CLAUDE.md §4.4 defect
    * with no compile error and no changed count. */
  def sort[A](xs: scala.collection.mutable.Buffer[A], cmp: java.util.Comparator[? >: A]): Unit =
    inPlace(xs, xs.toList.sortWith((a, b) => cmp.compare(a, b) < 0))

  /** `java.util.Collections.sort(list)` — the natural-ordering overload, which java resolves through
    * `Comparable`. Scala needs an `Ordering`, and there is none for an arbitrary `A`; taking the
    * `Comparable` bound explicitly is what java's own signature says.
    *
    * The bound is java's OWN — `<T extends Comparable<? super T>>`, not `Comparable<T>` — and the
    * difference is not cosmetic. `Comparable<Object>` satisfies java's bound (`? super
    * Comparable<Object>` admits `Object`) and does NOT satisfy `Comparable[A]`, so a `List<Comparable<Object>>`
    * — which is exactly what a library that sorts heterogeneous values declares — read
    * `Found: Buffer[Comparable[Object]] / Required: Buffer[A]`. Transcribing a JDK signature means
    * transcribing its wildcards. */
  def sortNatural[A <: Comparable[? >: A]](xs: scala.collection.mutable.Buffer[A]): Unit =
    inPlace(xs, xs.toList.sortWith((a, b) => a.compareTo(b) < 0))

  /** `java.util.Collections.shuffle(list, rnd)` — JAVA'S ALGORITHM, not an equivalent one.
    *
    * The loop below is `Collections.shuffle`'s, verbatim: walk down from the end, swapping each
    * position with `rnd.nextInt(i)`. Any other correct shuffle consumes the `Random` differently and
    * therefore produces a DIFFERENT permutation from the same seed — and a seeded shuffle is only
    * ever written because the caller wants a reproducible one. simple-graphs' `GraphTest` seeds
    * `new Random(123)` and asserts on what follows; a "correct" shuffle that permutes differently
    * would turn a deterministic test into a coin flip, with nothing in the compile to show for it. */
  def shuffle[A](xs: scala.collection.mutable.Buffer[A], rnd: java.util.Random): Unit = {
    var i = xs.size
    while i > 1 do {
      val j = rnd.nextInt(i)
      val t = xs(i - 1); xs(i - 1) = xs(j); xs(j) = t
      i -= 1
    }
  }

  /** `java.util.Arrays.asList(a, b, c)` — the ELEMENT form only. Java's `asList` has two shapes
    * with different semantics, and only one of them may ever reach this method:
    *
    *   - **Elements listed at the call site** (`asList(1, 2, 3)`): java packs them into a fresh
    *     array nobody else can reach, so the only observable divergence here is that java's list
    *     is FIXED-SIZE — `set` works, `add`/`remove` throw — and this returns an ordinary
    *     `Buffer`. There is no scala type that is both a `Buffer` (which is what `java.util.List`
    *     maps to, so it is what the declared slot demands) and fixed-size. That divergence is
    *     permissive: code java would have rejected now runs, and that direction cannot turn a
    *     correct program into an incorrect one — the same trade already recorded for `Buffer`
    *     adding an ordering guarantee `Collection` does not make.
    *   - **A caller-held array passed whole** (`asList(arr)`): java returns a LIVE VIEW — `set`
    *     writes through to `arr`, and writes to `arr` are visible through the list. A copy here
    *     would compile and silently detach every aliased write, which is exactly a §4.4 defect.
    *     This method MUST NOT receive that form, and now cannot: `CollectionsTransform.asListArgs`
    *     REFUSES the rewrite for a single array-typed argument, so the emitted text keeps the JDK
    *     name and fails to compile as `Found: java.util.List[Array[Object]] / Required:
    *     Buffer[String]` — an untranslated call rather than a broken helper.
    *
    * Note this is the one rewritten static declared with a SCALA vararg. The engine renders a java
    * `T...` parameter as `Array[T]` and materialises the pack at the call site, so `asListArgs`
    * opens that pack back into separate arguments before it reaches here; a change to this
    * signature has to change that with it. */
  def asList[A](xs: A*): scala.collection.mutable.Buffer[A] =
    scala.collection.mutable.ArrayBuffer.from(xs)

  /** `java.util.Arrays.asList(T[] a)` — the OTHER shape behind that syntax, and the one [[asList]]
    * above must never receive: a LIVE, FIXED-SIZE view of the caller's array.
    *
    * Java's contract, reproduced exactly rather than approximated:
    *
    *   - **reads go through to the array**, so a write to `arr` after the call is visible in the
    *     list;
    *   - **`set(i, v)` writes THROUGH**, so a write through the list is visible in `arr`. That is
    *     the half a copy silently loses, which is why `ENGINE-LIMITS.md` K6.5 refused a copying
    *     rewrite rather than shipping one: a detached alias is CLAUDE.md §4.4 exactly — valid
    *     Scala, no error, no moved count, and every aliased write gone;
    *   - **the size is FIXED.** `add`, `remove`, `clear`, `insert` and `prepend` throw
    *     `UnsupportedOperationException`, which is what `Arrays$ArrayList` does — it extends
    *     `AbstractList` and overrides `set` but nothing that resizes. So this is not a restriction
    *     the port invented; it is the exception java throws, at the call java throws it at.
    *
    * The element type is the ARRAY's, which is what java's own inference saw: `Arrays.asList(arr)`
    * on an `Insertion[]` is a `List<Insertion>`. The rewrite therefore has to hand this method the
    * argument BENEATH the erasure coercion the frontend synthesised for the JDK's `Object[]`
    * formal — see `CollectionsTransform.asListViewArg`, which is `arrayArg`'s rule at a second
    * site.
    *
    * CLAUDE.md §4.5 is not violated here for the same reason [[FrozenBuffer]] does not violate it:
    * nothing in a port ever EXTENDS this class, so there is no second java interface to satisfy and
    * no ported member for a collection trait's inherited names to collide with. */
  def asListView[A](arr: Array[A]): scala.collection.mutable.Buffer[A] = {
    // …and it FAILS FAST, because java's does. `Arrays.asList(T[])` is `new ArrayList<>(a)`, whose
    // constructor is `a = Objects.requireNonNull(array)`, so a null array is an NPE AT THE CALL and
    // the caller never holds anything. Constructed lazily the view throws too — but at the first
    // READ, an arbitrary distance away, in whichever member happened to touch it first. Same
    // exception, different stack, different member, and a correlation that anchors the failure on
    // the wrong frame. A one-line difference in WHEN is the whole of what translating a fail-fast
    // contract means.
    if arr == null then
      throw new NullPointerException(
        "java.util.Arrays.asList(T[]): the array is null. Java's own constructor calls " +
          "Objects.requireNonNull, so this throws at the call rather than at the first read")
    new ArrayViewBuffer[A](arr)
  }

  /** `java.util.stream.Stream.noneMatch(Predicate)` — the one short-circuiting terminal with no
    * scala namesake.
    *
    * `anyMatch` and `allMatch` are `exists` and `forall` exactly (same result, same laziness, same
    * answer on an empty source), so the collapse emits those as plain members and nothing lives
    * here for them. `noneMatch` is `!exists`, and a NEGATION is a term the collapse would have to
    * synthesise a `unary_!` symbol for — three lines of runtime against a minted operator for one
    * call shape. Java's own answer on an empty stream is `true`, which `!exists` gives. */
  def noneMatch[A](xs: scala.collection.Iterable[A], p: A => Boolean): Boolean = !xs.exists(p)

  /** `java.util.Collection.addAll(Collection<?>)` — java's read off an UNBOUNDED WILDCARD.
    *
    * `List<?>` in java means `List<? extends Object>`: the unbounded wildcard's implicit upper
    * bound is `Object`, so every element read off one IS an `Object` and `list.addAll(valueList)`
    * needs no cast. Scala's `?` is bounded by `Any`, which is strictly wider — `Buffer[?]` is an
    * `IterableOnce[Any]`, and `dst ++= src` on a `Buffer[Object]` reads
    * `Required: IterableOnce[Object]`.
    *
    * The cast here is java's ERASURE, stated: at run time `E` is erased, the source's elements are
    * whatever java put in them, and `asInstanceOf[E]` on an erased parameter is a no-op — so this
    * throws nothing java would not, and adds nothing java's own unchecked `addAll` does not.
    *
    * The RESULT is java's too: `addAll` returns whether the collection changed, and code branches
    * on it. `++=` returns the buffer.
    *
    * `IterableOnce[?]` rather than `Buffer[?]` on the source so the one signature serves every
    * java collection the retyping produces — a `Set`, a `Buffer` and a map's entry view are all
    * `IterableOnce`, and java's `addAll` takes a `Collection` without caring which.
    *
    * ==WIDENED off `mutable.Buffer`, on both parameters, and see the cluster comment below==
    * The DESTINATION takes the receiver contract `removeAll`/`retainAll` share, because a class that
    * DEFINES a set inherits this same `addAll` and calls it through `super` — `mutable.Buffer` is
    * one of the two targets such a class is re-parented onto, not the shape of the member. The
    * SOURCE takes `containsAll`'s union for that call's own reason: the argument of an inherited
    * bulk operation is the java-shaped shim as often as it is a scala collection.
    *
    * The size comparison stays java's answer at both targets. Java's default returns whether any
    * `add` reported a change; on a `Buffer` every `add` does and the size always moves, and on a
    * `Set` `add` reports exactly when the element was absent, which is exactly when the size moves.
    * The two agree cell for cell, which is why one test serves both. */
  def addAll[E](dst: scala.collection.mutable.Iterable[E] & scala.collection.mutable.Growable[E]
                  & scala.collection.mutable.Shrinkable[E],
                src: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = {
    val before = dst.size
    dst ++= elementsOf(src).map(_.asInstanceOf[E])
    dst.size != before
  }

  /** `java.util.List.addAll(int index, Collection c)` — the POSITIONAL sibling of [[addAll]], which
    * inserts rather than appends and which scala spells `insertAll`.
    *
    * It is a helper and not a bare `dst.insertAll(i, src)` for the two reasons the one-argument form
    * is one: java returns a `boolean` callers branch on, where `insertAll` returns `Unit`; and the
    * SOURCE is `containsAll`'s union, because the argument of an inherited bulk operation is the
    * java-shaped shim as often as it is a scala collection.
    *
    * ==Why the ARITY is worth an arm of its own==
    * Left to the one-argument `addAll` arm, `list.addAll(0, c)` matched no rewrite and reached
    * scalac as `buf.addAll(0, c)` against `Growable.addAll(IterableOnce)` — which scala ACCEPTS by
    * auto-tupling, so the two java arguments became one `(Int, Collection)` pair. At this port's
    * element type that is a compile error; at an element type of `Any` or a tuple it is a program
    * that appends a pair where java inserted a collection, with a green compile and no count moving
    * (`CLAUDE.md` §4.4's defect class, met at an arity). */
  def insertAll[E](dst: scala.collection.mutable.Buffer[E], index: Int,
                   src: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = {
    val before = dst.size
    dst.insertAll(index, elementsOf(src).map(_.asInstanceOf[E]))
    dst.size != before
  }

  /** `java.util.Collection.remove(Object)` — removal BY VALUE, which scala's `Buffer` does not have.
    *
    * Not `Collections`', and deliberately here anyway: like every other member of this object it
    * exists because a java call has no scala counterpart with the same MEANING, and it is a
    * receiver-first function for the same reason `sort` is — `Buffer` cannot be extended with a
    * member that would clash with the `remove(Int)` it already declares.
    *
    * Two things are java's and not scala's, and both are the reason this is not `xs -= o`:
    *
    *   - **the RESULT.** Java returns whether anything was removed, and code branches on it
    *     (`if (list.remove(x)) …`). `-=` returns the buffer.
    *   - **the explicit NULL arm.** Java's `ArrayList.remove(Object o)` has one, and it is what
    *     makes `remove(null)` remove a null element rather than throw.
    *
    * The DIRECTION of the equality is java's too — `o.equals(element)`, the PROBE's `equals`, not
    * the element's — and it must be preserved, but it is NOT one of the reasons this is a helper.
    * That was claimed here and is wrong: `SeqOps.indexOf(elem)` is `indexWhere(elem == _)`, so
    * scala's own `indexOf` (and `-=` through it) already asks the probe. MEASURED in
    * `JavaCollectionsSpec`, which pins both the direction and that fact — because a hand-written
    * `indexWhere(_ == o)` WOULD diverge, silently, for any asymmetric `equals` (a subclass that
    * narrows it, `java.sql.Timestamp` against `java.util.Date`).
    *
    * Only the FIRST match goes, as java's does. */
  def removeValue[A](xs: scala.collection.mutable.Buffer[A], o: scala.Any): Boolean = {
    val i = xs.indexWhere(e => if o == null then e == null else o.equals(e))
    if i < 0 then false else { xs.remove(i); true }
  }

  /** `java.util.Collection.toArray()` — a FRESH `Object[]` of exactly `size`, in iteration order.
    *
    * Not `xs.toArray`, and the difference is not stylistic: scala's `toArray` takes a `ClassTag[B]`
    * and therefore allocates an array of the ELEMENT's runtime class, so a `Buffer[String]` would
    * hand back a `String[]` where java hands back an `Object[]`. That is invisible to a compile —
    * an `Array[String]` conforms nowhere an `Array[Object]` is wanted in scala (arrays are
    * invariant), so it fails at the SLOT and reads as a missing mapping; and where the result flows
    * into an `Object` it type-checks and diverges only at `arr[0] = someNonString`, which java
    * permits and an `Object[]`-declared-`String[]` rejects with `ArrayStoreException`. Allocating
    * `Object[]` is java's own contract, stated on `Collection.toArray()`.
    *
    * ==`size()` IS A HINT, NOT A LENGTH — and both directions are java's own code path==
    *
    * `AbstractCollection.toArray` does not trust `size()`. It allocates on it and then reconciles:
    * an iterator that runs out EARLY returns `Arrays.copyOf(r, i)`, and one that runs on returns
    * `finishToArray(r, it)`, which GROWS. Both arms are written out in the JDK with the comment
    * *fewer elements than expected*, so tolerating the disagreement is the specified behaviour and
    * not a defensive accident.
    *
    * That is not a corner: a collection whose `size()` is a CONSTANT of its shape rather than a
    * count of its live elements is an ordinary design — a bit-set over a fixed universe reports the
    * universe and iterates only the non-zero fields, and java's own test asserts the TRIMMED array.
    * Filling `new Array[Object](xs.size)` by iteration and returning it leaves the tail `null` and
    * compiles perfectly: `ArraySeq(a, b, c, d, null, null, …)` against `[a, b, c, d]`, no error
    * moved and no check able to see it (`ENGINE-LIMITS.md` K31). The other direction is worse only
    * in being loud — an `ArrayIndexOutOfBoundsException` where java grew the array. */
  def toArray(xs: scala.collection.Iterable[?]): Array[Object] = {
    val r  = new Array[Object](xs.size)
    val it = xs.iterator
    var i  = 0
    while i < r.length && it.hasNext do {
      r(i) = it.next().asInstanceOf[Object]
      i += 1
    }
    if i < r.length then java.util.Arrays.copyOf(r, i)  // fewer elements than `size()` said
    else if !it.hasNext then r
    else grownFrom(r, i, it)                            // …and more
  }

  /** `AbstractCollection.finishToArray` — the arm for an iterator that outlives `size()`.
    *
    * The GROWTH policy is not observable (the result is trimmed to the count either way), so this
    * is java's shape and not its arithmetic; what IS observable is the component type, which is why
    * every reallocation goes through `java.util.Arrays.copyOf` rather than a fresh `Array[A]` — the
    * same reason the three-part `toArray(a)` contract below gives. */
  private def grownFrom[A <: AnyRef](r0: Array[A], from: Int, it: Iterator[?]): Array[A] = {
    var r = r0
    var i = from
    while it.hasNext do {
      if i == r.length then r = java.util.Arrays.copyOf(r, (r.length >> 1) + r.length + 1)
      r(i) = it.next().asInstanceOf[A]
      i += 1
    }
    if i == r.length then r else java.util.Arrays.copyOf(r, i)
  }

  /** `java.util.Collection.toArray(T[] a)` — java's THREE-part contract, reproduced exactly.
    *
    * Java does not simply allocate: it fills the caller's array when the elements fit, and the
    * caller may be relying on that (`list.toArray(shared)` and then reading `shared`). The contract,
    * from `Collection.toArray(T[])` and implemented by `AbstractCollection`:
    *
    *   1. **`a.length >= size`** — the elements are written INTO `a`, which is also what is
    *      returned. The array the caller passed is the array the caller gets back.
    *   2. **`a.length < size`** — a NEW array is allocated, with `a`'s RUNTIME component type
    *      (`java.util.Arrays.copyOf`, which is what preserves it — a fresh `Array[A]` here would
    *      have the erased component type and the result would throw `ArrayStoreException` on the
    *      first element the caller stored into it through a `T[]`-typed reference).
    *   3. **the NULL TERMINATOR** — if the returned array is LONGER than the collection, index
    *      `size` is set to `null`, so a caller that walks until it sees a `null` stops in the right
    *      place. Java sets exactly that one element and leaves the rest of the tail alone; so does
    *      this.
    *
    * Every one of the three is a CLAUDE.md §4.4 shape — a naive `xs.toArray` compiles, returns the
    * right elements, and silently breaks all three: it never fills `a`, so an aliasing caller reads
    * a stale array; it allocates on the element's class rather than `a`'s; and it is exactly `size`
    * long, so the terminator a caller looks for is never written. None of that moves a compile-error
    * count, which is why the contract is spelled out here and pinned in `JavaCollectionsSpec`.
    *
    * `java.util.Arrays.copyOf` rather than `java.lang.reflect.Array.newInstance`: the two do the
    * same thing here, and only the first survives `PortabilityCheck` (reflection does not exist on
    * Scala.js or Native). */
  def toArray[A <: AnyRef](xs: scala.collection.Iterable[?], a: Array[A]): Array[A] = {
    val n  = xs.size
    val r  = if a.length >= n then a else java.util.Arrays.copyOf(a, n)
    val it = xs.iterator
    var i  = 0
    while i < r.length && it.hasNext do {
      r(i) = it.next().asInstanceOf[A]
      i += 1
    }
    // …and java's FOURTH part, which is the one the row above states: `size()` is a hint. The
    // terminator goes at the element COUNT and never at what `size()` claimed, and each of java's
    // three early-exit shapes is preserved — write into the caller's own array, trim, or copy back.
    if it.hasNext then grownFrom(r, i, it)
    else if i == r.length then r
    else if a eq r then { r(i) = null.asInstanceOf[A]; r }
    else if a.length < i then java.util.Arrays.copyOf(r, i)
    else {
      System.arraycopy(r, 0, a, 0, i)
      if a.length > i then a(i) = null.asInstanceOf[A]
      a
    }
  }

  // -------------------------------------------------------------------------------------------
  // The IMMUTABLE producers — `emptyList`, `emptyMap`, `emptySet`, `singletonList`,
  // `singletonMap`, `singleton`.
  //
  // Every one of these hands back a collection java REFUSES to modify, at a slot the port retyped
  // to a MUTABLE scala collection. `mutable.ArrayBuffer.empty` would compile and be wrong in the
  // one direction that matters: `Insertions.EMPTY = new Insertions(Collections.emptyMap())` is a
  // shared static, and a caller that put into it gets `UnsupportedOperationException` in java and,
  // with a growable buffer, silently corrupts a global here. That is CLAUDE.md §4.4 exactly — valid
  // scala meaning something else, with no compile error and no count moved.
  //
  // So the immutability is REPRODUCED rather than dropped: the three `Frozen*` classes below are
  // scala collections of the retyped shape whose every mutator throws
  // `UnsupportedOperationException`, which is java's own behaviour and not an approximation of it.
  // This is what ENGINE-LIMITS K6 said `Collections.unmodifiableList` had no target for — correctly
  // for the STDLIB, which has no read-only `Buffer` view; the runtime can supply one, and these
  // factories are the half of that family with no VIEW semantics to get wrong.
  // -------------------------------------------------------------------------------------------

  /** `java.util.Collections.emptyList()` — an immutable, empty `List`.
    *
    * SHARED, exactly as java's is (`Collections.EMPTY_LIST`, cast on the way out), because
    * reference identity is observable: java code writing `xs == Collections.emptyList()` is a
    * reference comparison, which this engine emits as `eq` (§4.4), and a fresh instance per call
    * would answer `false` where java answers `true`. The cast java performs is sound for the same
    * reason java's is — the value is empty and cannot be written to, so no element of type `A` is
    * ever read out of it or stored into it. */
  def emptyList[A](): scala.collection.mutable.Buffer[A] =
    frozenEmptyBuffer.asInstanceOf[scala.collection.mutable.Buffer[A]]

  /** `java.util.Collections.emptyMap()` — shared and immutable, for [[emptyList]]'s reasons. */
  def emptyMap[K, V](): scala.collection.mutable.Map[K, V] =
    frozenEmptyMap.asInstanceOf[scala.collection.mutable.Map[K, V]]

  /** `java.util.Collections.emptySet()` — shared and immutable, for [[emptyList]]'s reasons. */
  def emptySet[A](): scala.collection.mutable.Set[A] =
    frozenEmptySet.asInstanceOf[scala.collection.mutable.Set[A]]

  /** `java.util.Collections.singletonList(x)` — one element, immutable.
    *
    * FRESH per call, and that is java's behaviour too (`new SingletonList<>(o)`), so the identity
    * argument [[emptyList]] makes does not apply here. */
  def singletonList[A](x: A): scala.collection.mutable.Buffer[A] =
    new FrozenBuffer(scala.collection.immutable.Vector(x))

  /** `java.util.Collections.singleton(x)` — the `Set` of the same shape. */
  def singleton[A](x: A): scala.collection.mutable.Set[A] =
    new FrozenSet(scala.collection.immutable.Set(x))

  /** `java.util.Collections.singletonMap(k, v)` — one mapping, immutable. */
  def singletonMap[K, V](k: K, v: V): scala.collection.mutable.Map[K, V] =
    new FrozenMap(scala.collection.immutable.Map(k -> v))

  /** `java.util.Collections.unmodifiableList(xs)` — a read-only VIEW of `xs`, as java's is.
    *
    * A VIEW and not a copy, and that distinction is the whole of it: java's result reflects later
    * changes to `xs`, so a copy would silently detach every one of them (§4.4), and the identity
    * would silently drop the immutability. Both compile. What makes the honest answer reachable is
    * [[FrozenBuffer]], which delegates every READ to the collection it wraps and throws on every
    * write — exactly `Collections.UnmodifiableList`. ENGINE-LIMITS K6 recorded this as unmappable,
    * and it was, while the only candidate targets were the STDLIB's: scala has no read-only
    * `Buffer` view. The runtime can supply one, and this is it.
    *
    * The `? <: A` WIDENING is java's own signature (`unmodifiableList(List<? extends T>): List<T>`)
    * and is load-bearing for the same reason [[JavaCollection.unmodifiable]]'s is — a caller passes
    * a `List<Sub>` and stores the result as a `List<Super>`. The cast is sound for java's reason:
    * the view is read-only, so no `A` is ever stored into it. */
  def unmodifiableList[A](xs: scala.collection.Seq[? <: A]): scala.collection.mutable.Buffer[A] =
    new FrozenBuffer[A](xs.asInstanceOf[scala.collection.Seq[A]])

  /** `java.util.Collections.unmodifiableSet(s)` — [[unmodifiableList]]'s rule, one kind along. */
  def unmodifiableSet[A](s: scala.collection.Set[? <: A]): scala.collection.mutable.Set[A] =
    new FrozenSet[A](s.asInstanceOf[scala.collection.Set[A]])

  /** `java.util.Collections.unmodifiableMap(m)` — [[unmodifiableList]]'s rule, one kind along. */
  def unmodifiableMap[K, V](m: scala.collection.Map[? <: K, ? <: V]): scala.collection.mutable.Map[K, V] =
    new FrozenMap[K, V](m.asInstanceOf[scala.collection.Map[K, V]])

  private val frozenEmptyBuffer = new FrozenBuffer[Any](scala.collection.immutable.Vector.empty)
  private val frozenEmptySet    = new FrozenSet[Any](scala.collection.immutable.Set.empty)
  private val frozenEmptyMap    = new FrozenMap[Any, Any](scala.collection.immutable.Map.empty)

  /** `java.util.List.subList(from, to)` — a WRITE-THROUGH VIEW, as java's is.
    *
    * `xs.slice(from, to)` is the shape everyone reaches for and it COPIES, which breaks java's
    * documented idiom in both directions with no compile error (§4.4): `list.subList(a, b).clear()`
    * removes that range FROM THE LIST in java and does nothing at all to a copy, and
    * `sub.set(i, v)` writes through in java and into a detached array here.
    *
    * So the view is real: reads index into the backing buffer, `update` writes into it, and
    * `insert`/`remove` shift the backing buffer and move this view's own end. What is NOT
    * reproduced is java's fail-fast `ConcurrentModificationException` when the BACKING list is
    * structurally modified behind the view's back — and that is not a gap, because java does not
    * promise it either: `List.subList` says the view's behaviour is *undefined* in that case and
    * the exception is explicitly best-effort.
    *
    * Java's own bounds are kept — `IndexOutOfBoundsException` for `from < 0`, `to > size` or
    * `from > to` — because a silently clamped range is a wrong answer rather than a loud one. */
  def subList[A](xs: scala.collection.mutable.Buffer[A], from: Int, to: Int): scala.collection.mutable.Buffer[A] = {
    if from < 0 || to > xs.length || from > to then
      throw new IndexOutOfBoundsException(s"subList($from, $to) on a list of size ${xs.length}")
    new SubBuffer(xs, from, to)
  }

  /** `java.util.Map.putIfAbsent(k, v)` — java's exact definition, which is NOT `getOrElseUpdate`.
    *
    * The two differ in what they RETURN, and the difference is silent: java hands back the PREVIOUS
    * value, so `null` is what a successful insertion returns and every `if (m.putIfAbsent(k, v) ==
    * null)` branches on it. `getOrElseUpdate` returns the value that is now in the map — the new
    * one on an insertion — so the same test would take the other branch, with no compile error.
    *
    * The body is `java.util.Map`'s own default implementation, verbatim, including its treatment of
    * a key mapped to `null` as ABSENT (java puts, and still returns `null`). */
  def putIfAbsent[K, V](m: scala.collection.mutable.Map[K, V], k: K, v: V): V = {
    val cur = m.get(k) match {
      case Some(x) => x
      case None    => null.asInstanceOf[V]
    }
    if cur == null then { m.put(k, v); null.asInstanceOf[V] } else cur
  }

  /** `java.util.Map.computeIfAbsent(k, f)` — java's own default implementation, which is NOT
    * `getOrElseUpdate`, and the two differ TWICE with no compile error either time.
    *
    *   - **a key mapped to `null` is ABSENT to java** and present to scala. Java's contract opens
    *     "if the specified key is not already associated with a value (or is mapped to null)", and
    *     a library that puts a `null` placeholder and later expects the factory to run gets the
    *     placeholder back from `getOrElseUpdate`;
    *   - **a factory that returns `null` records NOTHING in java** and stores the `null` in scala.
    *     Java's own words: "If the mapping function returns null, no mapping is recorded." The next
    *     call then re-runs the factory in java and does not in the port.
    *
    * The FUNCTION is java's own interface and its own wildcards — `Function<? super K, ? extends V>`
    * — because a caller may hold one (flexmark's `Parsing.getCachedPattern` takes the factory as a
    * parameter and forwards it), and a `K => V` formal would reject it while accepting every lambda.
    * A scala lambda SAM-converts to the wildcard-applied form; measured on 3.8.4, both directions. */
  def computeIfAbsent[K, V](m: scala.collection.mutable.Map[K, V], k: K,
                            f: java.util.function.Function[? >: K, ? <: V]): V = {
    val cur = m.get(k) match {
      case Some(x) => x
      case None    => null.asInstanceOf[V]
    }
    if cur != null then cur
    else {
      val v = f.asInstanceOf[java.util.function.Function[K, V]].apply(k)
      if v != null then { m.put(k, v); v } else null.asInstanceOf[V]
    }
  }

  /** `java.util.Collection.removeIf(p)` on a LIST — removes every element the predicate accepts,
    * and returns whether any went.
    *
    * Two things scala's nearest members are not. `filterInPlace` keeps what the predicate accepts,
    * which is the COMPLEMENT of what java removes — a silent inversion at a green compile — and it
    * returns the collection where java returns the `boolean` that callers branch on
    * (`if (list.removeIf(...)) recompute()`).
    *
    * The loop is INDEXED rather than a filter over a copy, so an element is identified by its
    * POSITION exactly as java's iterator identifies it. A predicate that answers differently for two
    * equal elements — `it.getOffset() == n` on a type whose `equals` ignores the offset, which is
    * flexmark's own shape — removes the right one here and could remove the other through any
    * by-value route. */
  def removeIf[A](xs: scala.collection.mutable.Buffer[A],
                  p: java.util.function.Predicate[? >: A]): Boolean = {
    val q = p.asInstanceOf[java.util.function.Predicate[A]]
    var i       = 0
    var removed = false
    while i < xs.length do
      if q.test(xs(i)) then { xs.remove(i); removed = true } else i += 1
    removed
  }

  /** `java.util.Collection.removeIf(p)` on a SET — the same member at the other kind.
    *
    * A SECOND NAME and not an overload, for two reasons that are both about being read: the two
    * erase to the same signature so scala cannot overload them at all, and the phase picks by the
    * receiver's KIND, so the emitted call names which one it meant rather than leaving it to a
    * dispatch at run time. `sort`/`sortNatural` are already split this way.
    *
    * A set has no positions, so the doomed elements are collected first and removed after — never
    * during the iteration, which is what java's own default implementation is careful to do through
    * `Iterator.remove` and what a `mutable.Set` gives no equivalent of. */
  def removeIfSet[A](xs: scala.collection.mutable.Set[A],
                     p: java.util.function.Predicate[? >: A]): Boolean = {
    val q     = p.asInstanceOf[java.util.function.Predicate[A]]
    val doomed = xs.iterator.filter(q.test).toList
    doomed.foreach(xs -= _)
    doomed.nonEmpty
  }

  /** `java.util.List.spliterator()` — java's own DEFAULT, at java's own CHARACTERISTICS.
    *
    * A `Spliterator` is a parallel-DECOMPOSITION protocol and its only consumer is
    * `java.util.stream`, which the collections phase collapses rather than models — which is why
    * this stayed refused when `listIterator` did not (`ENGINE-LIMITS.md` K23). That refusal's
    * stated evidence was a NEAR MISS — `buf.asJava.spliterator()` reports NEITHER `ORDERED` nor
    * `SIZED` where the `ArrayList` java held reports both — and it DOES NOT REPRODUCE: measured on
    * scala 3.8.4, the converter's wrapper reports `ORDERED | SIZED | SUBSIZED`, the same `16464`
    * these produce (`JavaCollectionsSpec` pins it).
    *
    * So delegating would have worked, and these exist for a different reason: they make the
    * characteristics follow JAVA'S OWN DECLARATION at the owner the receiver was typed by — a fact
    * a reader can check against the JDK source — instead of following whatever scala's converter
    * happens to wrap the collection in. §4.5's argument for a standalone shim over an inherited
    * one, and NOT the argument the refusal made. Java re-declares `spliterator()`
    * three times with three different characteristic sets, and `Spliterators.spliterator(Collection,
    * int)` ORs in `SIZED | SUBSIZED` on top of whatever each passes:
    *
    *   - `Collection.spliterator()` passes `0`         → `SIZED | SUBSIZED`
    *   - `List.spliterator()`       passes `ORDERED`   → `ORDERED | SIZED | SUBSIZED`
    *   - `Set.spliterator()`        passes `DISTINCT`  → `DISTINCT | SIZED | SUBSIZED`
    *
    * so a `List`-typed receiver answers exactly what java's `ArrayList` answered, which is the cell
    * the refusal named. THREE NAMES rather than one helper taking an `Int`, for `removeIf`'s own
    * two reasons one member up: the phase already picks by the receiver's KIND, so the emitted call
    * should NAME which of java's three declarations it is reproducing rather than carry a magic
    * constant, and a reader of the port can then check it against the JDK source.
    *
    * The traversal goes through java's own `Spliterators.spliterator(Collection, int)`, which is
    * what each of the three defaults calls — so `SIZED | SUBSIZED` arrives from java's code and is
    * not OR-ed here, where it would be this file restating a JDK constant it does not own. What
    * does NOT carry across is the fail-fast `modCount` check, for the reason
    * `JavaListIterator.over` states at length: a `mutable.Buffer` publishes no such counter, so java
    * THROWS a `ConcurrentModificationException` where this reads on. */
  def orderedSpliterator[A](xs: scala.collection.Iterable[A]): java.util.Spliterator[A] =
    spliteratorWith(xs, java.util.Spliterator.ORDERED)

  /** `java.util.Set.spliterator()` — the same, `DISTINCT`. */
  def distinctSpliterator[A](xs: scala.collection.Iterable[A]): java.util.Spliterator[A] =
    spliteratorWith(xs, java.util.Spliterator.DISTINCT)

  private def spliteratorWith[A](xs: scala.collection.Iterable[A], extra: Int): java.util.Spliterator[A] =
    java.util.Spliterators.spliterator(
      new java.util.AbstractCollection[A] {
        def iterator(): java.util.Iterator[A] = {
          val it = xs.iterator
          new java.util.Iterator[A] {
            def hasNext(): Boolean = it.hasNext
            def next(): A          = it.next()
          }
        }
        def size(): Int = xs.size
      }
      ,
      extra)

  /** `java.util.Map.containsValue(v)` — with java's own equality DIRECTION.
    *
    * `HashMap.containsValue` reads `v == value || (value != null && value.equals(v))`: identity
    * first, then the PROBE's `equals` applied to the stored value. Scala's `exists(_._2 == v)` asks
    * the STORED value's `equals`, which is the other way round and diverges for any asymmetric
    * `equals` — a subclass that narrows it, `java.sql.Timestamp` against `java.util.Date`. Same
    * argument, same shape, as `removeValue` two members up. */
  def containsValue[K, V](m: scala.collection.mutable.Map[K, V], v: scala.Any): Boolean =
    m.exists { case (_, stored) =>
      (stored.asInstanceOf[AnyRef] eq v.asInstanceOf[AnyRef]) || (v != null && v.equals(stored))
    }

  /** `java.util.Collection.containsAll(c)` — java's own default, `for (Object e : c) if
    * (!contains(e)) return false`.
    *
    * The equality direction is `contains`', which for `ArrayList` is `o.equals(element)` — the
    * PROBE's — and scala's `Seq.contains(elem)` is `exists(_ == elem)`, the element's. Written out
    * here for the reason `containsValue` above is: the two agree for every symmetric `equals` and
    * for nothing else, and nothing about a green compile would say which one ran.
    *
    * A LINEAR scan even where the receiver is a `Set`, which is a complexity difference and not a
    * behavioural one: java's `HashSet.contains` finds the element by hash, and this finds the same
    * element by scanning. They can only disagree where `hashCode` and `equals` already disagree.
    *
    * ==The ARGUMENT is a union, because the two sides of this call are on OPPOSITE sides of the
    * retyping==
    * `java.util.Collection.containsAll(Collection<?>)` is a member a class IMPLEMENTING `java.util
    * .List` also declares, and the port emits that declaration's parameter as the SHIM
    * ([[JavaCollection]]) while the field it delegates to is a retyped `Buffer` — so the one call
    * this arm rewrites routinely has a scala receiver and a java-shaped argument. A
    * `IterableOnce[?]` formal would reject exactly that site, and a `JavaIterable[?]` formal would
    * reject every ordinary one. The union states both and dispatches once, which is honest about a
    * seam that really does have two shapes; §4.5 forbids the alternative (giving the shim a scala
    * collection parent so one formal covers both). */
  def containsAll[A](xs: scala.collection.Iterable[A],
                     c: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean =
    elementsOf(c).forall(o => xs.exists(e => if o == null then e == null else o.equals(e)))

  /** the ARGUMENT of one of java's four bulk `Collection` operations, read once, from whichever side
    * of the retyping it arrived on.
    *
    * Stated ONCE rather than inlined per member, because the union is not a convenience: it is the
    * seam [[containsAll]]'s own doc describes, and four members spelling it four times is four
    * places for the two arms to drift. Nothing about the dispatch is per-member. */
  private def elementsOf(c: scala.collection.IterableOnce[?] | JavaIterable[?]): scala.collection.Iterator[Any] =
    c match {
      case it: scala.collection.IterableOnce[?] => it.iterator
      case ji: JavaIterable[?]                  =>
        val jit = ji.iterator()
        new scala.collection.AbstractIterator[Any] {
          def hasNext: Boolean = jit.hasNext()
          def next(): Any      = jit.next()
        }
    }

  // -------------------------------------------------------------------------------------------
  // `java.util.Collection`'s BULK DEFAULTS, at ONE receiver contract
  // -------------------------------------------------------------------------------------------
  //
  // `addAll`, `removeAll` and `retainAll` are three members a java class INHERITS from
  // `java.util.AbstractCollection` and never writes — and a class that DEFINES a collection
  // routinely calls them through `super`, to delegate the general case its own fast path does not
  // cover. Re-parenting such a class onto a scala collection removes the implementation java was
  // calling, and the phase owes one back: `CLAUDE.md` §1's *an obligation the engine's own
  // translation created*, with the measurement in `ENGINE-LIMITS.md` K29.
  //
  // ==ONE receiver type across the three, and it is java's own contract rather than a bound
  // somebody computed==
  // `mutable.Iterable[A] & mutable.Growable[A] & mutable.Shrinkable[A]` is exactly "a mutable
  // collection you can iterate, add to and remove from", which is what `java.util.Collection`
  // demands of every implementation — so the intersection is the SIGNATURE of the thing whose
  // defaults these are. Both targets a definer is re-parented onto satisfy it (`mutable.Buffer` and
  // `mutable.Set`), and that is MEASURED rather than assumed: an intersection is a place scala's
  // inference can decline, and a helper that does not INFER is a helper the emitted call cannot use.
  // `JavaCollectionsSpec` pins all three shapes — a plain `Buffer`, a `mutable.Set`, and a GENERIC
  // class extending `mutable.Set` calling the helper on `this`, which is the emitted shape itself.
  //
  // ==the equality DIRECTION is the OPPOSITE of `containsAll`'s, and that is java's==
  // `containsAll` asks `this.contains(o)` for each `o` of the ARGUMENT, so the probe is the
  // argument's element. These two ask `c.contains(e)` for each `e` of the RECEIVER, so the probe is
  // the receiver's. Same member family, two directions, and they differ for any asymmetric `equals`
  // — a subclass that narrows it, `java.sql.Timestamp` against `java.util.Date`. Pinned by spec in
  // both directions, because nothing about a green compile says which one ran.
  //
  // ==the argument is read ONCE and materialised==
  // Java takes a `Collection`, which is re-iterable, and probes it per element of the receiver. The
  // formal here admits an `IterableOnce`, which is not — so the elements are drained into a `List`
  // before the scan rather than re-`iterator`ed per element, which would silently answer `false` for
  // every element after the first. A complexity difference against java's `HashSet` argument and no
  // behavioural one, exactly as `containsAll`'s own linear scan is.

  /** `java.util.Collection.removeAll(c)` — java's own default, `while (it.hasNext()) if (c.contains
    * (it.next())) it.remove()`, answering whether anything went.
    *
    * EVERY occurrence goes, which is the one thing `removeValue` above deliberately does not do:
    * java's `remove(Object)` removes the FIRST match and its `removeAll` removes all of them, and
    * the two live apart in this object for exactly that reason.
    *
    * The BUFFER arm removes POSITIONALLY, as java's iterator does, for the reason [[removeIf]]'s own
    * doc gives — an element is identified by its place and not by an `equals` a second element might
    * also satisfy. A `Set` has no positions and `-=` IS the hash lookup java's `Iterator.remove`
    * reaches, so the fallback is exact there rather than approximate. Neither arm removes DURING the
    * iteration it decided from, which is what java's default is careful to do through
    * `Iterator.remove` and what scala gives no equivalent of. */
  def removeAll[A](xs: scala.collection.mutable.Iterable[A] & scala.collection.mutable.Growable[A]
                     & scala.collection.mutable.Shrinkable[A],
                   c: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = {
    val probes = elementsOf(c).toList
    dropWhere(xs, e => probes.exists(o => if e == null then o == null else e.equals(o)))
  }

  /** `java.util.Collection.retainAll(c)` — the same loop with the test negated: every element of the
    * receiver NOT in the argument goes.
    *
    * Java's default reads `if (!c.contains(it.next())) it.remove()`, so the probe is the receiver's
    * element here too and this is [[removeAll]]'s complement member for member. An EMPTY argument
    * therefore EMPTIES the receiver, which is java's answer and is worth pinning: read as "retain,
    * so nothing to do", it would be a no-op. */
  def retainAll[A](xs: scala.collection.mutable.Iterable[A] & scala.collection.mutable.Growable[A]
                     & scala.collection.mutable.Shrinkable[A],
                   c: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = {
    val probes = elementsOf(c).toList
    dropWhere(xs, e => !probes.exists(o => if e == null then o == null else e.equals(o)))
  }

  /** remove every element the predicate dooms and say whether any did — the shared half of
    * [[removeAll]] and [[retainAll]], which differ only in that predicate. */
  private def dropWhere[A](xs: scala.collection.mutable.Iterable[A] & scala.collection.mutable.Growable[A]
                             & scala.collection.mutable.Shrinkable[A],
                           doomed: A => Boolean): Boolean = xs match {
    case b: scala.collection.mutable.Buffer[A @unchecked] =>
      var i       = 0
      var removed = false
      while i < b.length do
        if doomed(b(i)) then { b.remove(i); removed = true } else i += 1
      removed
    case _ =>
      val gone = xs.iterator.filter(doomed).toList
      gone.foreach(xs -= _)
      gone.nonEmpty
  }

  /** `java.util.ArrayList.ensureCapacity(n)` — a CAPACITY HINT, with no observable semantics.
    *
    * Java's own words are "increases the capacity of this ArrayList instance, if necessary": no
    * element moves, `size()` does not change and nothing a program can read differs afterwards. So
    * the faithful translation is the scala hint where one exists and NOTHING where one does not — a
    * `ListBuffer` has no capacity to reserve, and a no-op there is exact rather than approximate.
    * This is the one member in this file whose java behaviour a caller cannot observe, which is why
    * it is the one whose "do nothing" arm is admissible. */
  def ensureCapacity(xs: scala.collection.mutable.Buffer[?], n: scala.Int): Unit = xs match {
    case ab: scala.collection.mutable.ArrayBuffer[?] => ab.sizeHint(n)
    case _                                           => ()
  }

  /** `java.util.Optional.orElse(other)` — STRICT, which is the whole reason it needs a member here
    * rather than a rename.
    *
    * Every other `Kind.Opt` arm in `CollectionsTransform` is a pure rename: `getAsInt` is `get`,
    * `isPresent` is `isDefined`, the VALUE is the same object and only the member name differs.
    * `orElse` is not, and the difference is one word in a signature. Java's parameter is
    * `T other` — a VALUE, evaluated at the call, whatever the optional holds — and scala's
    * `Option.getOrElse` takes `=> B`, evaluated only when the option is empty. So a default with a
    * side effect (a counter, a `remove`, a lazily-built fallback that registers itself) runs in
    * java and does not run in the port, and a costly one runs in java and does not in the port:
    * same name, same answer, different program. `CLAUDE.md` §4.4's class exactly — valid Scala
    * meaning something else, with a green compile and no moved count.
    *
    * Here and not on the alias, because the alias IS `Option` and a type alias cannot carry a
    * member; and taking the default by VALUE is the entire content of the fix, so the body is
    * `getOrElse` and the signature is the translation.
    *
    * Kept in this object for the reason its own header gives for the `Map.Entry` statics: these are
    * the members a MAPPING left without a home, not members of a collection type. */
  def optionalOrElse[A](o: Option[A], other: A): A = o.getOrElse(other)

  // -------------------------------------------------------------------------------------------
  // The EXTERNAL SEAM — a collection that crosses into or out of code the port does not emit.
  //
  // Every other member of this object exists because a JDK UTILITY has no scala counterpart. These
  // two exist because a THIRD PARTY's compiled signature cannot be retyped at all: an ANTLR parser
  // returns `java.util.List<AtomContext>` and a generated lexer takes `java.util.Set<String>`, and
  // no amount of retyping inside the port changes either. The port's own code around them moved, so
  // the seam is real and one-sided, and the only honest answer is a conversion AT the seam.
  //
  // A live WRAPPER, never a copy, which is what `scala.jdk.CollectionConverters` gives: writes
  // through the view reach the underlying collection and the caller's later changes are visible.
  // A copy would compile and detach both directions — §4.4 in the shape the whole `subList`/
  // `unmodifiable*` family above is written to avoid.
  //
  // ==BOTH directions ship, and the consumer one is younger than the producer one==
  // `toJava` was written and DELETED once, because deciding that a formal is a `java.util.*` needs
  // the callee's signature and the frontend interned every external member with none — a capability
  // nothing can reach reads as one that works. `SpoonTir` now interns an external member WITH its
  // `MethodType` wherever a class file can be read for one scope-free, so the trigger exists and
  // the helper is reachable. Where the formal is still unknown — a class file the parse could only
  // partially resolve — the seam is COUNTED exactly as it was. See ENGINE-LIMITS K15.
  //
  // ==What these do NOT convert, and why the ELEMENT type decides it==
  // `asScala` converts one level. A `java.util.List<java.util.List<String>>` becomes a
  // `Buffer[java.util.List[String]]` while the port's retyping claims `Buffer[Buffer[String]]`, so
  // the two disagree one type argument in. `CollectionsTransform` therefore refuses to emit either
  // of these where the type arguments mention anything it retyped, and COUNTS the refusal —
  // a wrap that silently lies about its element type is worse than no wrap.
  // -------------------------------------------------------------------------------------------

  import scala.jdk.CollectionConverters.*

  /** a `java.util.List` a third party HANDED BACK, as the `Buffer` the port's code expects. */
  def fromJava[A](xs: java.util.List[A]): scala.collection.mutable.Buffer[A] = xs.asScala

  /** …a `java.util.Set`. */
  def fromJava[A](s: java.util.Set[A]): scala.collection.mutable.Set[A] = s.asScala

  /** …a `java.util.Map`. */
  def fromJava[K, V](m: java.util.Map[K, V]): scala.collection.mutable.Map[K, V] = m.asScala

  /** …a `java.util.Iterator`, which the port retypes to the REMOVAL-CAPABLE shim rather than to
    * scala's `Iterator`. The wrapper's `remove()` throws, which is java's own default for an
    * iterator that offers no removal — and `asScala` does not carry java's `remove` across, so
    * this is the one member of the family that loses something. It loses it LOUDLY. */
  def fromJava[A](it: java.util.Iterator[A]): JavaIterator[A] = JavaIterator.from(it.asScala)

  /** …a `java.lang.Iterable`. */
  def fromJava[A](i: java.lang.Iterable[A]): JavaIterable[A] = JavaIterable.from(i.asScala)

  // -------------------------------------------------------------------------------------------
  // An UNTYPED PROBE — the members java declares over `Object` rather than over the element type
  //
  // `Map.get`, `Map.containsKey`, `Map.remove`, `Collection.contains` and `Set.remove` all take an
  // `Object`. That is not an accident of erasure, it is the contract: java looks the argument up BY
  // VALUE, and a probe of an unrelated type simply misses. Scala's `Map[K, V]` and `Set[A]` declare
  // the same members over `K`/`A`, so once a retyping has moved the receiver the probe no longer
  // fits the slot, and there are TWO ways it arrives (see `CollectionsTransform.objectProbe`):
  //
  //   - the receiver's type ARGUMENTS are wildcards, so `K` is an unnameable capture — `Found:
  //     String / Required: map.K` — and for `get` the `null` default sits at the equally unnameable
  //     `V`, which renders as a bare `?` in a TERM position and is not even syntax;
  //   - the ARGUMENT is java's own `Object`: a class implementing `java.util.Map<String, T>` must
  //     declare `remove(Object)` and delegate, and the frontend's erasure coercion (G14) widens a
  //     type-parameter or wildcard-read key the same way at any call to one of these members.
  //
  // Every member here takes the probe as `Any` and never names the element type, which is java's
  // own contract; `get`/`remove` supply their own `null` INSIDE, where `V` is an ordinary type
  // parameter. The cast is a widening of the PROBE POSITION only and is erased, so no value is
  // reinterpreted and NO `checkcast` is inserted — which is the whole point: narrowing the probe to
  // `K` instead would throw `ClassCastException` at `map.get(anInteger)` on a `Map<String, ?>`,
  // where java answers `null` (CLAUDE.md §4.4's shape, valid scala meaning something else).
  //
  // The lookup is the same `hashCode`/`equals` one java performs, in java's own DIRECTION: scala's
  // `HashMap.contains(key)` and `HashSet.contains(elem)` compare `probe == stored`, which is
  // `probe.equals(stored)` — exactly what `HashMap.getNode` does. (`Buffer.contains` is the other
  // direction, which is why there is no `Seq` member here and `containsAll` writes its scan out.)
  //
  // Not `put` or `getOrDefault`: java REJECTS both on a `Map<?, ?>`, because each needs a value at
  // the capture, so for the wildcard face there is nothing to translate. For the `Object`-probe face
  // they are reachable and have no arm — a gap NAMED in `ENGINE-LIMITS.md` K24 rather than filled,
  // because java declares the VALUE of both at `V` and no site in the corpus reaches one.
  // -------------------------------------------------------------------------------------------

  /** java's `Map.get(Object)` — `null` when absent, and the key is `Object`. */
  def mapGet[K, V](m: scala.collection.Map[K, V], key: Any): V =
    m.asInstanceOf[scala.collection.Map[Any, V]].getOrElse(key, null.asInstanceOf[V])

  /** java's `Map.containsKey(Object)`. */
  def mapContainsKey[K, V](m: scala.collection.Map[K, V], key: Any): Boolean =
    m.asInstanceOf[scala.collection.Map[Any, V]].contains(key)

  /** java's `Map.remove(Object)` — which RETURNS the value that was there, or `null`. */
  def mapRemove[K, V](m: scala.collection.mutable.Map[K, V], key: Any): V =
    m.asInstanceOf[scala.collection.mutable.Map[Any, V]].remove(key).getOrElse(null.asInstanceOf[V])

  /** java's `Collection.contains(Object)` at a SET receiver.
    *
    * A `Set` and not an `Iterable`: `HashSet.contains` is a HASH lookup asking the PROBE's `equals`
    * (`HashMap.getNode` reads `key.equals(k)`), and scala's `Set.contains` is the same lookup asking
    * the same way, so the widening is the only difference between them. `ArrayList.contains` is a
    * SCAN asking the probe's `equals` too, but scala's `Seq.contains` is `exists(_ == elem)` — the
    * STORED element's — so a `Seq` member would have to be written out rather than delegated, and
    * `containsAll` two members up is where that scan already lives. */
  def setContains[A](xs: scala.collection.Set[A], o: Any): Boolean =
    xs.asInstanceOf[scala.collection.Set[Any]].contains(o)

  /** java's `Set.remove(Object)` — which RETURNS whether the set held it.
    *
    * `-=` is the rewrite everywhere else and answers the RECEIVER, so java's `boolean` is lost; this
    * member is the probe seam and java's own result, in one. */
  def setRemove[A](xs: scala.collection.mutable.Set[A], o: Any): Boolean =
    xs.asInstanceOf[scala.collection.mutable.Set[Any]].remove(o)

  // -------------------------------------------------------------------------------------------
  // java's two `Set`-typed VIEWS of a map — the semantic scala does not have
  // -------------------------------------------------------------------------------------------
  //
  // `Map.keySet()` and `Map.entrySet()` are LIVE views: java's javadoc says "the set is backed by
  // the map, so changes to the map are reflected in the set, and vice-versa", and it says of both
  // that they support removal and "do not support the add or addAll operations".
  //
  // Scala has half of that. `m.keySet` is the same live view with the same write-through, typed
  // `scala.collection.Set` — which is the honest type for what the view CAN do and the wrong type
  // for the slot java DECLARED, because every `java.util.Set` in a ported program is a
  // `scala.collection.mutable.Set`. And a map's entry view has no scala counterpart at all: the
  // phase's rewrite hands back the MAP, which really is an `Iterable[(K, V)]` and really is not a
  // `Set`.
  //
  // So the two positions the phase could reach — a `val` initialised from `keySet` (retyped to the
  // read-only view) and a coercion SOURCE (refused) — each answered one slot, and a RETURN typed
  // at the declared `java.util.Set` was answered by neither. These two views are that answer, and
  // they belong to the runtime for §1's own reason: the capability is one scala's library does not
  // express, not a shape the emitter could have chosen differently.
  //
  // Both REFUSALS are java's own, spelled the way `CLAUDE.md` §1 asks — the java contract's own
  // refusal at an operation the interface itself declares unsupported, which is louder than java
  // and never quieter.

  /** java's `Map.keySet()` — the LIVE, WRITE-THROUGH view, at the type the retyping declares.
    *
    * Removal reaches the MAP, which is java's contract and is exactly the capability
    * `scala.collection.Set` cannot express; `add` throws what java's own view throws. */
  def keySetView[K, V](m: scala.collection.mutable.Map[K, V]): scala.collection.mutable.Set[K] =
    new scala.collection.mutable.AbstractSet[K] {
      def iterator: scala.collection.Iterator[K] = m.keysIterator
      def contains(k: K): Boolean                = m.contains(k)
      def addOne(k: K): this.type =
        throw new UnsupportedOperationException("add on a keySet() view")
      def subtractOne(k: K): this.type           = { m.remove(k); this }
      override def size: Int                     = m.size
      override def knownSize: Int                = m.knownSize
      override def isEmpty: Boolean              = m.isEmpty
      override def clear(): Unit                 = m.clear()
    }

  /** java's `Map.entrySet()` — the LIVE view of the map's mappings as a `Set` of pairs.
    *
    * The ELEMENT stays a `Tuple2`, which is the phase's existing decision and its existing refusal:
    * java's `Entry.setValue` writes through and a tuple cannot, so it fails to COMPILE rather than
    * writing to a copy (and the one shape where the map IS reachable — a `for` over `entrySet()` —
    * is rewritten to the map's own `put`). What this view adds is only the SET, so a declaration
    * java typed `Set<Map.Entry<K, V>>` has a value that conforms to it.
    *
    * `remove` is java's: an entry is removed only when the KEY AND THE VALUE both match, which is
    * what makes it a view of the mappings rather than of the keys. */
  def entrySetView[K, V](m: scala.collection.mutable.Map[K, V]): scala.collection.mutable.Set[(K, V)] =
    new scala.collection.mutable.AbstractSet[(K, V)] {
      def iterator: scala.collection.Iterator[(K, V)] = m.iterator
      def contains(e: (K, V)): Boolean                = m.get(e._1).contains(e._2)
      def addOne(e: (K, V)): this.type =
        throw new UnsupportedOperationException("add on an entrySet() view")
      def subtractOne(e: (K, V)): this.type = {
        if m.get(e._1).contains(e._2) then m.remove(e._1)
        this
      }
      override def size: Int          = m.size
      override def knownSize: Int     = m.knownSize
      override def isEmpty: Boolean   = m.isEmpty
      override def clear(): Unit      = m.clear()
    }

  /** a class the port DECLARES that kept java's `Map.Entry` as its parent, at the `Tuple2` slot the
    * mapping gave every USE of that interface — `ENGINE-LIMITS.md` K5.7's other half.
    *
    * The two halves of that entry are about different things and only one of them is a refusal.
    * `Tuple2` cannot BE a parent (it is final, takes its components in its constructor, and has no
    * write-through member), so a class implementing `Map.Entry` keeps java's interface — and its
    * VALUE then meets a slot the mapping already retyped to a pair. A projection is a COPY, and a
    * copy of an entry whose `setValue` writes through is exactly what K2 refuses: the write would
    * succeed and change nothing.
    *
    * What licenses it here is a CAPABILITY the caller has already checked, never this helper's own
    * opinion: `CollectionsTransform.detachedEntries` inserts this call only where the class's own
    * `setValue` — the library's, read before any substitution of this phase's — unconditionally
    * throws. There is then no write-through to lose, and the value really IS a detached pair.
    *
    * ONE evaluation of the argument, which is why this is a helper and not `(e.getKey, e.getValue)`
    * spelled at the site: the term at such a slot is routinely a `new` or a call, and duplicating it
    * is `CLAUDE.md` §4.4's compound-assignment shape one position over. */
  def entryToPair[K, V](e: java.util.Map.Entry[K, V]): (K, V) = (e.getKey, e.getValue)

  /** the CONSUMER direction: a `Buffer` the port holds, at a class file's `java.util.List` FORMAL.
    *
    * A live view, for the reason the whole family is: java's callee may KEEP the collection — an
    * ANTLR lexer stores the `Set<String>` it is constructed with — so a copy would detach every
    * later change the port makes to it. `asJava` on a MUTABLE scala collection is writable in both
    * directions, which is what java's own parameter passing means.
    *
    * The RESULT type is java's most specific: `java.util.List` also satisfies a
    * `java.util.Collection` and a `java.lang.Iterable` formal, so one overload per KIND covers
    * every slot in the family rather than one per formal type. */
  def toJava[A](xs: scala.collection.mutable.Buffer[A]): java.util.List[A] = xs.asJava

  /** …a `Set`. */
  def toJava[A](s: scala.collection.mutable.Set[A]): java.util.Set[A] = s.asJava

  /** …a `Map`. Note java's `Map` is neither a `Collection` nor an `Iterable`, so this overload
    * serves exactly the `java.util.Map` formals and no others — which is the same asymmetry
    * `coerce`'s refusal table records for the shim direction. */
  def toJava[K, V](m: scala.collection.mutable.Map[K, V]): java.util.Map[K, V] = m.asJava

  /** …and a `java.util.stream.Stream` FORMAL, which the collapse is what creates.
    *
    * `xs.stream().map(f)` collapses to `xs.map(f)` — a strict scala collection where java had a
    * lazy `Stream` — and that is right wherever the chain ends inside the program, because its
    * TERMINAL (`collect`) materialises anyway. Where the chain is NOT terminated the value crosses
    * back out to java at a `Stream` slot (`Stream.concat`, `Stream.of`, a third party's own), and
    * the collapse's result has to become a `Stream` again.
    *
    * `asJava.stream()` and never `.iterator`: java's `Stream` carries `spliterator`-derived size and
    * ordering characteristics that a hand-built one does not, and `toArray` reads them. What is NOT
    * restored is laziness — the operations before this call have already run — which is the
    * collapse's own documented divergence met at its boundary rather than a new one. The result is
    * SEQUENTIAL, as `Collection.stream()` is. */
  def toStream[A](xs: scala.collection.Iterable[A]): java.util.stream.Stream[A] =
    xs.toBuffer.asJava.stream()

  // -------------------------------------------------------------------------------------------
  // THREE `mutable.Buffer` MEMBERS JAVA'S `List` HAS NO COUNTERPART FOR — `ENGINE-LIMITS.md` K28.1
  // -------------------------------------------------------------------------------------------
  //
  // A class the engine re-parented from `java.util.List` onto `scala.collection.mutable.Buffer` owes
  // that trait's whole abstract surface, and the engine's bridge for each member is a delegation to
  // the java member that answers it. Three of the nine have NO java member to delegate to:
  // `removeRange`, `insertAll` and `patchInPlace` are operations java's `List` simply does not
  // declare (its nearest spelling of the first is `subList(a, b).clear()`, which is a different
  // member on a different object). There is therefore no java behaviour to reproduce and the answer
  // owed is SCALA's, expressed over the primitives the bridge already supplies.
  //
  // They live here rather than as emitted trees for the reason `containsAll` and its three siblings
  // do: the body is a loop over `Buffer`'s own members, identical for every class the engine will
  // ever re-parent, and an emitted loop would be one `while` per owner per port with nothing
  // per-owner in it. Every call below dispatches VIRTUALLY through `self`, so on a re-parented class
  // it lands on that class's own bridge and therefore on java's own member — which is the same
  // argument the `super` → `this` substitution rests on (`ENGINE-LIMITS.md` K29), read at a
  // synthesised member rather than at a JDK default.

  /** `mutable.Buffer.remove(idx, count)` — java's `List` has no such member.
    *
    * `AbstractList.removeRange`'s own body: remove at the SAME index `count` times, which is what
    * makes it correct on a list that shifts left after each removal. */
  def bufferRemoveRange[A](self: scala.collection.mutable.Buffer[A], idx: Int, count: Int): Unit = {
    var n = count
    while n > 0 do {
      self.remove(idx)
      n -= 1
    }
  }

  /** `mutable.Buffer.insertAll(idx, elems)` — java's nearest is `addAll(int, Collection)`, whose
    * argument is a `java.util.Collection` and not an `IterableOnce`.
    *
    * Written over `insert` rather than bridged to `addAll` through a copy: a copy at the argument is
    * exactly the detachment K2 refuses, and `elems` may be a one-shot `Iterator`, which no
    * `java.util.Collection` view can be. Insertion order is preserved by advancing the index. */
  def bufferInsertAll[A](self: scala.collection.mutable.Buffer[A], idx: Int,
                        elems: scala.collection.IterableOnce[A]): Unit = {
    var at = idx
    val it = elems.iterator
    while it.hasNext do {
      self.insert(at, it.next())
      at += 1
    }
  }

  /** `mutable.Buffer.patchInPlace(from, patch, replaced)` — java has no counterpart at all.
    *
    * Scala's own contract, over the two above: `replaced` is CLAMPED to what is actually there,
    * which is what `Buffer`'s own implementations do and what keeps a `replaced` past the end from
    * throwing where scala's does not. */
  def bufferPatchInPlace[A](self: scala.collection.mutable.Buffer[A], from: Int,
                      patch: scala.collection.IterableOnce[A], replaced: Int): Unit = {
    val start = math.max(0, math.min(from, self.length))
    bufferRemoveRange(self, start, math.max(0, math.min(replaced, self.length - start)))
    bufferInsertAll(self, start, patch)
  }

  /** `java.util.Collections.reverse(list)` — in place, as java's is. */
  def reverse[A](xs: scala.collection.mutable.Buffer[A]): Unit = inPlace(xs, xs.toList.reverse)

  /** `java.util.Collections.swap(list, i, j)` — in place, as java's is.
    *
    * Not routed through [[inPlace]], and that is the point of writing it out: `swap` touches two
    * positions, so rebuilding the whole buffer would be a clear-and-refill where java performs two
    * `set` calls. On a buffer that is also aliased as something else — jbump's `Collisions.keySort`
    * swaps through a `List<?>` the caller still holds — an empty intermediate state is observable in
    * a way java's never is.
    *
    * Java's own `swap` is silent about equal indices and throws `IndexOutOfBoundsException`
    * otherwise; both fall out of the two `apply`/`update` calls unchanged, so nothing is
    * approximated here either. */
  def swap[A](xs: scala.collection.mutable.Buffer[A], i: scala.Int, j: scala.Int): Unit = {
    val t = xs(i); xs(i) = xs(j); xs(j) = t
  }

  /** Replace a buffer's contents, keeping the IDENTITY the caller holds.
    *
    * `sortInPlaceWith` would be the obvious call and is not available: it lives on
    * `IndexedSeqOps`, and the port's `List`/`Collection` slots are plain `Buffer` — which a
    * `ListBuffer` also satisfies. Sorting a snapshot and writing it back works for every `Buffer`
    * and preserves the one property that matters, that the reference the caller kept sees the
    * change. `sortWith` on a `List` is a stable merge sort, as `java.util.Collections.sort` is. */
  private def inPlace[A](xs: scala.collection.mutable.Buffer[A], replacement: scala.collection.Seq[A]): Unit = {
    xs.clear()
    xs ++= replacement
  }

  /** `java.util.Map.Entry.comparingByKey(cmp)` over the `Tuple2` a `Map.Entry` becomes.
    *
    * The engine maps `Map.Entry` to `Tuple2` (a key/value pair has no identity of its own), which
    * means `Entry`'s own statics have to come along: `Tuple2` has no `comparingByKey`, and the call
    * survives to the compiler naming `java.util.Map.Entry` — a type the port no longer produces. */
  def comparingByKey[K, V](cmp: java.util.Comparator[? >: K]): java.util.Comparator[(K, V)] =
    (a: (K, V), b: (K, V)) => cmp.compare(a._1, b._1)

  // -------------------------------------------------------------------------------------------
  // REIFIED OCCURRENCES — an `instanceof` and a downcast ask about a RUNTIME OBJECT
  //
  // Everything above this block is about STATIC types: a retyping moves a declaration, and the
  // seam it opens is a slot whose two sides disagree. `x instanceof java.util.Map` and
  // `(Map<K,V>) x` are not that. They are questions asked of an OBJECT at run time, and the
  // retyping moved neither the objects nor the classes they are instances of — so translating
  // them by moving the type alone changes the ANSWER, silently, in valid Scala.
  //
  // After a retyping a program legitimately holds BOTH representations at an `Object` slot: the
  // ones its own code made (the mapping's targets) and the ones an external producer made
  // (java's own classes — jackson deserialising into a `HashMap`, a parser handing back an
  // `ArrayList`, a caller passing the library a `Map` it built itself). Java's test accepted
  // every one of those. So each predicate below is the DISJUNCTION of the representations one
  // java type can have in a port, and each coercion accepts either and produces the port's.
  //
  // ==Why the disjunction is not just "the target, or java's own type"==
  // The mapping preserves java's subtype relations wherever it can (see `typeMap`'s own notes),
  // so for `Map`, `List`, `Set` and `Iterator` those two disjuncts are exact — every port
  // representation of a java `Map` is a `mutable.Map`. The two SHIM targets are where it does
  // not: `java.util.List <: java.util.Collection` in java, and `mutable.Buffer` is not a
  // `JavaCollection`, because the shim exists precisely so a class can EXTEND
  // `AbstractCollection` (CLAUDE.md §4.5). So `isCollection` names the targets of `Collection`'s
  // mapped java subtypes as well, and `isIterable` names those plus the collection shim.
  //
  // ==and why it is not `scala.collection.Iterable` either==
  // Widening the scala side to `Iterable` reads as the obvious simplification and is WRONG in the
  // other direction: a `mutable.Map` is a `scala.collection.Iterable` and a `java.util.Map` is
  // NOT a `java.util.Collection`, so `x instanceof Collection` would start answering true for a
  // map. Measured on liqp, where `LValue.asArray` treats a `Map` as a single element by exactly
  // that test: the loose form is TWO test failures worse than this one (552 vs 550 passing).
  // -------------------------------------------------------------------------------------------

  /** the two halves of a REIFIED occurrence: `is*` answers java's `instanceof`, `as*` performs
    * java's downcast. `CollectionsTransform` emits these where it retyped the tested/cast type;
    * where the target is a CONCRETE one no live view can produce (`mutable.HashMap`,
    * `ArrayBuffer`, `Tuple2`) it emits nothing and counts the refusal instead. */
  object Reified {

    import scala.jdk.CollectionConverters.*

    /** WHAT A SHIM IS DELEGATING TO — `ENGINE-LIMITS.md` K19, and the reason every predicate below
      * asks twice.
      *
      * A coercion at a shim target BUILDS a wrapper, because `mutable.Buffer` is not a
      * `JavaCollection` and no view can make it one. Java's cast was the identity, so a later
      * reified question about that value is still a question about the ORIGINAL class:
      * `(Collection) list` then `instanceof List` is TRUE in java. Asked of the wrapper alone it
      * was false, and `asBuffer` on it threw — valid Scala, right static types, wrong answer, no
      * count (`CLAUDE.md` §4.4 reached through a retyping).
      *
      * Transitive, because a chain of coercions is what produces the shape in the first place; and
      * an UNMODIFIABLE wrapper is deliberately not `Wrapping`, so this stops there — see that
      * trait for why handing back the guarded collection would be the worse defect. */
    private def under(x: Any): Any = x match {
      case w: Wrapping => under(w.wrapped)
      case other       => other
    }

    /** java's `x instanceof java.util.Map`. */
    def isMap(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[scala.collection.mutable.Map[?, ?]] || v.isInstanceOf[java.util.Map[?, ?]]
      test(x) || test(under(x))
    }

    /** java's `x instanceof java.util.List`. */
    def isBuffer(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[scala.collection.mutable.Buffer[?]] || v.isInstanceOf[java.util.List[?]]
      test(x) || test(under(x))
    }

    /** java's `x instanceof java.util.Set`. */
    def isSet(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[scala.collection.mutable.Set[?]] || v.isInstanceOf[java.util.Set[?]]
      test(x) || test(under(x))
    }

    /** java's `x instanceof java.util.Collection` — the shim target, so the mapped subtypes'
      * targets are named beside it (see the block comment). */
    def isCollection(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[JavaCollection[?]] || v.isInstanceOf[scala.collection.mutable.Buffer[?]] ||
        v.isInstanceOf[scala.collection.mutable.Set[?]] || v.isInstanceOf[java.util.Collection[?]]
      test(x) || test(under(x))
    }

    /** java's `x instanceof java.lang.Iterable` — every `Collection` representation plus the
      * `Iterable` shim itself. A java `Map` is not an `Iterable`, so no map is named here. */
    def isIterable(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[JavaIterable[?]] || v.isInstanceOf[java.lang.Iterable[?]]
      test(x) || test(under(x)) || isCollection(x)
    }

    /** java's `x instanceof java.util.Iterator`. */
    def isIterator(x: Any): Boolean = {
      def test(v: Any) = v.isInstanceOf[JavaIterator[?]] || v.isInstanceOf[java.util.Iterator[?]]
      test(x) || test(under(x))
    }

    // -----------------------------------------------------------------------------------------
    // …and the CAST. Each returns the port's representation, LIVE where the value is java's —
    // the same `asScala` view `fromJava` gives, for the same reason: the producer may still hold
    // the collection, so a copy would detach every later change. The result carries wildcard type
    // arguments and the emitter leaves java's own cast in place around the call, which is exact:
    // java's cast to `Map<K,V>` is unchecked in its type arguments too (JLS 5.5).
    // -----------------------------------------------------------------------------------------

    // …and each cast is taken THROUGH `under`, for the reason stated there: java's cast is the
    // identity, so `(Collection) list` followed by `(List) list` is two casts of ONE object, and
    // the second one reached `asInstanceOf[Buffer]` on the wrapper the first one built — a
    // `ClassCastException` in a program that compiled and whose every check count was flat.

    /** java's `(java.util.Map<K,V>) x`. */
    def asMap(x: Any): scala.collection.mutable.Map[?, ?] = under(x) match {
      case m: java.util.Map[?, ?] => m.asInstanceOf[java.util.Map[Any, Any]].asScala
      case m                      => m.asInstanceOf[scala.collection.mutable.Map[?, ?]]
    }

    /** java's `(java.util.List<A>) x`. */
    def asBuffer(x: Any): scala.collection.mutable.Buffer[?] = under(x) match {
      case xs: java.util.List[?] => xs.asInstanceOf[java.util.List[Any]].asScala
      case xs                    => xs.asInstanceOf[scala.collection.mutable.Buffer[?]]
    }

    /** java's `(java.util.Set<A>) x`. */
    def asSet(x: Any): scala.collection.mutable.Set[?] = under(x) match {
      case xs: java.util.Set[?] => xs.asInstanceOf[java.util.Set[Any]].asScala
      case xs                   => xs.asInstanceOf[scala.collection.mutable.Set[?]]
    }

    /** java's `(java.util.Collection<A>) x`.
      *
      * The port's own representations reach the shim through its two factories; a java value
      * reaches it through [[JavaCollection.fromJava]], which is a live view over java's own
      * collection and NOT a copy — this is the one direction `liveWrappable` refuses at a static
      * slot, and the reason it can be taken here is that the object is in hand: there is no
      * overload to resolve and no element type to lie about. */
    // The three SHIM targets keep their identity arm FIRST and do not unwrap: a value that already
    // IS the target is what java's identity cast yields, and rebuilding it from the underlying
    // would replace one wrapper with another for no gain.
    def asCollection(x: Any): JavaCollection[?] = x match {
      case c: JavaCollection[?]                       => c
      case xs: scala.collection.mutable.Buffer[?]     => JavaCollection.from(xs.asInstanceOf[scala.collection.mutable.Buffer[Any]])
      case xs: scala.collection.mutable.Set[?]        => JavaCollection.fromSet(xs.asInstanceOf[scala.collection.mutable.Set[Any]])
      case c: java.util.Collection[?]                 => JavaCollection.fromJava(c.asInstanceOf[java.util.Collection[Any]])
      case other                                      => other.asInstanceOf[JavaCollection[?]]
    }

    /** java's `(java.lang.Iterable<A>) x`. */
    def asIterable(x: Any): JavaIterable[?] = x match {
      case i: JavaIterable[?]     => i
      case i: java.lang.Iterable[?] =>
        JavaIterable.from(i.asInstanceOf[java.lang.Iterable[Any]].asScala)
      case xs: scala.collection.Iterable[?] =>
        JavaIterable.from(xs.asInstanceOf[scala.collection.Iterable[Any]])
      case other => other.asInstanceOf[JavaIterable[?]]
    }

    /** java's `(java.util.Iterator<A>) x`. */
    def asIterator(x: Any): JavaIterator[?] = x match {
      case it: JavaIterator[?]      => it
      case it: java.util.Iterator[?] =>
        JavaIterator.from(it.asInstanceOf[java.util.Iterator[Any]].asScala)
      case other => other.asInstanceOf[JavaIterator[?]]
    }

    // -----------------------------------------------------------------------------------------
    // THE EGRESS DIRECTION — `ENGINE-LIMITS.md` K21 face 1
    // -----------------------------------------------------------------------------------------
    //
    // Every `is*`/`as*` above answers a question the PORT asks about a value it is holding. This
    // one answers a question SOMEBODY ELSE asks: a value the port retyped is handed to external
    // java at a `java.lang.Object` slot, and the callee reads its RUNTIME representation —
    // reflectively, through a serializer, an injector or a bean mapper. Java's value there was a
    // `java.util.*`; the port's is a `scala.collection.*`, it CONFORMS to the formal, and jackson
    // answers by bean-serializing the internals:
    //
    //   writeValueAsString(mutable.HashMap("key" -> "value"))
    //     = {"scala$collection$mutable$HashMap$$table":[…],"empty":false,"class":"…"}
    //
    // Nothing static can see this. The formal is `Object`, so no slot disagrees, there is no type
    // to compare and no compile error to look for; the argument's own static type is usually
    // `Object` too, so the phase has no evidence at the call site either. The question is
    // therefore asked where the answer exists — of the OBJECT, at run time — which is what makes
    // this a member of `Reified` and not of the static `toJava` family beside it.
    //
    // ==DEEP, and by VIEW rather than by copy==
    // `toJava` (i.e. `asJava`) converts ONE level, which is exactly the refusal `coerce` records
    // for a nested element type. One level is not enough here: a serializer walks the whole tree,
    // and liqp's data model is a `Map<String,Object>` whose values are maps and lists. So each
    // view converts its elements ON READ, and the result is still a live view of the port's own
    // collection — a COPY would detach both directions, which is K15's own refusal.
    //
    // ==READ-ONLY, deliberately==
    // A java caller that MUTATES the value it was handed gets `UnsupportedOperationException`
    // where java would have written through. That is the loud failure M6 asks for, and the
    // alternative is worse: `entrySet().setValue` and `add` on a converting view have to guess
    // what to write BACK through the conversion, which is precisely §4.4's mistake. The consumers
    // this bridge exists for are readers.
    //
    // ==IDENTITY==
    // A view is not the object it views (K19's open half, unchanged). The one place identity is
    // cheaply preservable is an ARRAY, whose spine has to be copied to convert it — so the copy is
    // made only when an element actually moved, and the ORIGINAL array is returned otherwise.
    //
    // WHERE AN ELEMENT DID MOVE, THE ARRAY IS A COPY AND THEREFORE DETACHED — say it rather than
    // let it read as the identity the sentence above promises. Java handed the callee the port's
    // own array; the port hands it a new one, so a WRITE through it reaches nothing and a later
    // read of the port's array does not see it. That is the same detachment the collection arms
    // refuse by using views, and it is a refusal here too — an array has no view to build, since
    // java's `[]` access is a bytecode instruction and not an interface — so it is STATED rather
    // than counted. It is not the read-only compromise either: the copy accepts the write and
    // silently loses it, where a view would throw. Nothing in the corpus reaches this arm with an
    // element that moves, which is exactly why the sentence is here rather than a number.
    //
    // …and a SELF-REFERENTIAL array terminates. `Object[] a = new Object[1]; a[0] = a;` is legal
    // java and this arm is EAGER, so the recursion has no view's laziness to stop it — the guard is
    // an identity set threaded through the array chain alone (every other arm is lazy and re-enters
    // per read, which terminates by construction). An array already in the set is returned AS IT
    // ARRIVED, which is also the only honest answer: no detached copy of a cyclic array exists.

    /** the value AS JAVA WOULD HAVE HELD IT, decided from the object rather than from its type.
      *
      * Identity for everything this engine did not put there — a `String`, a boxed number, a
      * `java.util.*` the port never touched, a class the port emits — so it is safe to insert
      * wherever a value crosses out and the phase cannot prove the value is harmless. */
    def toJavaValue(x: Any): java.lang.Object = under(x) match {
      case null                             => null
      // …`Map` BEFORE `Iterable`: a scala `Map` is a `scala.collection.Iterable` of pairs, and a
      // java `Map` is not a `java.util.Collection` at all. The wrong order turns every map into a
      // list of tuples, which is the same asymmetry `isCollection` is exact about.
      case m: scala.collection.Map[?, ?]    => new MapView(m.asInstanceOf[scala.collection.Map[Any, Any]])
      case s: scala.collection.Set[?]       => new SetView(s.asInstanceOf[scala.collection.Set[Any]])
      case q: scala.collection.Seq[?]       => new ListView(q.asInstanceOf[scala.collection.Seq[Any]])
      case i: scala.collection.Iterable[?]  => new IterableView(i.asInstanceOf[scala.collection.Iterable[Any]])
      case it: scala.collection.Iterator[?] => new IteratorView(it.asInstanceOf[scala.collection.Iterator[Any]])
      // …and the three SHIMS, which are the other representation this engine introduces: a scala
      // trait with java's SHAPE is not a java type, so a java consumer sees a bean here too. A
      // DELEGATING one never reaches these arms — `under` has already replaced it with the scala
      // collection it wraps — so what is left is a class the port EMITS that implements the shim,
      // which in java implemented `java.util.Collection` and must read as one.
      case c: JavaCollection[?]             => new ShimCollectionView(c.asInstanceOf[JavaCollection[Any]])
      case i: JavaIterable[?]               => new ShimIterableView(i.asInstanceOf[JavaIterable[Any]])
      case it: JavaIterator[?]              => new ShimIteratorView(it.asInstanceOf[JavaIterator[Any]])
      case a: Array[AnyRef]                 => arrayValue(a, new java.util.IdentityHashMap[AnyRef, AnyRef]())
      case other                            => other.asInstanceOf[java.lang.Object]
    }

    /** java and scala agree about arrays, so the SPINE is already right and only the elements can be
      * wrong. Converted eagerly because an array has no view, and returned AS IT ARRIVED whenever
      * nothing moved — an array of `String` crossing out is the same object it was.
      *
      * `seen` is the cycle guard the IDENTITY note above is about, and it is allocated only on this
      * path: every other arm is a lazy view that re-enters per read and terminates by construction,
      * so paying for a map at each bridged argument would be a cost for a shape that cannot recurse.
      * An array already in it is returned unchanged, which is the only honest answer — no detached
      * copy of a self-referential array exists. */
    private def arrayValue(a: Array[AnyRef], seen: java.util.IdentityHashMap[AnyRef, AnyRef]): AnyRef =
      if seen.containsKey(a) then a
      else {
        seen.put(a, a)
        var moved = false
        val out = new Array[AnyRef](a.length)
        var i = 0
        while i < a.length do {
          out(i) = a(i) match {
            case n: Array[AnyRef] => arrayValue(n, seen)
            case _                => toJavaValue(a(i))
          }
          if out(i) ne a(i) then moved = true
          i += 1
        }
        if moved then out else a
      }

    /** the one entry shape these views need. Not `java.util.AbstractMap.SimpleImmutableEntry`,
      * whose availability is a platform question this module answers for itself; `equals` and
      * `hashCode` are java's own documented contract for `Map.Entry`. */
    private final class Entry(k: java.lang.Object, v: java.lang.Object) extends java.util.Map.Entry[Any, Any] {
      def getKey(): Any = k
      def getValue(): Any = v
      def setValue(value: Any): Any = throw new UnsupportedOperationException("setValue")
      override def equals(o: Any): Boolean = o match {
        case e: java.util.Map.Entry[?, ?] =>
          (if k == null then e.getKey == null else k.equals(e.getKey)) &&
            (if v == null then e.getValue == null else v.equals(e.getValue))
        case _ => false
      }
      override def hashCode(): Int =
        (if k == null then 0 else k.hashCode) ^ (if v == null then 0 else v.hashCode)
    }

    private final class MapView(m: scala.collection.Map[Any, Any]) extends java.util.AbstractMap[Any, Any] {
      def entrySet(): java.util.Set[java.util.Map.Entry[Any, Any]] =
        new java.util.AbstractSet[java.util.Map.Entry[Any, Any]] {
          def iterator(): java.util.Iterator[java.util.Map.Entry[Any, Any]] =
            new java.util.Iterator[java.util.Map.Entry[Any, Any]] {
              private val it = m.iterator
              def hasNext(): Boolean = it.hasNext
              def next(): java.util.Map.Entry[Any, Any] = {
                val (k, v) = it.next()
                new Entry(toJavaValue(k), toJavaValue(v))
              }
            }
          def size(): Int = m.size
        }
      // `AbstractMap` derives both from `entrySet`, i.e. in O(n). The map this views has them, and
      // a consumer that looks a key up should not pay for the whole spine.
      override def size(): Int = m.size
      override def isEmpty(): Boolean = m.isEmpty
    }

    private final class ListView(q: scala.collection.Seq[Any]) extends java.util.AbstractList[Any] {
      def get(i: Int): Any = toJavaValue(q(i))
      override def size(): Int = q.size
    }

    private final class SetView(s: scala.collection.Set[Any]) extends java.util.AbstractSet[Any] {
      def iterator(): java.util.Iterator[Any] = new IteratorView(s.iterator)
      def size(): Int = s.size
    }

    /** a scala `Iterable` that is neither a `Seq`, a `Set` nor a `Map` — java's nearest honest
      * shape for it is a `Collection` whose `add` throws, which is what `AbstractCollection` is. */
    private final class IterableView(i: scala.collection.Iterable[Any]) extends java.util.AbstractCollection[Any] {
      def iterator(): java.util.Iterator[Any] = new IteratorView(i.iterator)
      def size(): Int = i.size
    }

    private final class IteratorView(it: scala.collection.Iterator[Any]) extends java.util.Iterator[Any] {
      def hasNext(): Boolean = it.hasNext
      def next(): Any = toJavaValue(it.next())
    }

    private final class ShimCollectionView(c: JavaCollection[Any]) extends java.util.AbstractCollection[Any] {
      def iterator(): java.util.Iterator[Any] = new ShimIteratorView(c.iterator())
      def size(): Int = c.size()
    }

    private final class ShimIterableView(i: JavaIterable[Any]) extends java.util.AbstractCollection[Any] {
      def iterator(): java.util.Iterator[Any] = new ShimIteratorView(i.iterator())
      // `java.lang.Iterable` has no `size`, so the only honest answer is to count — which is what
      // a java caller asking a `Collection` view of an `Iterable` is asking for.
      def size(): Int = {
        val it = i.iterator()
        var n = 0
        while it.hasNext() do { it.next(); n += 1 }
        n
      }
    }

    private final class ShimIteratorView(it: JavaIterator[Any]) extends java.util.Iterator[Any] {
      def hasNext(): Boolean = it.hasNext()
      def next(): Any = toJavaValue(it.next())
    }
  }

  def comparingByValue[K, V](cmp: java.util.Comparator[? >: V]): java.util.Comparator[(K, V)] =
    (a: (K, V), b: (K, V)) => cmp.compare(a._2, b._2)

  /** `Stream.sorted(Comparator)` on a chain this engine has already collapsed to a `Buffer`.
    *
    * A COPY here, unlike [[sort]], and for the same reason [[sort]] is in place: java's stream
    * operation is non-mutating, and the collection it reads is often one the caller still holds.
    * The two differ because the java methods differ, not because one shape was convenient.
    *
    * Note the name. Scala's `Buffer.sorted` takes an IMPLICIT `Ordering`, so an unmapped
    * `Stream.sorted(cmp)` binds to it and fails with `Required: Ordering[…]` — an error that names
    * neither streams nor comparators. Had the element types lined up it would have been worse: a
    * silently different sort order. */
  def sortedWith[A](xs: scala.collection.mutable.Buffer[A], cmp: java.util.Comparator[? >: A])
      : scala.collection.mutable.Buffer[A] =
    xs.sortWith((a, b) => cmp.compare(a, b) < 0)

  /** `Stream.mapToDouble(f)` on a chain already collapsed to a `Buffer`.
    *
    * The WIDENING is the point, and it is why this is a named function rather than a bare `.map`.
    * Java's `ToDoubleFunction` widens whatever the lambda returns to `double`; simple-graphs sums
    * `Edge.getWeight()`, which is a `float`. Left as `.map(f).sum` scala would sum in FLOAT — the
    * same answer to within a few ulps, which is exactly the kind of difference that passes a
    * tolerance-based assertion until the collection is large enough and then does not. Declaring the
    * function `A => Double` makes scala insert the same widening java did, at the same place. */
  def mapToDouble[A](xs: scala.collection.mutable.Buffer[A], f: A => Double): scala.collection.mutable.Buffer[Double] =
    xs.map(f)

  /** `IntStream.range(a, b)` — a stream SOURCE that is not a collection, so nothing else in the
    * chain-collapse can produce it. Half-open, as java's is. */
  def intRange(startInclusive: Int, endExclusive: Int): scala.collection.mutable.Buffer[Int] =
    scala.collection.mutable.ArrayBuffer.from(startInclusive until endExclusive)

  /** `Collectors.toSet()` on a chain this engine has already collapsed to a `Buffer`.
    *
    * `collect(Collectors.toList())` collapses to NOTHING, because the receiver already IS the
    * sequence; `toSet` cannot, because the TARGET TYPE differs — which is exactly why K6 recorded
    * it as open rather than guessing. A `HashSet`, because that is what java's collector actually
    * builds; java documents the type, mutability, serializability and thread-safety of the result
    * as unspecified, so nothing here is a promise java makes and this does not. */
  def toSet[A](xs: scala.collection.mutable.Buffer[A]): scala.collection.mutable.Set[A] =
    scala.collection.mutable.HashSet.from(xs)

  /** `Collectors.toMap(keyFn, valueFn)` — java's TWO-argument form, which THROWS on a duplicate key.
    *
    * The throw is the whole reason this is not a `.map(x => k(x) -> v(x)).toMap`: java's two-argument
    * collector calls `Map.merge` with a remapping function that throws `IllegalStateException`, so a
    * duplicate key is a loud failure, and building a scala `Map` from pairs silently keeps the LAST
    * one. A stream whose keys collide is a bug java reports and the naive translation hides — §4.4,
    * with no compile error and no count moved. The three-argument overload below is the one that
    * takes a merge function, and it is a different method for that reason.
    *
    * The mappers are declared with JAVA's own `Function` type, not `A => K`: `Function.identity()`
    * reaches this slot in real code (liqp's `Insertions.of`) and is a `java.util.function.Function`,
    * while a lambda written at the call site SAM-converts to one. Declaring the scala type would
    * accept the second and reject the first. Same rule the shim's `removeIf` records for
    * `Predicate`. */
  def toMap[A, K, V](
      xs: scala.collection.mutable.Buffer[A],
      key: java.util.function.Function[? >: A, ? <: K],
      value: java.util.function.Function[? >: A, ? <: V]): scala.collection.mutable.Map[K, V] = {
    val m = scala.collection.mutable.HashMap.empty[K, V]
    xs.foreach { x =>
      val k = key.apply(x)
      if m.contains(k) then throw new IllegalStateException(s"Duplicate key $k")
      m(k) = value.apply(x)
    }
    m
  }

  /** `Collectors.toMap(keyFn, valueFn, mergeFn)` — the overload that RESOLVES a duplicate key.
    *
    * Java's merge runs as `merge(existing, incoming)` and that ORDER is observable for any
    * non-commutative resolver — `(a, b) -> b` means last-wins and `(a, b) -> a` means first-wins,
    * and swapping the two inverts every one of them with no compile error. Java also REMOVES the
    * mapping when the merge returns null, which is `Map.merge`'s documented behaviour and not an
    * accident of the collector. */
  def toMap[A, K, V](
      xs: scala.collection.mutable.Buffer[A],
      key: java.util.function.Function[? >: A, ? <: K],
      value: java.util.function.Function[? >: A, ? <: V],
      merge: java.util.function.BinaryOperator[V]): scala.collection.mutable.Map[K, V] = {
    val m = scala.collection.mutable.HashMap.empty[K, V]
    xs.foreach { x =>
      val k = key.apply(x)
      val v = value.apply(x)
      m.get(k) match {
        case Some(old) =>
          val merged = merge.apply(old, v)
          if merged == null then m.remove(k) else m(k) = merged
        case None => m(k) = v
      }
    }
    m
  }

  /** `Collectors.toCollection(Factory::new)` — build the factory's collection and fill it.
    *
    * `Growable` is the exact bound: it is what "a collection you can add to" is in scala, and it is
    * what every `Collectors.toCollection` target satisfies. */
  def into[A, C <: scala.collection.mutable.Growable[A]](
      xs: scala.collection.mutable.Buffer[A], factory: () => C): C = {
    val c = factory()
    c ++= xs
    c
  }

  /** A `mutable.Buffer` that REFUSES every mutation, as java's immutable lists do.
    *
    * PRIVATE, and it is the target the retyping demands rather than a type any port names: the
    * slot a `Collections.emptyList()` reaches is `scala.collection.mutable.Buffer`, because that is
    * what `java.util.List` maps to — so the value has to BE one. Everything scala derives from
    * `apply`/`length`/`iterator` (`map`, `filter`, `foreach`, `contains`, `mkString`) works and
    * builds an ordinary `ArrayBuffer` when it builds anything, which is what java's own read
    * operations do; everything that would MUTATE throws `UnsupportedOperationException`, which is
    * java's own behaviour and not an approximation of it.
    *
    * CLAUDE.md §4.5 forbids modelling a JAVA INTERFACE on a scala collection trait, and this is not
    * that: nothing in a port ever extends this, so there is no second java interface to satisfy and
    * no member of a ported class for the trait's hundreds of inherited names to collide with. The
    * rule's hazard is inheritance in the PORT, and this class is never in one. */
  private final class FrozenBuffer[A](under: scala.collection.Seq[A])
      extends scala.collection.mutable.AbstractBuffer[A] {
    def apply(i: Int): A                                   = under(i)
    def length: Int                                        = under.length
    override def iterator: scala.collection.Iterator[A]    = under.iterator
    override def knownSize: Int                            = under.length
    def update(i: Int, elem: A): Unit                      = refuse
    def insert(idx: Int, elem: A): Unit                    = refuse
    def insertAll(idx: Int, elems: scala.collection.IterableOnce[A]): Unit = refuse
    def prepend(elem: A): this.type                        = refuse
    def remove(idx: Int): A                                = refuse
    def remove(idx: Int, count: Int): Unit                 = refuse
    def addOne(elem: A): this.type                         = refuse
    def clear(): Unit                                      = refuse
    override def patchInPlace(from: Int, patch: scala.collection.IterableOnce[A], replaced: Int): this.type = refuse
  }

  /** `java.util.Arrays.asList(T[])`'s live, fixed-size view — see [[asListView]] for the contract.
    *
    * Every size-preserving operation reads or writes the ARRAY; every size-CHANGING one throws, as
    * `java.util.Arrays$ArrayList` does. `refuseFixedSize` rather than [[refuse]] because the reason
    * is a different one and the message a reader gets at run time is the whole value of throwing. */
  private final class ArrayViewBuffer[A](arr: Array[A]) extends scala.collection.mutable.AbstractBuffer[A] {
    def apply(i: Int): A                                = arr(i)
    def length: Int                                     = arr.length
    override def knownSize: Int                         = arr.length
    override def iterator: scala.collection.Iterator[A] = arr.iterator
    def update(i: Int, elem: A): Unit                   = arr(i) = elem
    def insert(idx: Int, elem: A): Unit                 = refuseFixedSize
    def insertAll(idx: Int, elems: scala.collection.IterableOnce[A]): Unit = refuseFixedSize
    def prepend(elem: A): this.type                     = refuseFixedSize
    def remove(idx: Int): A                             = refuseFixedSize
    def remove(idx: Int, count: Int): Unit              = refuseFixedSize
    def addOne(elem: A): this.type                      = refuseFixedSize
    def clear(): Unit                                   = refuseFixedSize
    override def patchInPlace(from: Int, patch: scala.collection.IterableOnce[A], replaced: Int): this.type =
      refuseFixedSize
  }

  /** `java.util.List.subList`'s view — see [[subList]] for the contract and for what is
    * deliberately not reproduced. `until` is a `var` because java's view resizes when you insert or
    * remove THROUGH it. */
  private final class SubBuffer[A](
      under: scala.collection.mutable.Buffer[A], from: Int, private var until: Int)
      extends scala.collection.mutable.AbstractBuffer[A] {
    def length: Int = until - from
    private def at(i: Int): Int =
      if i < 0 || i >= length then throw new IndexOutOfBoundsException(s"$i (sublist size $length)")
      else from + i
    private def gap(i: Int): Int =
      if i < 0 || i > length then throw new IndexOutOfBoundsException(s"$i (sublist size $length)")
      else from + i
    def apply(i: Int): A                                = under(at(i))
    override def iterator: scala.collection.Iterator[A] = Iterator.range(0, length).map(apply)
    def update(i: Int, elem: A): Unit                   = under(at(i)) = elem
    def insert(idx: Int, elem: A): Unit                 = { under.insert(gap(idx), elem); until += 1 }
    def insertAll(idx: Int, elems: scala.collection.IterableOnce[A]): Unit = {
      val es = scala.collection.immutable.Vector.from(elems)
      under.insertAll(gap(idx), es)
      until += es.size
    }
    def prepend(elem: A): this.type = { insert(0, elem); this }
    def addOne(elem: A): this.type  = { insert(length, elem); this }
    def remove(idx: Int): A         = { val v = under.remove(at(idx)); until -= 1; v }
    def remove(idx: Int, count: Int): Unit = {
      if count < 0 then throw new IllegalArgumentException(s"removing a negative number of elements: $count")
      var n = count
      while n > 0 do { under.remove(at(idx)); until -= 1; n -= 1 }
    }
    def clear(): Unit = remove(0, length)
    def patchInPlace(idx: Int, patch: scala.collection.IterableOnce[A], replaced: Int): this.type = {
      val es = scala.collection.immutable.Vector.from(patch)
      remove(idx, math.min(math.max(replaced, 0), length - idx))
      insertAll(idx, es)
      this
    }
  }

  /** [[FrozenBuffer]]'s `Set`. */
  private final class FrozenSet[A](under: scala.collection.Set[A])
      extends scala.collection.mutable.AbstractSet[A] {
    def contains(elem: A): Boolean                      = under.contains(elem)
    def iterator: scala.collection.Iterator[A]          = under.iterator
    override def knownSize: Int                         = under.size
    def addOne(elem: A): this.type                      = refuse
    def subtractOne(elem: A): this.type                 = refuse
    override def clear(): Unit                          = refuse
  }

  /** [[FrozenBuffer]]'s `Map`. */
  private final class FrozenMap[K, V](under: scala.collection.Map[K, V])
      extends scala.collection.mutable.AbstractMap[K, V] {
    def get(key: K): Option[V]                          = under.get(key)
    def iterator: scala.collection.Iterator[(K, V)]     = under.iterator
    override def knownSize: Int                         = under.size
    def addOne(kv: (K, V)): this.type                   = refuse
    def subtractOne(k: K): this.type                    = refuse
    override def clear(): Unit                          = refuse
  }

  /** java's own answer at every one of those members — `UnsupportedOperationException`, with the
    * message naming what the value IS, since the alternative a reader will guess is an engine bug. */
  private def refuseFixedSize: Nothing = throw new UnsupportedOperationException(
    "this list is java's `Arrays.asList(array)` — a FIXED-SIZE view of the caller's array. " +
      "`set` writes through; `add`/`remove`/`clear` throw, and java throws here too")

  private def refuse: Nothing = throw new UnsupportedOperationException(
    "this collection came from a java factory that returns an IMMUTABLE collection or an " +
      "unmodifiable VIEW (Collections.emptyList/emptyMap/emptySet/singletonList/singletonMap/" +
      "singleton/unmodifiableList/unmodifiableSet/unmodifiableMap); java throws here too")
}
