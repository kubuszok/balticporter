package balticporter.corpus

import balticporter.testkit.PortSuite

/** `null` at a slot whose formal is a TYPE PARAMETER — java assigns it to every reference type and
  * scala's `Null` is not a subtype of an unbounded `T`, so the faithful emission is the cast java
  * performs implicitly.
  *
  * The arm that resolves the parameter from the caller's own scope is old; this pins the one that
  * resolves it from the RECEIVER'S INSTANTIATION (`ENGINE-LIMITS.md` G12's third source), and the
  * two negatives that decide it. Both negatives are §4.56 read at a type variable: the substitution
  * is keyed on the DECLARING CLASS's formals by NAME, so a method's own variable of the same name
  * must not take it, and an actual this scope cannot write down must not be emitted at all.
  */
class NullAtTypeParamSpec extends PortSuite:

  test("a `null` at a callee's own variable, resolved through the RECEIVER's type arguments") {
    val p = port(
      """package demo;
        |class Bag<E> { void add(E e) { } }
        |class Holder<K, V> {
        |  Bag<V> values = new Bag<V>();
        |  void pad() { values.add(null); }
        |}
        |""".stripMargin)
    // `Bag<E>.add(E)` on a `Bag<V>` field says `E := V` exactly, and `V` is a type THIS class can
    // write. Without it the emitted `add(null)` is `Found: Null / Required: E`.
    assertEmits(p, "this.values.add(null.asInstanceOf[V])")
  }

  test("…and a CONCRETE instantiation names the concrete type") {
    val p = port(
      """package demo;
        |class Bag<E> { void add(E e) { } }
        |class Use {
        |  Bag<String> names = new Bag<String>();
        |  void pad() { names.add(null); }
        |}
        |""".stripMargin)
    assertEmits(p, "this.names.add(null.asInstanceOf[java.lang.String])")
  }

  test("NEGATIVE: a METHOD's own variable shadowing the class's does NOT take the receiver's") {
    val p = port(
      """package demo;
        |class Bag<E> {
        |  <E> void put(E e) { }
        |}
        |class Use {
        |  Bag<String> names = new Bag<String>();
        |  void pad() { names.put(null); }
        |}
        |""".stripMargin)
    // `put`'s `<E>` is not `Bag`'s, so the receiver's `E := String` says nothing about this slot —
    // and substituting it would emit a cast to a type java never chose. The old arm declines too
    // (`resolveTypeParam("E")` finds nothing in `Use`), so the honest emission is the bare `null`.
    assertEmits(p, "this.names.put(null)")
  }

  test("NEGATIVE: a WILDCARD receiver argument is not a type this scope can write") {
    val p = port(
      """package demo;
        |class Bag<E> { void add(E e) { } }
        |class Use {
        |  void pad(Bag<?> any) { any.add(null); }
        |}
        |""".stripMargin)
    // `?` is a capture, and rendering it would put a `?` where a real type has to go — the `?T`
    // stub this frontend refuses everywhere else. `receiverTypeArgs` excludes wildcards outright,
    // so this slot's answer comes from the ERASED-RECEIVER arm that was already here (G11) and
    // names `java.lang.Object`. What must never appear is the capture itself.
    assertEmits(p, "add(null.asInstanceOf[java.lang.Object])")
    assertNotEmits(p, "add(null.asInstanceOf[?")
  }

  test("NEGATIVE: a callee's OWN variable is nameable nowhere outside it, same NAME or not") {
    val p = port(
      """package demo;
        |class Actor { }
        |class Cell<Widget extends Actor> {
        |  static <Widget extends Actor> Cell<Widget> of(Widget w) { return new Cell<Widget>(); }
        |  static Cell<Actor> builder() { return of(null); }
        |}
        |""".stripMargin)
    // `of`'s `<Widget>` SHADOWS the class's, which is ordinary java, and java infers it as `Actor`
    // from the target type. The old guard asked whether the NAME resolved in scope — it does, to
    // the CLASS's `Widget` — and emitted `of(null.asInstanceOf[Widget])` from a `static` member,
    // where the class's parameter is not in scope at all (JLS 8.4.4): `Not found: type Widget`.
    // NEGATIVE for the fix: restore `resolveTypeParam(...).isDefined` and the ascription returns.
    assertEmits(p, "Cell.of(null)")
    assertNotEmits(p, "asInstanceOf[Widget]")
  }

  test("…and the SELF-CALL the arm exists for still casts — the variable is WRITABLE here") {
    val p = port(
      """package demo;
        |class Node<N> {
        |  void put(N n) { }
        |  void clear() { this.put(null); }
        |}
        |""".stripMargin)
    // `put`'s formal IS this class's `N`, and `N` is nameable in `clear`. Without the cast the
    // emitted `put(null)` is `Found: Null / Required: N`.
    assertEmits(p, "this.put(null.asInstanceOf[N])")
  }

  test("…and an ENCLOSING METHOD's own variable is writable too, though it is a DIFFERENT declaration") {
    val p = port(
      """package demo;
        |class Tree<T> { }
        |class Library {
        |  <T> Tree<T> make(String ref, T board) { return null; }
        |  <T> Tree<T> make(String ref) { return make(ref, null); }
        |}
        |""".stripMargin)
    // the callee's `<T>` and the caller's `<T>` are two declarations with one name, and java infers
    // the callee's from the caller's return type — so the caller's IS what the slot wants, and it is
    // in scope. A same-DECLARATION test answers `no` here and costs `Found: Null / Required: T`,
    // which is what it measured on gdx-ai and on ssg-md before this arm read writability instead.
    assertEmits(p, "make(ref, null.asInstanceOf[T])")
  }

  test("NEGATIVE: a null at an ORDINARY reference formal takes no cast at all") {
    val p = port(
      """package demo;
        |class Bag { void add(String s) { } }
        |class Use {
        |  Bag b = new Bag();
        |  void pad() { b.add(null); }
        |}
        |""".stripMargin)
    assertEmits(p, "this.b.add(null)")
  }

  test("a LAMBDA whose body is `null` takes the SAM RESULT's variable — the one slot with no formal") {
    val p = port(
      """package demo;
        |interface Factory<V> { V make(String k); }
        |class Key<T> {
        |  Key(String n, T d, Factory<T> f) { }
        |  Key(String n) { this(n, null, k -> null); }
        |}
        |""".stripMargin)
    // the ARGUMENT `null` has a formal (`T`) and was already cast; the lambda BODY has none —
    // java takes its type from `Factory<T>.make`'s result, and scala's `Null` is not a `T`.
    assertEmits(p, "null.asInstanceOf[T]")
    assertEmits(p, "=> null.asInstanceOf[T]")
  }

  test("…and where the SAM is INHERITED, the result is composed along the hierarchy") {
    val p = port(
      """package demo;
        |interface Fn<A, R> { R apply(A a); }
        |interface Maker<V> extends Fn<String, V> { }
        |class Key2<T> {
        |  Key2(String n, Maker<T> f) { }
        |  Key2(String n) { this(n, k -> null); }
        |}
        |""".stripMargin)
    // `Maker<V>` declares NO abstract method — the SAM is `Fn.apply(): R`, and only composing the
    // adaptation through `extends Fn<String, V>` reaches `T` from it.
    assertEmits(p, "=> null.asInstanceOf[T]")
  }

  test("…and where the inherited SAM is a JDK interface, which noClasspath cannot read") {
    val p = port(
      """package demo;
        |import java.util.function.Function;
        |interface Maker2<V> extends Function<String, V> { }
        |class Key3<T> {
        |  Key3(String n, Maker2<T> f) { }
        |  Key3(String n) { this(n, k -> null); }
        |}
        |""".stripMargin)
    assertEmits(p, "=> null.asInstanceOf[T]")
  }

  test("…and where the interface RE-DECLARES the inherited SAM at a DIFFERENT erasure") {
    val p = port(
      """package demo;
        |import java.util.function.Function;
        |class Holder { }
        |interface Maker3<V> extends Function<Holder, V> {
        |  @Override V apply(Holder h);
        |}
        |class Key4<T> {
        |  Key4(String n, Maker3<T> f) { }
        |  Key4(String n) { this(n, k -> null); }
        |}
        |""".stripMargin)
    // `Function.apply(T)` and `Maker3.apply(Holder)` are ONE method to java and two signatures to
    // Spoon — the shape a JVM bridge exists for. Counted as two, `Maker3` is not a SAM at all.
    assertEmits(p, "=> null.asInstanceOf[T]")
  }

  test("NEGATIVE: two abstract OVERLOADS are still two, so the interface is not a SAM") {
    val p = port(
      """package demo;
        |interface TwoWay { void f(String s); void f(int i); }
        |class UseTwo {
        |  void take(TwoWay t) { }
        |}
        |""".stripMargin)
    // same name, same arity, ONE declarer — no supertype edge, so nothing collapses and the
    // interface keeps both members.
    assertEmits(p, "def f(s: java.lang.String): scala.Unit")
    assertEmits(p, "def f(i: scala.Int): scala.Unit")
  }

  test("NEGATIVE: a lambda body `null` at a CONCRETE SAM result takes no cast") {
    val p = port(
      """package demo;
        |interface Factory<V> { V make(String k); }
        |class Use {
        |  Factory<String> f = k -> null;
        |}
        |""".stripMargin)
    // `Null` conforms to every reference type; only an ABSTRACT one rejects it, so a cast here
    // would be noise on every `x -> null` in a corpus.
    assertEmits(p, "=> null")
    assertNotEmits(p, "null.asInstanceOf[java.lang.String]")
  }

  test("an INLINED `this(null)` keeps the FORMAL's type — java's null has the slot's type") {
    val p = port(
      """package demo;
        |interface Holder { String all(); }
        |class Set2 {
        |  String s;
        |  Set2() { this(null); }
        |  Set2(Holder other) { if (other == null) s = ""; else s = other.all(); }
        |}
        |class Sub extends Set2 { }
        |""".stripMargin)
    // the nilary constructor is promoted and its `this(null)` INLINED, substituting the argument at
    // every use of `other` — including a RECEIVER, where a bare scala `null` has no members.
    assertEmits(p, "null.asInstanceOf[demo.Holder].all()")
    assertNotEmits(p, "null.all()")
  }

  test("NEGATIVE: an inlined non-null argument is substituted as java wrote it") {
    val p = port(
      """package demo;
        |class Set3 {
        |  String s;
        |  Set3() { this("x"); }
        |  Set3(String other) { s = other; }
        |}
        |class Sub3 extends Set3 { }
        |""".stripMargin)
    // only `null` takes its type from the slot (JLS 5.2); every other argument carries its own.
    assertEmits(p, """this.s = "x"""")
    assertNotEmits(p, "asInstanceOf[java.lang.String]")
  }
