package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** A KIND THAT IS SEQ-SHAPED EVERYWHERE ELSE MUST BE SEQ-SHAPED AT THE SEAM TOO. */
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
