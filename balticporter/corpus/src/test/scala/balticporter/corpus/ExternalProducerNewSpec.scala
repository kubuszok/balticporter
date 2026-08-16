package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.Pipeline
import balticporter.transform.CollectionsTransform

/** The external-producer bridge is about what a callee HANDS BACK — a `new` hands back nothing of
  * java's (`CLAUDE.md` §4.56).
  *
  * ==The defect==
  * `externalProducer` wraps a value an external callee returns: the class file says
  * `java.util.List` and this port wants the scala view, so `JavaCollections.fromJava` is the
  * faithful bridge. Both of its tests answer YES for an anonymous class implementing a JDK
  * interface, and both answer correctly — `new java.util.Iterator<E>(){ … }` really does name an
  * external constructor, and the node's type really is a shim this phase retyped to. So the port
  * emitted
  *
  * {{{
  *   val NONE: JavaIterator[String] = JavaCollections.fromJava(new JavaIterator[String]() { … })
  * }}}
  *
  * — a converter FROM java wrapped around an object the program had just built, which already IS
  * the shim. The error names the HELPER (`E134 None of the overloaded alternatives of method
  * fromJava`) rather than any boundary, which is the one shape that seam is explicitly built never
  * to produce.
  *
  * A test on the node's TYPE cannot separate the two cases: a constructed value and a returned one
  * have the same type by construction. That is why nothing here was looking, and why the question
  * has to be asked of the NODE — the applied function is a `new`, or the resolved method is an
  * initialiser.
  *
  * ==The positive direction is pinned elsewhere, deliberately==
  * `CollectionsCarrierSpec` asserts that a real external CALL still gets the wrap
  * (`fromJava(mapper.convertValue(…))`) and that removing the reason removes the wrap. Restating it
  * here would be a second opinion about one seam; what this spec owns is the shape that must NOT be
  * bridged.
  */
class ExternalProducerNewSpec extends PortSuite:

  private val src =
    """package extnew;
      |import java.util.ArrayList;
      |import java.util.Iterator;
      |import java.util.List;
      |class Holder {
      |  /** an anonymous class IMPLEMENTING a JDK interface the phase retypes to a shim. */
      |  static final Iterator<String> NONE = new Iterator<String>() {
      |    public boolean hasNext() { return false; }
      |    public String next() { return null; }
      |  };
      |  /** an ordinary `new` of a retyped collection — the same question with no anonymous body. */
      |  static final List<String> EMPTY = new ArrayList<String>();
      |}
      |""".stripMargin

  private val after = Pipeline.run(SpoonTir.fromSource(src), List(new CollectionsTransform))
  private val out   = new TirEmitter(after).emit

  test("an anonymous class implementing a retyped JDK interface is NOT bridged from java") {
    assert(clue(out).contains("val NONE: balticporter.runtime.JavaIterator[java.lang.String] = " +
                              "new balticporter.runtime.JavaIterator[java.lang.String]()"))
    assert(!out.contains("fromJava(new balticporter.runtime.JavaIterator"))
  }

  test("…and neither is a plain `new` of a retyped collection") {
    assert(clue(out).contains("new scala.collection.mutable.ArrayBuffer[java.lang.String]()"))
    assert(!out.contains("fromJava(new scala.collection.mutable"))
  }

  test("the anonymous body still overrides the SHIM's members, at java's arity") {
    assert(clue(out).contains("override def hasNext(): scala.Boolean"))
    assert(out.contains("override def next(): java.lang.String"))
  }
