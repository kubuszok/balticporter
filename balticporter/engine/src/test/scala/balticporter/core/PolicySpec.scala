package balticporter.core

import balticporter.tir.*
import balticporter.tir.TypeRepr.*
import balticporter.transform.{ClassTableTransform, StaticForwarderTransform}

/** A §1(b) rule's POLICY is a bag of strings the compiler cannot check, so a typo in it is a
  * silent no-op: the phase runs, matches nothing, and the port keeps the very construct the policy
  * was written to remove. These pin the complaint — and, just as importantly, pin that a CORRECT
  * key produces no complaint, because a check that cries wolf is turned off and then it is not a
  * check at all. */
class PolicySpec extends munit.FunSuite:

  // ---- a tiny program: `com.x.Wrapper` with three statics, and one class that could call them.
  private val CALLER  = SymId(1)
  private val WRAPPER = SymId(2)
  private val FORNAME = SymId(3)
  private val SIMPLE1 = SymId(4) // getSimpleName(Class)
  private val SIMPLE2 = SymId(5) // getSimpleName(Class, boolean) — an OVERLOAD of the same name
  private val NOW     = SymId(6) // now() — no parameters at all
  private val STRING  = SymId(7)
  private val CLASS   = SymId(8)

  private val O = Origin.synthetic
  private def ref(id: SymId) = TypeRef(NoPrefix, id)
  private def m(ps: TypeRepr*) = MethodType(ps.toList.zipWithIndex.map((t, i) => (s"p$i", t)), ref(STRING))

  private val caller = Tree.ClassDef(CALLER, parents = Nil, selfType = None, body = Nil, origin = O)
  // The wrapper is a UNIT this program declares, so its members are OWNED (`Program.owned` climbs
  // to a `units` symbol, §4.56). Without it every key here would bind `ExternalOnly` — correctly,
  // since a policy that rewrites declarations has nothing to rewrite in a type it only references.
  private val wrapper = Tree.ClassDef(WRAPPER, parents = Nil, selfType = None, body = Nil, origin = O)

  private val symbols = SymbolTable(
    List(
      Symbol(CALLER, "Caller", "demo.Caller", Flags(), SymId.None, ref(CALLER)),
      Symbol(WRAPPER, "Wrapper", "com.x.Wrapper", Flags(), SymId.None, ref(WRAPPER)),
      Symbol(FORNAME, "forName", "com.x.Wrapper#forName", Flags(isStatic = true), WRAPPER, m(ref(STRING))),
      Symbol(SIMPLE1, "getSimpleName", "com.x.Wrapper#getSimpleName", Flags(isStatic = true), WRAPPER, m(ref(CLASS))),
      Symbol(SIMPLE2, "getSimpleName", "com.x.Wrapper#getSimpleName(2)", Flags(isStatic = true), WRAPPER,
             m(ref(CLASS), ref(STRING))),
      Symbol(NOW, "now", "com.x.Wrapper#now", Flags(isStatic = true), WRAPPER, m()),
      Symbol(STRING, "String", "java.lang.String", Flags(), SymId.None, NoType),
      Symbol(CLASS, "Class", "java.lang.Class", Flags(), SymId.None, NoType),
    )
  )

  private val units = List(caller, wrapper)
  private def program(): Program = new Program(units, symbols, Xref.build(units), MemberIndex.empty)

  /** BIND, then run — the order a `PortRun` uses, and the order a phase's report now depends on:
    * the never-fired answer is a property of the policy and the program, so it is complete before
    * the pipeline starts and says the same thing whether or not the phase ran. */
  private def bindAndRun[P <: Phase & PolicyBound](ph: P): Program =
    val p = program()
    ph.bindPolicy(new PolicyBinder(p, p.members))
    ph.run(p)

  private def keys(r: PolicyReport)   = r.findings.map(_.key)
  private def issues(r: PolicyReport) = r.findings.map(_.issue)

  // -------------------------------------------------------------------------
  // Substitutions — a PURE policy value. Which of its keys FIRED is a question about a RUN, and
  // `PolicyBinder` answers it from the program plus the frontend's index (where a DROPPED member
  // still exists); see `PolicyBinderSpec`.
  test("dropsType and dropsMethod are PURE — asking twice is asking once, and asking changes nothing") {
    val subs = Substitutions(dropTypes = Set("demo.Real"), dropMethods = Set("demo.C#m", "demo.C#n(Int)"))
    assert(subs.dropsType("demo.Real"))
    assert(subs.dropsType("demo.Real")) // …and again: no accumulated state to differ on
    assert(!subs.dropsType("demo.Something"))
    assertEquals(subs, Substitutions(subs.dropTypes, subs.dropMethods, subs.inject))
  }

  test("a BARE key drops every overload; a PRECISE key drops exactly one") {
    val subs = Substitutions(dropMethods = Set("demo.C#write", "demo.C#<init>(Int)"))
    assert(subs.dropsMethod("demo.C", "write", List("String")))
    assert(subs.dropsMethod("demo.C", "write", List("Int", "Class"))) // bare: any parameter list
    assert(subs.dropsMethod("demo.C", "<init>", List("Int")))
    assert(!subs.dropsMethod("demo.C", "<init>", List("Long")))       // precise: that one only
    assert(!subs.dropsMethod("demo.C", "read", Nil))
  }

  test("`keys` is every declared key in the one grammar a report quotes them in") {
    assertEquals(Substitutions(dropTypes = Set("demo.A"), dropMethods = Set("demo.C#m")).keys,
      Set("demo.A", "demo.C#m"))
    assertEquals(Substitutions.none.keys, Set.empty[String])
  }

  // -------------------------------------------------------------------------
  // ClassTableTransform
  // -------------------------------------------------------------------------
  test("a class-table redirect that matched nothing is reported; one that fired is not") {
    val good = new ClassTableTransform(Map("com.x.Wrapper#forName" -> "com.x.Table#classFor"))
    bindAndRun(good)
    assert(clue(good.policyReport.render) == "  none")

    val typo = new ClassTableTransform(Map("com.x.Wrapper#fromName" -> "com.x.Table#classFor"))
    val out  = bindAndRun(typo)
    assertEquals(keys(typo.policyReport), List("com.x.Wrapper#fromName"))
    assertEquals(issues(typo.policyReport), List(PolicyIssue.NeverMatched))
    assertEquals(out.symbols.all.size, program().symbols.all.size) // and nothing was rewritten
  }

  test("a class-table destination that is not `owner#member` is reported, not thrown") {
    // it used to be `dest.substring(dest.lastIndexOf('#'))` on a -1 index.
    val bad = new ClassTableTransform(Map("com.x.Wrapper#forName" -> "com.x.Table"))
    bindAndRun(bad)
    assertEquals(issues(bad.policyReport), List(PolicyIssue.Malformed))
    assertEquals(keys(bad.policyReport), List("com.x.Wrapper#forName"))
  }

  test("an empty class-table policy is a silent no-op") {
    val off = new ClassTableTransform(Map.empty)
    bindAndRun(off)
    assert(off.policyReport.isEmpty)
  }

  // -------------------------------------------------------------------------
  // StaticForwarderTransform
  // -------------------------------------------------------------------------
  import StaticForwarderTransform.Forwarder

  test("a forwarder member that matched nothing is reported; one that fired is not") {
    val ph = new StaticForwarderTransform(List(
      Forwarder("com.x.Wrapper", "java.lang.Class", Set("forName", "getNaem"))))
    bindAndRun(ph)
    assertEquals(keys(ph.policyReport), List("com.x.Wrapper#getNaem"))
    assertEquals(issues(ph.policyReport), List(PolicyIssue.NeverMatched))
  }

  test("a forwarder WRAPPER that matched nothing is reported once, not once per member") {
    val ph = new StaticForwarderTransform(List(
      Forwarder("com.x.Wrpper", "java.lang.Class", Set("forName", "getSimpleName"))))
    bindAndRun(ph)
    assertEquals(keys(ph.policyReport), List("com.x.Wrpper"))
    assertEquals(ph.policyReport.findings.map(_.setting), List("Forwarder.wrapper"))
  }

  test("a member matched by NAME with overloads is diagnosed, and still rewritten") {
    // the latent edge: receiver-first is an assumption a name cannot carry. The engine says so
    // rather than guessing, because refusing correct rewrites would be the worse failure.
    val ph = new StaticForwarderTransform(List(
      Forwarder("com.x.Wrapper", "java.lang.Class", Set("getSimpleName"))))
    bindAndRun(ph)
    assertEquals(issues(ph.policyReport), List(PolicyIssue.Unverifiable))
    assert(clue(ph.policyReport.findings.head.detail).contains("2 overloads"))
    assert(ph.policyReport.findings.head.detail.contains("java.lang.Class"))
  }

  test("a member with a KNOWN no-argument signature is EXCLUDED, not merely diagnosed") {
    // proved, not suspected: with no first argument there is no receiver, so the rewrite is
    // impossible rather than doubtful. Nothing is minted for it.
    val ph  = new StaticForwarderTransform(List(
      Forwarder("com.x.Wrapper", "java.lang.Class", Set("now"))))
    val out = bindAndRun(ph)
    assertEquals(issues(ph.policyReport), List(PolicyIssue.Malformed))
    assertEquals(out.symbols.all.size, program().symbols.all.size)
  }

  test("an empty forwarder policy is a silent no-op") {
    val off = new StaticForwarderTransform(Nil)
    bindAndRun(off)
    assert(off.policyReport.isEmpty)
  }

  // -------------------------------------------------------------------------
  test("reports are COLLECTED from the seams an orchestrator already holds, and classify the fix") {
    val fwd  = new StaticForwarderTransform(List(Forwarder("com.x.Wrapper", "java.lang.Class", Set("getNaem"))))
    val tbl  = new ClassTableTransform(Map("com.x.Wrapper#fromName" -> "com.x.Table#classFor"))
    bindAndRun(fwd); bindAndRun(tbl)

    val all = PolicyReport.collect(fwd, tbl)
    assertEquals(all.keys, Set("com.x.Wrapper#getNaem", "com.x.Wrapper#fromName"))
    // every line names the key AND says which of CLAUDE.md §1's three kinds the fix is, so a
    // reader in another repository needs no investigation to act on it.
    all.findings.foreach { f =>
      assert(clue(f.render).contains(f.key))
      assert(f.render.contains("§1(b)"))
      assert(f.render.contains("manifest"))
    }
  }
