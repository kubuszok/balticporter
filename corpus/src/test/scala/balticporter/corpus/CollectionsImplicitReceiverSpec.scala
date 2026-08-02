package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** AN INHERITED COLLECTION CALL WITH NO RECEIVER WRITTEN — the shape `inheritedKind` could not see.
  *
  * `ENGINE-LIMITS.md` K5 closed the class that EXTENDS a mapped JDK collection: `this.get(k)`,
  * `super.putAll(m)` and the rest are identified from the RESOLVED METHOD's declaring type rather
  * than from the receiver's own (which is the subclass's, a type this phase never minted). But the
  * rewrite is dispatched on `Tree.Select(recv, m)` — it needs a receiver term, both to ask the
  * question and to build the answer — and java's most common way of writing such a call is to write
  * no receiver at all:
  *
  * {{{
  * List<?> xs = new ArrayList<Object>() {{ add(a); add(b); }};   // java's double-brace idiom
  * }}}
  *
  * WHERE THE RECEIVER COMES FROM IS NOT UNIFORM, and the two halves below are why this spec has
  * negatives that pass before the fix. Inside a NAMED class the frontend already supplies one:
  * Spoon reports an implicit `CtThisAccess` and `SpoonTir` emits `this.add(…)` /
  * `Outer.this.add(…)`, choosing the innermost enclosing type that PROVIDES the member — so those
  * shapes have always reached `inheritedKind`. Inside an ANONYMOUS class the target is absent and
  * the call is a bare `Tree.Ident`, so the whole family went through untouched and emitted
  * `add(…)` against a `mutable.ArrayBuffer`, which has no such member. Four errors on one library's
  * suite, all in one field initialiser.
  *
  * The receiver java means is the innermost enclosing class that provides the member, and since the
  * member is a mapped collection's, that is the innermost enclosing class which IS one. The
  * traversal is bottom-up, so an anonymous class is offered the pending calls under it BEFORE
  * anything further out is: it claims them if its own type answers `kindAt`, and drops them
  * unclaimed if it does not — because `this` inside a nested anonymous class is that class, and a
  * receiver synthesised further out would be naming something Scala's `this` cannot reach from
  * there. An unclaimed call is emitted exactly as it was, which is the honest refusal.
  */
class CollectionsImplicitReceiverSpec extends PortSuite:

  /** java's double-brace initialiser, nested — the shape measured in the corpus. */
  private val doubleBrace =
    """package demo;
      |import java.util.*;
      |class Holder {
      |  List<?> made = new ArrayList<Object>() {{
      |    add("a");
      |    add(new HashMap<String, String>(){{
      |      put("k", "v");
      |    }});
      |  }};
      |}
      |""".stripMargin

  test("an unqualified inherited `add` in a double-brace anonymous subclass takes `this`") {
    assertEmits(port(doubleBrace, new CollectionsTransform), "this += \"a\"")
  }

  test("…and the NESTED anon claims its own call, so the outer one never sees it") {
    // the inner `new HashMap(){{ put(…) }}` is a `Map`, the outer an `ArrayList`. Bottom-up, the
    // inner class is offered its own pending call first; claimed by the outer instead, `put` would
    // have been rewritten against a `Buffer`.
    //
    // …and the whole `put` REWRITE arrives with it, which is the half that moved no error count and
    // was therefore invisible: java's `Map.put` returns the PREVIOUS value and scala's returns an
    // `Option`, so an unclaimed bare `put(k, v)` inside a double-brace initialiser COMPILED and
    // silently had a different result type (§4.4). 22 such sites in one library, all repaired by
    // this claim and none of them by the four errors that made it visible.
    assertEmits(port(doubleBrace, new CollectionsTransform),
                "this.put(\"k\", \"v\").getOrElse(null.asInstanceOf[java.lang.String])")
  }

  /** an anonymous class of a type this phase does NOT map, nested inside one it does. */
  private val nestedForeignAnon =
    """package demo;
      |import java.util.*;
      |class Holder {
      |  List<?> made = new ArrayList<Object>() {{
      |    Runnable r = new Runnable() { public void run() { add("a"); } };
      |  }};
      |}
      |""".stripMargin

  test("a pending call inside a NON-collection anon is DROPPED, not claimed further out") {
    // java resolves the bare `add` to the enclosing anonymous `ArrayList`'s. Scala's `this` inside
    // the `Runnable` is the Runnable, and the enclosing anonymous class has no name to qualify
    // with — so there is no receiver to synthesise and the call is left exactly as java wrote it.
    // A rewrite here would emit `this += "a"` against a `Runnable`.
    val p = port(nestedForeignAnon, new CollectionsTransform)
    assertEmits(p, "add(\"a\")")
    assertNotEmits(p, "this += \"a\"")
  }

  // ---------------------------------------------------------------------------------------------
  // …and the two shapes the FRONTEND already supplies a receiver for. They pass before this fix and
  // must keep passing after it: the claim must not fire twice, and must not be what makes them work.
  // ---------------------------------------------------------------------------------------------

  private val namedSubclass =
    """package demo;
      |import java.util.*;
      |class Seeded extends ArrayList<String> {
      |  void seed() { add("a"); }
      |  int mine(String s) { return s.length(); }
      |  int useMine() { return mine("x"); }
      |}
      |""".stripMargin

  test("an unqualified inherited `add` in a NAMED subclass is already a `this.` receiver") {
    assertEmits(port(namedSubclass, new CollectionsTransform), "this += \"a\"")
  }

  test("a call to the class's OWN method is untouched — the rule reads the method's OWNER") {
    // `mine` is declared here, so its owner is not a key in this phase's `typeMap`. A rewrite keyed
    // on "am I inside a class that is a collection" rather than on the method would have taken it.
    assertEmits(port(namedSubclass, new CollectionsTransform), "this.mine(\"x\")")
  }

  private val throughAnonOuter =
    """package demo;
      |import java.util.*;
      |class Outer extends ArrayList<String> {
      |  Runnable r = new Runnable() { public void run() { add("a"); } };
      |}
      |""".stripMargin

  test("…and an enclosing NAMED collection is reached as `Outer.this`, which only the frontend can say") {
    assertEmits(port(throughAnonOuter, new CollectionsTransform), "Outer.this += \"a\"")
  }

  /** nothing in this program extends a mapped collection at all. */
  private val noCollectionAnywhere =
    """package demo;
      |class Plain {
      |  void add(String s) { }
      |  Runnable r = new Runnable() { public void run() { add("a"); } };
      |}
      |""".stripMargin

  test("a same-NAMED call that no mapped type declares is left alone — the negative") {
    val p = port(noCollectionAnywhere, new CollectionsTransform)
    assertEmits(p, "add(\"a\")")
    assertNotEmits(p, "+=")
  }
