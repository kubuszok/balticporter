package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** ONE SHIM'S ABSENCE MUST NOT SWITCH OFF ANOTHER SHIM'S BRIDGE.
  *
  * `coerce` is the single place a scala collection is bridged into a shim-typed slot, and its table
  * has two independent targets: `JavaIterable` (from `java.lang.Iterable`) and `JavaCollection`
  * (from `java.util.Collection`). The pass that applies it at an OWNED callee's formals was gated
  * on `javaIterableSym != SymId.None` — a symbol that exists only when the program NAMES
  * `java.lang.Iterable`.
  *
  * A library that uses `Collection` and never mentions `Iterable` is therefore a library where the
  * whole argument-bridging pass is a no-op, and nothing says so: no check fires, no policy entry is
  * unmatched, and the only evidence is `E134 None of the overloaded alternatives` at the call. liqp
  * is exactly that library — 135 java files, `Collection` throughout, `Iterable` nowhere, and not
  * one `JavaCollection.from(` in the emitted port.
  *
  * The rule this is an instance of is `CLAUDE.md` §4.56's: a phase may conclude something only from
  * what the phase itself did to the thing it is concluding about. "Is there a `JavaIterable` in
  * this program" is not a fact about a `Collection`-typed formal.
  *
  * The two fixtures below differ in ONE line — whether anything mentions `Iterable` — and must
  * emit the same bridge. They are separate programs on purpose: in one program the mention would
  * enable the other fixture and the test would prove nothing.
  */
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
