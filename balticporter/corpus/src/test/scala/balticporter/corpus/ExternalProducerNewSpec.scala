package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.Pipeline
import balticporter.transform.CollectionsTransform

/** The external-producer bridge is about what a callee HANDS BACK — a `new` hands back nothing of
  * java's (`CLAUDE.md` §4.56). */
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
