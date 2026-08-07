package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.Pipeline
import balticporter.transform.CollectionsTransform

/** `java.util.Vector` and `java.util.Stack` — java's two LEGACY sequences, both absent from
  * Scala.js and `Stack` from Scala Native too.
  *
  * The whole content of this spec is that BOTH map to `mutable.ArrayBuffer`, and it is worth a
  * suite of its own because the obvious answer for the second one is wrong in a way no compile can
  * see. `scala.collection.mutable.Stack` has java's `push`/`pop`/`peek` and is an `ArrayDeque`
  * whose `push` PREPENDS — so its element 0 is the TOP, while java's `Stack extends Vector extends
  * List` puts the top LAST. Every list-shaped read of the same object (a `for`, a `get(i)`, an
  * `indexOf`, a `toString`) then answers in the opposite order with a green compile, which is
  * CLAUDE.md §4.4's defect class arriving through a type mapping.
  *
  * So `peek` is the arm to watch: at `Kind.Seq` it is the DEQUE `peek` — `headOption.orNull`, the
  * FIRST element, null when empty — and java's `Stack.peek()` is the LAST element and THROWS. Both
  * spellings compile; only one is java.
  */
class CollectionsLegacySeqSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.Stack;
      |import java.util.Vector;
      |class Frames {
      |  private Stack<String> stack = new Stack<String>();
      |  private Vector<String> legacy = new Vector<String>();
      |  void push(String s) { stack.push(s); }
      |  String pop() { return stack.pop(); }
      |  String top() { return stack.peek(); }
      |  boolean none() { return stack.empty(); }
      |  int find(String s) { return stack.search(s); }
      |  String bottom() { return stack.get(0); }
      |  int depth() { return stack.size(); }
      |  void keep(String s) { legacy.add(s); }
      |  String at(int i) { return legacy.get(i); }
      |}
      |""".stripMargin

  private val out =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(new CollectionsTransform))).emit

  test("Vector is an ArrayBuffer and Stack is the shim — one relation, two types") {
    // `Stack extends Vector extends List`, so the targets must keep that relation: `JavaStack`
    // EXTENDS `ArrayBuffer`, which is what makes `Vector<String> v = aStack` still type-check.
    assert(clue(out).contains("scala.collection.mutable.ArrayBuffer[java.lang.String]"))
    assert(clue(out).contains("balticporter.runtime.JavaStack[java.lang.String]"))
    assert(!out.contains("scala.collection.mutable.Stack"), "the order-inverting target must not be emitted")
    assert(!out.contains("java.util.Stack") && !out.contains("java.util.Vector"))
  }

  test("push, pop, peek and search are LEFT ALONE — the shim declares them with java's contracts") {
    // The faithful rewrite is no rewrite, and the arm that says so is not an omission: falling
    // through, `peek()` would reach the `Kind.Seq` DEQUE arm — the FIRST element and `null` when
    // empty, where java's is the LAST and a throw.
    assert(clue(out).contains("this.stack.push(s)"))
    assert(clue(out).contains("this.stack.pop()"))
    assert(clue(out).contains("this.stack.peek()"))
    assert(clue(out).contains("this.stack.search(s)"))
    assert(!out.contains("this.stack.headOption"), "the Deque `peek` arm answered a Stack receiver")
  }

  test("empty() is RENAMED to isEmpty, never left alone") {
    // `empty` on a `Buffer` is the companion's FACTORY, so the untouched name would compile
    // somewhere else entirely — the paren strip `parenless` does is not enough here.
    assert(clue(out).contains("this.stack.isEmpty"))
    assert(!out.contains("this.stack.empty"))
  }

  test("everything else a Stack is sent is answered by the Seq arms, through the re-entry") {
    // `Stack extends Vector extends List`: `get`/`size` are List's members and get List's rewrites,
    // which is the fallback arm at the foot of `rewrite` and not a second copy of the table.
    assert(clue(out).contains("this.stack(0)"))   // get(i) -> apply
    assert(clue(out).contains("this.stack.size")) // parenless
    assert(clue(out).contains("this.legacy(i)"))  // the same arm, reached at Kind.Seq
    assert(clue(out).contains("this.legacy += s"))
  }

  test("the mapping is in the phase's own fingerprint, so a port map can see an engine that moved") {
    // Not a constructor parameter and surface all the same: a base ported before this mapping and a
    // dependent ported after emit `java.util.Stack` against `ArrayBuffer` at the same slot, and the
    // published `policy=` digest is the only thing that could ever say so (`engine=` is a release
    // string, and the source digest is about the base's unchanged Java).
    val fp = new CollectionsTransform().surfaceFingerprint
    assert(clue(fp).contains("mapping="), "the mapping table does not reach the fingerprint")
  }
