package balticporter.corpus

import balticporter.testkit.PortSuite

/** A call through an ERASED RECEIVER whose declared result is a RAW generic — the node's type must
  * say what the emitted scala HAS, not what java's expression meant. */
class ErasedReceiverResultSpec extends PortSuite:

  private val pooled =
    """package demo;
      |class Pool<E> { E obtain() { return null; } }
      |class Store<K, V> { void put(K k, V v) { } }
      |class Holder<T> {
      |  static class W<T> {
      |    static final Pool<W> pool = new Pool<W>();
      |    W init(T item) { return this; }
      |  }
      |  private final Store<T, W<T>> items = new Store<T, W<T>>();
      |  void add(T item) { items.put(item, W.pool.obtain().init(item)); }
      |}
      |""".stripMargin

  test("the raw result of an erased-receiver call is cast to the slot the receiver pins") {
    val p = port(pooled)
    // the receiver IS read through java's erased view — that part is G11 and unchanged …
    assertEmits(p, "asInstanceOf[demo.Holder.W[java.lang.Object]]")
    // … and the value it produces is converted back to what the slot's own instantiation says,
    // which is java's unchecked conversion written out.
    assertEmits(p, ".asInstanceOf[demo.Holder.W[T]])")
  }

  test("a call with NO erased receiver is untouched — the retype is scoped to that path") {
    val p = port(
      """package demo;
        |class Store<K, V> { void put(K k, V v) { } }
        |class Plain<T> {
        |  private final Store<T, T> items = new Store<T, T>();
        |  void add(T item) { items.put(item, item); }
        |}
        |""".stripMargin
    )
    assertEmits(p, "this.items.put(item, item)")
    assertNotEmits(p, "asInstanceOf")
  }
