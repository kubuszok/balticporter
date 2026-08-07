package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** A KIND THAT IS SEQ-SHAPED EVERYWHERE ELSE MUST BE SEQ-SHAPED AT THE SEAM TOO.
  *
  * `java.util.Stack` got its own `Kind` because ONE member of it — `peek()` — means the opposite
  * end from the `Deque` `peek` the `Kind.Seq` arms answer, and a shared arm cannot be both. That is
  * a fact about the CALL REWRITE table and about nothing else: the target `JavaStack` extends
  * `mutable.ArrayBuffer`, so as a VALUE it is exactly a `Kind.Seq` and conforms wherever one does.
  *
  * `coerce`'s factory table nonetheless names `Kind.Seq | Kind.Set | Kind.Map` in every arm and
  * `Kind.Stack` in none, so a stack at a bridged slot matched nothing and the boundary check
  * reported it — as an honest refusal, which is what the surrounding rows in that same count are.
  * `ENGINE-LIMITS.md` K2.5's shape exactly: a residue count is only as good as the assumption that
  * everything able to close it RAN, and here the factory existed and applied on its first line.
  *
  * The subtyping licence is IDENTICAL to `Kind.Seq`'s — `JavaIterable.from` takes a
  * `scala.collection.Iterable` and `JavaCollection.from` a `scala.collection.Seq`, and a
  * `JavaStack` is both — so the arms are shared rather than duplicated, and this is the test that
  * says the sharing is real rather than a claim in a comment.
  */
class CollectionsStackSeamSpec extends PortSuite:

  /** Nothing here names `Iterable`, so the `Collection` bridge is tested on its own terms
    * (`CollectionsBridgeGateSpec`'s rule, one kind over). */
  private val ownedCollection =
    """package demo;
      |import java.util.*;
      |class Holder {
      |  static Holder of(Collection<String> xs) { return new Holder(); }
      |  Holder push(Stack<String> s)            { return of(s); }
      |}
      |""".stripMargin

  private val ownedIterable =
    """package demo;
      |import java.util.*;
      |class Holder {
      |  static Holder each(Iterable<String> xs) { return new Holder(); }
      |  Holder push(Stack<String> s)            { return each(s); }
      |}
      |""".stripMargin

  /** the EXTERNAL half — a class file's `java.lang.Object` formal, where nothing is broken and
    * nothing compiles wrong, and the callee nevertheless sees a `JavaStack` where java handed it a
    * `java.util.Stack` (`CollectionsTransform.ObjectFqn`'s own reasoning). */
  private val externalUniversal =
    """package demo;
      |import java.util.*;
      |class Holder {
      |  void show(Stack<String> s) { System.out.println(s); }
      |}
      |""".stripMargin

  test("a Stack at an owned `Collection` formal takes the SAME factory a List does") {
    assertEmits(port(ownedCollection, new CollectionsTransform),
                "Holder.of(balticporter.runtime.JavaCollection.from(s))")
  }

  test("…and at an `Iterable` formal, the shim every kind reaches") {
    assertEmits(port(ownedIterable, new CollectionsTransform),
                "Holder.each(balticporter.runtime.JavaIterable.from(s))")
  }

  test("…and at an EXTERNAL universal formal, where no compile error would ever have said so") {
    assertEmits(port(externalUniversal, new CollectionsTransform),
                "balticporter.runtime.JavaCollections.toJava(s)")
  }
