package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, UsageKind}
import balticporter.transform.CollectionsTransform

/** The java→scala collections transform: retypes every collection occurrence and rewrites
  * the common call shapes, whole-program and symbol-driven. Asserts both the xref (the old
  * type is vacated, the new one inherits its positions) and the emitted Scala. */
class CollectionsTransformSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |import java.util.*;
      |class Bag {
      |  private List<String> items = new ArrayList<String>();
      |  private Map<String, Integer> counts = new HashMap<String, Integer>();
      |  void add(String s) { items.add(s); counts.put(s, items.size()); }
      |  String first() { return items.get(0); }
      |  boolean empty() { return items.isEmpty(); }
      |}
      |""".stripMargin

  private val before = SpoonTir.fromSource(src)
  private val after  = Pipeline.run(before, List(new CollectionsTransform))
  private val out    = new TirEmitter(after).emit

  private def id(p: balticporter.tir.Program, full: String) =
    p.symbols.all.find(_.fullName == full).map(_.id)

  test("retypes every java.util.List occurrence to scala Buffer (whole-program)") {
    val listId   = id(before, "java.util.List").getOrElse(fail("no java.util.List"))
    // before: java.util.List is used (field type, type arg positions); after: vacated.
    assert(before.usagesOf(listId).nonEmpty)
    assertEquals(after.usagesOf(listId), Nil)
    // the scala Buffer symbol now carries usages.
    val bufId = id(after, "scala.collection.mutable.Buffer").getOrElse(fail("no Buffer symbol"))
    assert(after.usages(bufId).map(_.kind).contains(UsageKind.Tycon))
  }

  test("emits scala collection types and rewritten calls") {
    assert(clue(out).contains("scala.collection.mutable.Buffer[java.lang.String]"))
    assert(out.contains("new scala.collection.mutable.ArrayBuffer["))
    assert(out.contains("scala.collection.mutable.HashMap["))
    assert(out.contains("this.items += s"))          // add -> +=
    assert(out.contains("this.counts.update(s,"))    // put -> update
    assert(out.contains("this.items(0)"))            // get(i) -> apply
    assert(out.contains("this.items.isEmpty\n") || out.contains("this.items.isEmpty "))  // drop ()
    assert(!out.contains("java.util.List"))          // nothing left un-migrated
  }
