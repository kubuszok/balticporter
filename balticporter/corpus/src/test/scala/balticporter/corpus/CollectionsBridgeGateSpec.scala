package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** ONE SHIM'S ABSENCE MUST NOT SWITCH OFF ANOTHER SHIM'S BRIDGE. */
class CollectionsBridgeGateSpec extends PortSuite:

  /** `List` into a `Collection` formal — java's own subtyping, which the retyping does not keep:
    * `mutable.Buffer` is not a `JavaCollection`. Nothing here names `Iterable`. */
  private val noIterable =
    """package demo;
      |import java.util.*;
      |class Holder {
      |  static Holder of(Collection<String> xs) { return new Holder(); }
      |  Holder merge(List<String> more)         { return of(more); }
      |  Holder mergeSet(Set<String> more)       { return of(more); }
      |}
      |""".stripMargin

  /** the same program with ONE extra method, whose only job is to put `java.lang.Iterable` in the
    * symbol table. Nothing about the two calls under test changes. */
  private val withIterable =
    """package demo;
      |import java.util.*;
      |class Holder {
      |  static Holder of(Collection<String> xs) { return new Holder(); }
      |  Holder merge(List<String> more)         { return of(more); }
      |  Holder mergeSet(Set<String> more)       { return of(more); }
      |  int size(Iterable<String> any)          { int n = 0; for (String s : any) n++; return n; }
      |}
      |""".stripMargin

  test("a Buffer at an owned `Collection` formal is bridged — with no `Iterable` in the program") {
    assertEmits(port(noIterable, new CollectionsTransform),
                "Holder.of(balticporter.runtime.JavaCollection.from(more))")
  }

  test("…and a Set reaches the same slot through its own factory") {
    assertEmits(port(noIterable, new CollectionsTransform),
                "Holder.of(balticporter.runtime.JavaCollection.fromSet(more))")
  }

  test("the program that DOES name `Iterable` emits exactly the same two bridges") {
    // the control. If this one had also been failing, the bug would be in `coerce`'s table rather
    // than in the gate in front of it, and the fix would be somewhere else entirely.
    val p = port(withIterable, new CollectionsTransform)
    assertEmits(p, "Holder.of(balticporter.runtime.JavaCollection.from(more))")
    assertEmits(p, "Holder.of(balticporter.runtime.JavaCollection.fromSet(more))")
  }
