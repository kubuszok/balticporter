package balticporter.corpus

import balticporter.testkit.PortSuite

/** A STATIC member of a generic class cannot name the class's type parameters — java's rule, and
  * scala's too once the member lands in the companion object. */
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
