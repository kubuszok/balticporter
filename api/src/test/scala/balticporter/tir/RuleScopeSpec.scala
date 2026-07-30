package balticporter.tir

/** [[RuleScope]] — and above all its SEPARATOR CUT, which is CLAUDE.md §4.56's trap.
  *
  * The negative cases are the point. A membership test that only ever gets asked about names it
  * covers reports the same as a bare `startsWith`, and a bare `startsWith` is the defect §4.56
  * exists for: it rewrote `java.lang.String` into `j.lang.String` once and deleted three live casts
  * another time, both silently and both with a green compile. So every `covers` test here comes
  * with the name that must NOT match.
  */
class RuleScopeSpec extends munit.FunSuite:

  // -------------------------------------------------------------------------
  // the separator cut
  // -------------------------------------------------------------------------

  test("a PACKAGE entry covers its types and their members — and not a package that merely shares its prefix") {
    assert(RuleScope.covers("com.foo.Bar", "com.foo"))
    assert(RuleScope.covers("com.foo.Bar#baz", "com.foo"))
    assert(RuleScope.covers("com.foo.sub.Bar", "com.foo"))
    assert(RuleScope.covers("com.foo", "com.foo")) // the entry itself
    // THE TRAP: `com.foo` must not cover `com.foobar`.
    assert(!RuleScope.covers("com.foobar.Bar", "com.foo"))
    assert(!RuleScope.covers("com.foobar", "com.foo"))
  }

  test("a TYPE entry covers its members and its NESTED types — the `#` and `$` separators") {
    assert(RuleScope.covers("com.foo.Bar#baz", "com.foo.Bar"))
    assert(RuleScope.covers("com.foo.Bar$Inner", "com.foo.Bar"))
    assert(RuleScope.covers("com.foo.Bar$Inner#x", "com.foo.Bar"))
    // …and not a sibling type whose name begins with it.
    assert(!RuleScope.covers("com.foo.BarBaz", "com.foo.Bar"))
    assert(!RuleScope.covers("com.foo.Barn#x", "com.foo.Bar"))
  }

  test("a MEMBER entry covers that member and its parameters, and not a longer member name") {
    assert(RuleScope.covers("com.foo.Bar#baz", "com.foo.Bar#baz"))
    assert(RuleScope.covers("com.foo.Bar#baz#p", "com.foo.Bar#baz")) // a parameter, as SpoonTir names it
    assert(!RuleScope.covers("com.foo.Bar#bazinga", "com.foo.Bar#baz"))
  }

  test("an EMPTY entry names NOTHING — one stray comma must not swallow the port") {
    assert(!RuleScope.covers("com.foo.Bar", ""))
    assertEquals(RuleScope.Only(Set("")).includes("com.foo.Bar"), false)
    assertEquals(RuleScope.Everywhere(Set("")).includes("com.foo.Bar"), true)
  }

  test("longestPrefix picks the MOST SPECIFIC entry — the string an agent would edit") {
    val es = Set("com.foo", "com.foo.Bar", "com.foo.Bar#baz")
    assertEquals(RuleScope.longestPrefix("com.foo.Bar#baz", es), Some("com.foo.Bar#baz"))
    assertEquals(RuleScope.longestPrefix("com.foo.Bar#qux", es), Some("com.foo.Bar"))
    assertEquals(RuleScope.longestPrefix("com.foo.Other", es), Some("com.foo"))
    assertEquals(RuleScope.longestPrefix("com.other.X", es), scala.None)
  }

  // -------------------------------------------------------------------------
  // the two directions
  // -------------------------------------------------------------------------

  test("the DEFAULT scope is unrestricted and includes everything — §1(b)'s no-op parameter") {
    val s = RuleScope.Everywhere()
    assert(s.isUnrestricted)
    assert(s.includes("anything.at.all"))
    assertEquals(s.entries, Set.empty[String])
    assertEquals(s.fingerprint, "")
  }

  test("Everywhere(except) excludes exactly what it names, and nothing that merely resembles it") {
    val s = RuleScope.Everywhere(Set("com.foo.Legacy"))
    assert(!s.includes("com.foo.Legacy"))
    assert(!s.includes("com.foo.Legacy#xs"))
    assert(s.includes("com.foo.LegacyBridge")) // the separator cut, in the exclusion direction
    assert(s.includes("com.foo.Other"))
    assert(!s.isUnrestricted)
  }

  test("Only(include) admits exactly what it names") {
    val s = RuleScope.Only(Set("com.foo.Model"))
    assert(s.includes("com.foo.Model#xs"))
    assert(!s.includes("com.foo.ModelBuilder"))
    assert(!s.includes("com.bar.Model"))
  }

  test("Only(Set.empty) is a no-op in the honest direction — 'only these' of nothing is nothing") {
    val s = RuleScope.Only(Set.empty)
    assert(!s.includes("com.foo.Bar"))
    assert(!s.isUnrestricted)
  }

  test("entryFor names the ENTRY that decided, in both directions — the key a Reason.Configured quotes") {
    assertEquals(RuleScope.Everywhere(Set("com.foo")).entryFor("com.foo.Bar#x"), Some("com.foo"))
    assertEquals(RuleScope.Only(Set("com.foo.Bar")).entryFor("com.foo.Bar#x"), Some("com.foo.Bar"))
    assertEquals(RuleScope.Only(Set("com.foo.Bar")).entryFor("com.other.X"), scala.None)
  }

  // -------------------------------------------------------------------------
  // symbols — the owner climb
  // -------------------------------------------------------------------------

  /** a four-level table, with the two names the frontend actually produces for the kinds a name-only
    * test cannot place (both established by running `FlowPropagationSpec` against `SpoonTir`, not by
    * reading it): a PARAMETER is `?#p`, and a method-LOCAL is its bare simple name. */
  private def program: Program =
    val cls   = Symbol(SymId(1), "Bar", "com.foo.Bar", Flags(), SymId.None, TypeRepr.NoType)
    val meth  = Symbol(SymId(2), "m", "com.foo.Bar#m", Flags(), cls.id, TypeRepr.MethodType(Nil, TypeRepr.NoType))
    val param = Symbol(SymId(3), "p", "?#p", Flags(isParam = true), meth.id, TypeRepr.NoType)
    val local = Symbol(SymId(4), "i", "i", Flags(), meth.id, TypeRepr.NoType)
    val other = Symbol(SymId(5), "Baz", "com.other.Baz", Flags(), SymId.None, TypeRepr.NoType)
    new Program(Nil, SymbolTable(List(cls, meth, param, local, other)), Xref.build(Nil))

  private def sym(p: Program, fqn: String): Symbol = p.symbols.all.find(_.fullName == fqn).get

  test("a method-LOCAL is placed through its OWNERS — its own fullName is a bare simple name") {
    val p = program
    val local = sym(p, "i")
    assertEquals(local.fullName, "i") // the fact that makes a name-only test insufficient
    assert(RuleScope.Only(Set("com.foo.Bar")).includes(p, local))
    assert(!RuleScope.Everywhere(Set("com.foo.Bar")).includes(p, local))
    // …and a scope naming an unrelated type does not reach it by accident.
    assert(!RuleScope.Only(Set("com.other")).includes(p, local))
  }

  test("a PARAMETER is in scope with its member — its own fullName is `?#p` and names nothing") {
    val p     = program
    val param = sym(p, "?#p")
    assert(!RuleScope.Only(Set("com.foo.Bar")).includes(param.fullName)) // the name alone: no
    assert(RuleScope.Only(Set("com.foo.Bar#m")).includes(p, param))      // through the owner: yes
    assert(RuleScope.Only(Set("com.foo.Bar")).includes(p, param))
    assert(!RuleScope.Everywhere(Set("com.foo.Bar#m")).includes(p, param))
  }

  test("a member is in scope with its type, and a type is NOT in scope with one of its members") {
    val p = program
    assert(RuleScope.Only(Set("com.foo.Bar")).includes(p, sym(p, "com.foo.Bar#m")))
    assert(!RuleScope.Only(Set("com.foo.Bar#m")).includes(p, sym(p, "com.foo.Bar")))
  }

  test("a local's BARE NAME places nothing — an entry that is a simple name must match no local anywhere") {
    // The trap this closes: a method-local's `fullName` IS its simple name, so `Only(Set("i"))` —
    // a policy line copied without its type, or a typo for `com.foo.Bar#i` — matched EVERY local
    // called `i`, in every class, and reported itself as having fired.
    val p = program
    assertEquals(sym(p, "i").fullName, "i")
    assert(!RuleScope.Only(Set("i")).includes(p, sym(p, "i")))
    assert(RuleScope.Everywhere(Set("i")).includes(p, sym(p, "i")))
    assertEquals(RuleScope.Only(Set("i")).entryFor(p, sym(p, "i")), scala.None)
    // …and the same for a parameter's `?#p`, which a bare `?` covers at a separator.
    assertEquals(RuleScope.Only(Set("?")).entryFor(p, sym(p, "?#p")), scala.None)
    // The name test is not consulted for those two ONLY. A top-level type in the DEFAULT package
    // has no separator in its name either, and it is still placed by it — the test is structural.
    val dflt = Symbol(SymId(6), "Loose", "Loose", Flags(), SymId.None, TypeRepr.NoType)
    val q    = new Program(Nil, SymbolTable(p.symbols.all.toList :+ dflt), Xref.build(Nil))
    assertEquals(RuleScope.Only(Set("Loose")).entryFor(q, dflt), Some("Loose"))
  }

  test("an empty scope short-circuits the owner climb and includes/excludes by direction") {
    val p = program
    assert(RuleScope.Everywhere().includes(p, sym(p, "i")))
    assert(!RuleScope.Only(Set.empty).includes(p, sym(p, "i")))
  }

  // -------------------------------------------------------------------------
  // reporting a policy that never fired
  // -------------------------------------------------------------------------

  test("neverFired is the complement of what the phase observed — the §1(b) silent-no-op report") {
    val s = RuleScope.Only(Set("com.foo.Bar", "com.foo.Typo"))
    assertEquals(s.neverFired(Set("com.foo.Bar")), Set("com.foo.Typo"))
    assertEquals(s.neverFired(Set("com.foo.Bar", "com.foo.Typo")), Set.empty[String])
    // an unrestricted scope has nothing to report, whatever fired.
    assertEquals(RuleScope.Everywhere().neverFired(Set.empty), Set.empty[String])
  }

  test("the fingerprint is stable under set order and separates the two directions") {
    assertEquals(RuleScope.Everywhere(Set("b", "a")).fingerprint, RuleScope.Everywhere(Set("a", "b")).fingerprint)
    assertNotEquals(RuleScope.Everywhere(Set("a")).fingerprint, RuleScope.Only(Set("a")).fingerprint)
    assertNotEquals(RuleScope.Everywhere(Set("a")).fingerprint, RuleScope.Everywhere(Set("a", "b")).fingerprint)
  }
