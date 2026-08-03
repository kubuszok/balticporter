package balticporter.corpus

import balticporter.testkit.PortSuite

/** A STATIC member of a generic class cannot name the class's type parameters — java's rule, and
  * scala's too once the member lands in the companion object.
  *
  * The engine's raw fill reconstructs a raw type's arguments from same-named parameters in scope
  * (`Entries` inside `ObjectMap<K,V>` → `Entries[K, V]`, so a member projection stays
  * path-INdependent), and that fill has to be OFF in a static context. It was gated on a
  * per-EXECUTABLE flag, which is reset the moment an anonymous class in a static initialiser
  * declares an instance method — so the enclosing class's parameters became reachable again and the
  * emitted companion object said `Not found: type T`.
  *
  * The idiom that finds it is a per-class object pool, and it is written RAW precisely because java
  * says the same thing this spec does:
  *
  * {{{
  * private static class Wrapper<T> {
  *   private static final Pool<Wrapper> pool = new Pool<Wrapper>() {
  *     protected Wrapper newObject() { return new Wrapper(); }   // raw — `T` is out of scope
  *   };
  * }
  * }}}
  *
  * The scope now lives in the type-parameter FRAME instead, so everything lexically inside a static
  * member inherits the truth with no flag to reset.
  */
class StaticTypeParamScopeSpec extends PortSuite:

  /** the shape the fill exists for: a nested generic whose parameters are named like the enclosing
    * class's, referenced RAW from both an instance and a static member of that class. */
  private val nested =
    """package demo;
      |class Store<K, V> {
      |  static class Entries<K, V> { }
      |  Entries live;
      |  static Entries shared;
      |}
      |""".stripMargin

  test("an INSTANCE member fills a nested raw type from the class's parameters") {
    assertEmits(port(nested), "var live: demo.Store.Entries[K, V]")
  }

  test("a STATIC member of the same class fills it with `?` — those parameters are not in scope") {
    assertEmits(port(nested), "var shared: demo.Store.Entries[?, ?]")
  }

  test("…and so does an ANONYMOUS class declared inside a static initialiser") {
    // the case the per-executable flag could not see: `make()` is an INSTANCE method of the
    // anonymous class, so entering it reset the flag while the frame still carried `K`/`V`.
    val p = port(
      """package demo;
        |interface Factory { Object make(); }
        |class Store<K, V> {
        |  static class Entries<K, V> { }
        |  static final Factory f = new Factory() {
        |    public Entries make() { return new Entries(); }
        |  };
        |}
        |""".stripMargin
    )
    assertEmits(p, "override def make(): demo.Store.Entries[?, ?]")
    assertNotEmits(p, "Entries[K, V]")
  }

  test("a static METHOD sees its OWN type parameters — the rule removes the class's, not all of them") {
    val p = port(
      """package demo;
        |class Holder<T> {
        |  static <E> Holder<E> of(E e) { return null; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "def of[E <: java.lang.Object](e: E): Holder[E]")
  }
