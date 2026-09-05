package balticporter.corpus

import balticporter.core.PolicyIssue
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, DecisionLog, Pipeline, Program}
import balticporter.transform.PackageRenameTransform

/** `PackageRenameTransform` — the §1(b) phase that moves a port out of the upstream namespace. */
class PackageRenameTransformSpec extends munit.FunSuite:

  private val src =
    """package com.example.demo;
      |public class Widget {
      |  public static class Style { public int pad; }
      |  public class Handle { public int id; }
      |  private java.util.List<String> names = new java.util.ArrayList<String>();
      |  private Style style = new Style();
      |  public String label(int i) { return names.get(i); }
      |  public Widget copy() { return new Widget(); }
      |}
      |class Panel {
      |  Widget w = new Widget();
      |  Widget.Style s = new Widget.Style();
      |  Widget.Handle h;
      |}
      |""".stripMargin

  private val before = SpoonTir.fromSource(src)

  private def run(renames: Map[String, String]): Program =
    Pipeline.run(before, List(new PackageRenameTransform(renames)))

  private def names(p: Program): Set[String] = p.symbols.all.map(_.fullName).toSet
  private def emit(p: Program): String       = new TirEmitter(p).emit

  // ---------------------------------------------------------------------------
  // the no-op
  // ---------------------------------------------------------------------------

  test("empty map is a total no-op — every symbol and every byte of output is unchanged") {
    val after = run(Map.empty)
    assertEquals(names(after), names(before))
    // and the simple names too: a rename that only touched `name` would pass the check above.
    assertEquals(after.symbols.all.map(s => s.id -> s.name).toMap, before.symbols.all.map(s => s.id -> s.name).toMap)
    assertEquals(emit(after), emit(before))
    assertEquals(PackageRenameTransform.check(before, Map.empty).matched, Map.empty[String, Int])
  }

  // ---------------------------------------------------------------------------
  // the rename
  // ---------------------------------------------------------------------------

  private val renamed = run(Map("com.example" -> "sge.ui"))
  private val out     = emit(renamed)

  test("renames every owned symbol — types, members, fields and params follow the prefix") {
    assert(names(before).contains("com.example.demo.Widget"))
    assert(!names(renamed).contains("com.example.demo.Widget"), clue = "upstream name survived")
    assert(names(renamed).contains("sge.ui.demo.Widget"))
    assert(names(renamed).contains("sge.ui.demo.Panel"))
    // a MEMBER is `owner#name`; the `#` must be carried across, not cut at.
    assert(names(renamed).contains("sge.ui.demo.Widget#label"))
    assert(names(renamed).contains("sge.ui.demo.Widget#names"))
    // nothing at all is left in the upstream namespace.
    assertEquals(names(renamed).filter(_.startsWith("com.example")), Set.empty[String])
  }

  test("a NESTED type survives the rename — `$` is a boundary, and both path forms still render") {
    // symbol table: the `$` chain is carried verbatim behind the new prefix.
    assert(names(renamed).contains("sge.ui.demo.Widget$Style"))
    assert(names(renamed).contains("sge.ui.demo.Widget$Handle"))
    assert(names(renamed).contains("sge.ui.demo.Widget$Style#pad"))
    // emission: a STATIC nested type is a companion member (`.`), a genuine INNER class is a type
    // projection (`#`). Both are derived from the renamed `fullName` via the owner chain, so a
    // prefix cut in the wrong place would show up only here.
    assert(clue(out).contains("sge.ui.demo.Widget.Style"))
    assert(clue(out).contains("sge.ui.demo.Widget#Handle"))
  }

  test("emitted package clause and every reference move; no upstream name is left in the output") {
    assert(clue(out).contains("package sge.ui.demo"))
    assert(!out.contains("package com.example.demo"))
    assert(!out.contains("com.example"), clue = "upstream namespace leaked into emitted Scala")
  }

  // ---------------------------------------------------------------------------
  // what must NOT be renamed
  // ---------------------------------------------------------------------------

  test("an EXTERNAL symbol is never renamed, even when the map covers its prefix") {
    // a map that deliberately covers the stdlib: only ownership stops this rewriting `java.lang`.
    val hostile = run(Map("com.example" -> "sge.ui", "java" -> "jvm", "scala" -> "s"))
    val n       = names(hostile)
    assert(n.contains("java.lang.String"), clue = "the JDK was rewritten")
    assert(n.contains("java.util.List"))
    assert(n.contains("scala.Int"))
    assertEquals(n.filter(_.startsWith("jvm")), Set.empty[String])
    assert(!emit(hostile).contains("jvm."))
    assert(clue(emit(hostile)).contains("java.lang.String"))
    // the owned half of the same map still ran.
    assert(n.contains("sge.ui.demo.Widget"))
  }

  test("a prefix must end at a separator — `com.exampl` does not cover `com.example`") {
    val after = run(Map("com.exampl" -> "WRONG"))
    assertEquals(names(after), names(before))
    assertEquals(PackageRenameTransform.check(before, Map("com.exampl" -> "WRONG")).unmatched, List("com.exampl"))
  }

  test("longest prefix wins") {
    val after = run(Map("com.example" -> "a.x", "com.example.demo" -> "b.y.demo"))
    assert(names(after).contains("b.y.demo.Widget"))
    assertEquals(names(after).filter(_.startsWith("a.x")), Set.empty[String])
  }

  test("renaming a TYPE (not a package) also moves its simple name, so the emitter renders it") {
    val after = run(Map("com.example.demo.Widget" -> "com.example.demo.Gadget"))
    assert(names(after).contains("com.example.demo.Gadget"))
    assert(names(after).contains("com.example.demo.Gadget$Style"))
    val o = emit(after)
    assert(clue(o).contains("class Gadget"))
    assert(!o.contains("class Widget"))
  }

  // ---------------------------------------------------------------------------
  // the check (CLAUDE.md §3 — a translation path gets a check at the same time)
  // ---------------------------------------------------------------------------

  test("check counts what will move before the phase, and reports zero residue after it") {
    val policy = Map("com.example" -> "sge.ui")
    val pre    = PackageRenameTransform.check(before, policy)
    assert(pre.matched("com.example") > 0)
    assertEquals(pre.unmatched, Nil)
    // after the phase the SAME map must match nothing — any hit is an owned symbol the rename
    // failed to reach, which is the defect this check exists to catch.
    val post = PackageRenameTransform.check(renamed, policy)
    assertEquals(post.matched, Map.empty[String, Int])
    assertEquals(post.unmatched, List("com.example"))
    assert(post.render.contains("§1b"))
  }

  test("ownership is structural: every owned symbol roots at a unit, no external does") {
    val owned = PackageRenameTransform.ownedSymbols(before)
    val fq    = owned.flatMap(before.symbolOf).map(_.fullName)
    assert(fq.contains("com.example.demo.Widget"))
    assert(fq.contains("com.example.demo.Widget$Style"))
    assert(!fq.contains("java.lang.String"))
    assert(!fq.contains("java.util.List"))
  }

  // ---------------------------------------------------------------------------
  // M6 — the PER-TYPE maps: `typeRenames`, `subPackages`, `flattenNestedTypes`

  private def phase(
      pkg: Map[String, String] = Map.empty,
      types: Map[String, String] = Map.empty,
      subs: Map[String, String] = Map.empty,
      flat: Set[String] = Set.empty,
      allow: Set[String] = Set.empty,
  ) = new PackageRenameTransform(pkg, types, subs, flat, allow)

  /** run ONE instance, and keep it — the refusals and the decisions are read off the same value the
    * pipeline bound, which is exactly what `PortRun` now does with it. */
  private def runPhase(p: PackageRenameTransform, on: Program = before): (Program, DecisionLog) =
    Pipeline.runTraced(on, List(p))

  private def issues(p: PackageRenameTransform): List[(String, PolicyIssue)] =
    p.policyReport.findings.map(f => f.key -> f.issue).sortBy(_._1)

  test("every per-type map EMPTY is the same no-op the empty prefix map is — byte for byte") {
    val p            = phase()
    val (after, log) = runPhase(p)
    assertEquals(names(after), names(before))
    assertEquals(emit(after), emit(before))
    assertEquals(log.all, Nil)
    assertEquals(p.policyReport.findings, Nil)
    assertEquals(p.upstreamTable, Map.empty[String, String])
  }

  test("typeRenames: a BARE simple name renames the type in place; a dotted value is a whole FQN") {
    val inPlace = phase(types = Map("com.example.demo.Widget" -> "Gadget"))
    val (a, _)  = runPhase(inPlace)
    assert(names(a).contains("com.example.demo.Gadget"))
    assert(names(a).contains("com.example.demo.Gadget$Style"), clue = "a nested type must follow its enclosure")
    assert(names(a).contains("com.example.demo.Gadget#label"))
    assert(clue(emit(a)).contains("class Gadget"))

    val moved  = phase(types = Map("com.example.demo.Widget" -> "com.other.Gadget"))
    val (b, _) = runPhase(moved)
    assert(names(b).contains("com.other.Gadget"))
    assert(names(b).contains("com.other.Gadget$Handle"))
    assert(clue(emit(b)).contains("package com.other"))
  }

  test("a typeRenames TARGET is written UPSTREAM: the package rename applies to it, once") {
    val p      = phase(pkg = Map("com.example" -> "sge.ui"), types = Map("com.example.demo.Widget" -> "Gadget"))
    val (a, _) = runPhase(p)
    assertEquals(p.emittedName("com.example.demo.Widget"), "sge.ui.demo.Gadget")
    assertEquals(p.emittedName("com.example.demo.Panel"), "sge.ui.demo.Panel")
    assert(names(a).contains("sge.ui.demo.Gadget"))
    assertEquals(names(a).filter(_.startsWith("com.example")), Set.empty[String])
  }

  test("a per-TYPE key cuts at a SEPARATOR too — `…Widget` does not cover `…Widgetry`") {
    val src2 = """package com.example.demo;
                 |public class Widget { public int a; }
                 |class Widgetry { Widget w = new Widget(); }
                 |""".stripMargin
    val prog   = SpoonTir.fromSource(src2)
    val p      = phase(types = Map("com.example.demo.Widget" -> "Gadget"))
    val (a, _) = Pipeline.runTraced(prog, List(p))
    val n      = a.symbols.all.map(_.fullName).toSet
    assert(n.contains("com.example.demo.Gadget"))
    assert(clue(n).contains("com.example.demo.Widgetry"), clue = "a prefix that does not cut at a separator moved a sibling")
    assert(!n.contains("com.example.demo.Gadgetry"))
  }

  test("a key naming a type this program does not DECLARE is refused, never silently applied") {
    val jdk    = phase(types = Map("java.util.List" -> "sge.Seq"))
    val (a, _) = runPhase(jdk)
    assertEquals(names(a), names(before))
    assertEquals(issues(jdk), List("java.util.List" -> PolicyIssue.NeverMatched))
    assert(jdk.policyReport.findings.head.detail.contains("REFERENCES and does not DECLARE"))

    val typo   = phase(types = Map("com.example.demo.Widgt" -> "Gadget"))
    val (b, _) = runPhase(typo)
    assertEquals(names(b), names(before))
    assertEquals(issues(typo), List("com.example.demo.Widgt" -> PolicyIssue.NeverMatched))
  }

  test("a DESTINATION that is already taken is a COLLISION, refused loudly, and nothing moves") {
    val p      = phase(types = Map("com.example.demo.Widget" -> "Panel"))
    val (a, _) = runPhase(p)
    assertEquals(names(a), names(before), "a refused rename must leave the program alone")
    assertEquals(issues(p), List("com.example.demo.Widget" -> PolicyIssue.Malformed))
    assert(clue(p.policyReport.findings.head.detail).contains("com.example.demo.Panel"))

    // …and two entries aimed at ONE destination are the same collision from the other side.
    val twin   = phase(types = Map("com.example.demo.Widget" -> "Z", "com.example.demo.Panel" -> "Z"))
    val (b, _) = runPhase(twin)
    assertEquals(names(b), names(before))
    assertEquals(issues(twin).map(_._2), List(PolicyIssue.Malformed, PolicyIssue.Malformed))
  }

  test("one type, ONE destination: a key named by two maps is refused on both") {
    val p = phase(types = Map("com.example.demo.Widget" -> "Gadget"),
                  subs = Map("com.example.demo.Widget" -> "internal"))
    val (a, _) = runPhase(p)
    assertEquals(names(a), names(before))
    assertEquals(p.policyReport.findings.size, 2)
    assert(p.policyReport.findings.forall(_.detail.contains("more than one of")))
  }

  test("subPackages nests a type in place, and refuses a nested type and a `$` in the value") {
    val p      = phase(subs = Map("com.example.demo.Widget" -> "internal"))
    val (a, _) = runPhase(p)
    assert(names(a).contains("com.example.demo.internal.Widget"))
    assert(names(a).contains("com.example.demo.internal.Widget$Style"))
    assert(names(a).contains("com.example.demo.Panel"), clue = "a sibling must not move")
    assert(clue(emit(a)).contains("package com.example.demo.internal"))

    val nested = phase(subs = Map("com.example.demo.Widget$Style" -> "internal"))
    runPhase(nested)
    assertEquals(issues(nested), List("com.example.demo.Widget$Style" -> PolicyIssue.Malformed))
  }

  test("flattenNestedTypes promotes a STATIC nested type to a unit — its own file and package clause") {
    val p      = phase(flat = Set("com.example.demo.Widget$Style"))
    val (a, _) = runPhase(p)
    assertEquals(p.policyReport.findings, Nil)
    assert(names(a).contains("com.example.demo.Style"))
    assert(!names(a).contains("com.example.demo.Widget$Style"))
    assert(names(a).contains("com.example.demo.Style#pad"))
    // it is a UNIT now: its own emitted file, with its own package clause.
    assertEquals(a.units.count(u => a.symbolOf(u.symbol).exists(_.fullName == "com.example.demo.Style")), 1)
    // …and the enclosing type no longer declares it.
    val widget = a.units.find(u => a.symbolOf(u.symbol).exists(_.fullName == "com.example.demo.Widget")).get
    assert(!widget.body.exists {
      case c: balticporter.tir.Tree.ClassDef => a.symbolOf(c.symbol).exists(_.name == "Style")
      case _                                 => false
    })
    // every reference followed — nothing names the old path.
    assert(!clue(emit(a)).contains("Widget.Style"))
  }

  test("a NON-STATIC inner class cannot be flattened: it carries an implicit enclosing instance") {
    val p      = phase(flat = Set("com.example.demo.Widget$Handle"))
    val (a, _) = runPhase(p)
    assertEquals(names(a), names(before))
    assertEquals(issues(p), List("com.example.demo.Widget$Handle" -> PolicyIssue.Malformed))
    assert(clue(p.policyReport.findings.head.detail).contains("STATIC"))
  }

  test("`flattenNestedTypes` naming a type with no `$` is malformed, not a silent no-op") {
    val p = phase(flat = Set("com.example.demo.Panel"))
    runPhase(p)
    assertEquals(issues(p), List("com.example.demo.Panel" -> PolicyIssue.Malformed))
  }

  // ---------------------------------------------------------------------------
  // DECISION PROVENANCE — a per-type move is a `RenamedType`, not a `RenamedPackage`
  // ---------------------------------------------------------------------------

  test("a per-type move records RenamedType; a prefix move records RenamedPackage") {
    val p        = phase(pkg = Map("com.example" -> "sge.ui"), types = Map("com.example.demo.Widget" -> "Gadget"))
    val (_, log) = runPhase(p)
    val byKind   = log.all.groupBy(_.kind).view.mapValues(_.map(_.subjectFqn).sorted).toMap
    assertEquals(byKind.getOrElse(Decision.Kind.RenamedType, Nil), List("com.example.demo.Widget"))
    assertEquals(byKind.getOrElse(Decision.Kind.RenamedPackage, Nil), List("com.example.demo.Panel"))
    val d = log.all.find(_.kind == Decision.Kind.RenamedType).get
    assertEquals(d.detail("to"), "sge.ui.demo.Gadget")
    assertEquals(d.reason.detail, "package-rename:com.example.demo.Widget -> sge.ui.demo.Gadget")
    assert(d.detail.contains("why"))
  }

  // ---------------------------------------------------------------------------
  // THE PACKAGE-SPLIT RULE (DESIGN.md §8.7) — M6 falsifies the no-split premise
  // ---------------------------------------------------------------------------

  private val splitSrc =
    """package com.example.demo;
      |public class Alpha {
      |  protected int shared() { return 1; }
      |  public int own() { return shared(); }
      |}
      |class Beta {
      |  int use(Alpha a) { return a.shared(); }
      |}
      |class Gamma {
      |  public int plain(Alpha a) { return a.own(); }
      |}
      |""".stripMargin
  private val split = SpoonTir.fromSource(splitSrc)

  test("split REFUSED: a move that puts a `protected` member across a new package boundary") {
    val p        = phase(types = Map("com.example.demo.Alpha" -> "com.other.Alpha"))
    val (a, log) = runPhase(p, split)
    assertEquals(a.symbols.all.map(_.fullName).toSet, split.symbols.all.map(_.fullName).toSet,
                 "a refused split must leave every name where it was")
    assertEquals(issues(p), List("com.example.demo.Alpha" -> PolicyIssue.Unverifiable))
    val why = p.policyReport.findings.head.detail
    assert(clue(why).contains("package-split"))
    assert(clue(why).contains("com.example.demo.Alpha#shared"))
    assert(clue(why).contains("allowPackageSplit"), clue = "a refusal must say how to declare the move")
    assertEquals(log.all.count(_.kind == Decision.Kind.WidenedVisibility), 0)
    assertEquals(p.recordedWidenings, Nil)
  }

  test("split RECORDED: declared deliberate, it happens and each affected declaration gets a row") {
    val p = phase(types = Map("com.example.demo.Alpha" -> "com.other.Alpha"),
                  allow = Set("com.example.demo.Alpha"))
    val (a, log) = runPhase(p, split)
    assertEquals(issues(p), Nil)
    assert(a.symbols.all.map(_.fullName).toSet.contains("com.other.Alpha"))
    val rows = log.all.filter(_.kind == Decision.Kind.WidenedVisibility)
    assertEquals(rows.map(_.subjectFqn), List("com.example.demo.Alpha#shared"))
    assertEquals(rows.head.detail("cause"), "package-split")
    assertEquals(rows.head.detail("type"), "com.example.demo.Alpha")
    assertEquals(rows.head.detail("reader"), "com.example.demo.Beta#use")
    // §1's classification is on the row, because which repository the fix lives in is the reader's
    // first question — and this one is CONFIGURED, exactly as `package-merge` is.
    assertEquals(rows.head.reason.className, "configured")
  }

  test("no split: a move with no restricted member across the old boundary is SILENT") {
    val p        = phase(types = Map("com.example.demo.Gamma" -> "com.other.Gamma"))
    val (a, log) = runPhase(p, split)
    assertEquals(issues(p), Nil)
    assert(a.symbols.all.map(_.fullName).toSet.contains("com.other.Gamma"))
    assertEquals(log.all.count(_.kind == Decision.Kind.WidenedVisibility), 0)
    assertEquals(p.recordedWidenings, Nil)
  }

  test("a SUB-PACKAGE move blocks only the OUTGOING half — subpackage nesting widens, never blocks") {
    // DESIGN.md §8.7. Scala's `private[p]` covers `p` AND its subpackages, so nesting a type under
    // `p.internal` keeps everything `p` restricts reachable FROM it. What it does take away is the
    // other direction, and only that half is a split.
    val incoming =
      """package com.example.demo;
        |public class Host { protected int shared() { return 1; } }
        |class Moving { int use(Host h) { return h.shared(); } }
        |""".stripMargin
    val p = phase(subs = Map("com.example.demo.Moving" -> "internal"))
    val (a, _) = Pipeline.runTraced(SpoonTir.fromSource(incoming), List(p))
    assertEquals(issues(p), Nil, clue = "the moved type still reads its old package's `protected`")
    assert(a.symbols.all.map(_.fullName).toSet.contains("com.example.demo.internal.Moving"))

    // …and the SAME two types with the restricted member on the moving side is a split.
    val outgoing =
      """package com.example.demo;
        |public class Moving { protected int shared() { return 1; } }
        |class Host { int use(Moving m) { return m.shared(); } }
        |""".stripMargin
    val q = phase(subs = Map("com.example.demo.Moving" -> "internal"))
    Pipeline.runTraced(SpoonTir.fromSource(outgoing), List(q))
    assertEquals(issues(q), List("com.example.demo.Moving" -> PolicyIssue.Unverifiable))
  }

  test("an `allowPackageSplit` entry that declares nothing is itself a finding") {
    val p = phase(types = Map("com.example.demo.Gamma" -> "com.other.Gamma"),
                  allow = Set("com.example.demo.Gamma"))
    runPhase(p, split)
    assertEquals(issues(p), List("com.example.demo.Gamma" -> PolicyIssue.NeverMatched))
  }

  test("flattening that breaks Java's `private[TopLevel]` boundary is refused as an enclosure split") {
    val src2 =
      """package com.example.demo;
        |public class Host {
        |  private static int secret = 1;
        |  public static class Guest { int peek() { return secret; } }
        |}
        |""".stripMargin
    val prog   = SpoonTir.fromSource(src2)
    val p      = phase(flat = Set("com.example.demo.Host$Guest"))
    val (a, _) = Pipeline.runTraced(prog, List(p))
    assertEquals(a.symbols.all.map(_.fullName).toSet, prog.symbols.all.map(_.fullName).toSet)
    assertEquals(issues(p), List("com.example.demo.Host$Guest" -> PolicyIssue.Unverifiable))
    assert(clue(p.policyReport.findings.head.detail).contains("enclosure-split"))
  }

  // ---------------------------------------------------------------------------
  // the table the RUN reads for BOTH namespaces (§4.56), and the check
  // ---------------------------------------------------------------------------

  test("the accepted table carries both namespaces, and a REFUSED entry is not in it") {
    val ok = phase(pkg = Map("com.example" -> "sge.ui"), types = Map("com.example.demo.Widget" -> "Gadget"))
    runPhase(ok)
    assertEquals(ok.upstreamTable("com.example.demo.Widget"), "sge.ui.demo.Gadget")

    val no = phase(pkg = Map("com.example" -> "sge.ui"), types = Map("com.example.demo.Widget" -> "Panel"))
    runPhase(no)
    assertEquals(no.upstreamTable.get("com.example.demo.Widget"), scala.None)
    assertEquals(no.emittedName("com.example.demo.Widget"), "sge.ui.demo.Widget")
  }

  test("check reports zero residue for a per-type entry too, after the phase") {
    val p      = phase(types = Map("com.example.demo.Widget" -> "Gadget"))
    val (a, _) = runPhase(p)
    assert(PackageRenameTransform.check(before, p.upstreamTable).matched("com.example.demo.Widget") > 0)
    assertEquals(PackageRenameTransform.check(a, p.upstreamTable).matched, Map.empty[String, Int])
  }

  // ---------------------------------------------------------------------------
  // an UNOWNED symbol under a port prefix — CLAUDE.md §4.56: a class-file FQN no phase may move
  // ---------------------------------------------------------------------------

  private val withExternal = SpoonTir.fromSource(
    """package com.example.demo;
      |public class Uses {
      |  public boolean go() { return com.example.ext.Loader.isMac; }
      |}
      |""".stripMargin)

  private val extFqn = "com.example.ext.Loader"

  /** the same program with `Loader` marked RESOLVED — what a frontend classpath holding the class
    * file produces, which no unit-test source root can supply. */
  private def resolvingExternal: Program =
    val t = withExternal.symbols.all.foldLeft(withExternal.symbols) { (acc, s) =>
      if s.fullName.startsWith(extFqn) then acc.updated(s.copy(flags = s.flags.copy(isResolved = true)))
      else acc
    }
    withExternal.rebuilt(symbols = t)

  private val toSge = Map("com.example" -> "sge.ui")

  test("an UNRESOLVED external under the port's prefix moves — nothing says its FQN is fixed") {
    assert(withExternal.symbols.all.exists(s => s.fullName == extFqn && !s.flags.isResolved),
           clue = "the frontend claimed a resolution it does not have")
    val after = Pipeline.run(withExternal, List(new PackageRenameTransform(toSge)))
    assert(names(after).contains("sge.ui.ext.Loader"))
    assert(!names(after).contains(extFqn))
  }

  test("a RESOLVED, undropped external under the prefix keeps its class-file FQN") {
    val after = Pipeline.run(resolvingExternal, List(new PackageRenameTransform(toSge)))
    assert(names(after).contains(extFqn), clue = "a resolved external was moved out of its namespace")
    assert(!names(after).contains("sge.ui.ext.Loader"))
    // the port's OWN declarations still move — the rule narrows nothing else
    assert(names(after).contains("sge.ui.demo.Uses"))
  }

  test("…and moves after all when the port DROPS it: the replacement is the port's own") {
    val after = Pipeline.run(resolvingExternal,
                             List(new PackageRenameTransform(toSge, drops = Set(extFqn))))
    assert(names(after).contains("sge.ui.ext.Loader"))
    assert(!names(after).contains(extFqn))
  }

  test("a symbol outside every port prefix is untouched, resolved or not") {
    val after = Pipeline.run(withExternal, List(new PackageRenameTransform(toSge)))
    assert(!names(after).exists(_.startsWith("sge.ui.java")))
  }

  test("…or when the port INJECTS ready-made Scala at the RENAMED name: upstream declares it nowhere") {
    val after = Pipeline.run(resolvingExternal,
                             List(new PackageRenameTransform(toSge, injected = Set("sge.ui.ext.Loader"))))
    assert(names(after).contains("sge.ui.ext.Loader"))
    assert(!names(after).contains(extFqn))
  }

  test("an injection at some OTHER name moves nothing — the set is matched, never approximated") {
    val after = Pipeline.run(resolvingExternal,
                             List(new PackageRenameTransform(toSge, injected = Set("sge.ui.ext.Other"))))
    assert(names(after).contains(extFqn))
  }

  test("a MEMBER of a resolved external stays with its owner — resolution is the TYPE's fact") {
    val after = Pipeline.run(resolvingExternal, List(new PackageRenameTransform(toSge)))
    assert(!names(after).exists(_.startsWith("sge.ui.ext.Loader")))
  }
