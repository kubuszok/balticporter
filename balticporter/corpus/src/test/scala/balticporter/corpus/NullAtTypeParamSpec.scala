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
