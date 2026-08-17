package balticporter.corpus

import balticporter.testkit.PortSuite

/** A RAW BOUND'S NAME-FILL IS LICENSED PER SLOT — `ENGINE-LIMITS.md` G30, `CLAUDE.md` §4.56 read at
  * a bound.
  *
  * `N extends Node` is java's F-BOUND idiom and the name-directed fill exists to preserve it; JLS 4.8
  * then stops checking, so the SAME syntax at a type that is not the declaring one is a coincidence
  * of two names, and filling it re-imposes bounds java never checked. Both blanket answers are
  * refuted with a counter-example each and both are negatives here:
  *
  *  - scoping the fill to the DECLARING type would take the third test's fill away (a raw bound at a
  *    type that does not declare it, whose fill libGDX needs and whose port compiles at 0);
  *  - erasing every raw bound to `Foo[?, ?, ?]` would take the fourth's, which is `fbound`'s own
  *    record.
  *
  * What separates them is whether the in-scope variable can STAND in the slot, decided from what
  * java WROTE — same declaration, unbounded formal, or the same bound spelled the same way — never
  * from a conformance lookup a `noClasspath` model can answer `false` for a readable hierarchy.
  */
class RawBoundNameFillSpec extends PortSuite:

  /** G30's site, reduced: `ReferencingNode<R extends NodeRepository<B>, B extends ReferenceNode>`
    * over `ReferenceNode<R extends NodeRepository<B>, B extends Node, N extends Node>`. The names
    * `R` and `B` line up and mean different things: `ReferenceNode`'s slot 1 asks for a `Node` and
    * this `B` is declared a `ReferenceNode`. */
  private val coincidence =
    """package demo;
      |class Node { }
      |abstract class Repo<T> { }
      |interface Ref<R extends Repo<B>, B extends Node, N extends Node> { }
      |interface Referencing<R extends Repo<B>, B extends Ref> {
      |  B get(R repository);
      |}
      |""".stripMargin

  test("a matching NAME at a slot whose bound the variable does not discharge is not filled") {
    val p = port(coincidence)
    // The fill would read `Ref[R, B, ?]`, which imposes `B <: Node` on a `B` declared `B <: Ref` —
    // `E057 Type argument B does not conform to upper bound Node`, and invisible until the port is
    // at 0 typer errors, since the applied-type check does not run before then (§3).
    assertNotEmits(p, "B <: demo.Ref[R, B, ?]")
    // …and slot 0 goes WITH it: scalac substitutes a declined slot as a PROJECTION rather than as a
    // wildcard, so `Ref[R, ?, ?]` reads `R does not conform to Repo[Ref[R, ?, ?]#B]`. A slot whose
    // formal bound MENTIONS a declined formal is declined too — the fixpoint, not a per-slot test.
    assertNotEmits(p, "B <: demo.Ref[R, ?, ?]")
    assertEmits(p, "trait Referencing[R <: demo.Repo[B], B <: demo.Ref[?, ?, ?]]")
  }

  test("a slot the decline does NOT reach keeps its fill — this is not a blanket erasure") {
    // `Holder`'s slot 0 asks for `Thing` and `Q`'s `X` is declared `Thing`: licensed. Its slot 1 asks
    // for `Other` and `Q`'s `Y` is declared `Holder`: declined. Slot 0's bound does not mention slot
    // 1, so nothing propagates and the fill keeps what it can say.
    val p = port(
      """package demo2;
        |class Thing { }
        |class Other { }
        |interface Holder<X extends Thing, Y extends Other> { }
        |interface Q<X extends Thing, Y extends Holder> {
        |  X first();
        |}
        |""".stripMargin
    )
    assertEmits(p, "trait Q[X <: demo2.Thing, Y <: demo2.Holder[X, ?]]")
  }

  test("libGDX's counter-example: a raw bound at a NON-declaring type whose bound really does line up") {
    // `class Tree<N extends Node, V>` over `class Node<N extends Node, V, A extends Actor>`. `Node`'s
    // slot 0 asks for a `Node` and `Tree`'s `N` is declared `Node`; slot 1 is unbounded. Scoping the
    // fill to the declaring type would erase both and cost this family its self-reference — that
    // port compiles at 0 errors with the fill exactly as written here.
    val p = port(
      """package demo3;
        |class Actor { }
        |abstract class Node<N extends Node, V, A extends Actor> { }
        |class Tree<N extends Node, V> {
        |  N root;
        |}
        |""".stripMargin
    )
    assertEmits(p, "class Tree[N <: demo3.Node[N, V, ?], V <: java.lang.Object]")
  }

  test("the F-BOUND idiom itself: a raw bound at the DECLARING type is the identity substitution") {
    val p = port(
      """package demo4;
        |abstract class Item<N extends Item, V> {
        |  N self() { return null; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "class Item[N <: Item[N, V], V <: java.lang.Object]")
  }
