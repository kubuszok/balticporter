package balticporter.tir

/** [[MemberKey]] and [[Descriptor]] — the ONE grammar for "which member". */
class MemberKeySpec extends munit.FunSuite:

  // -------------------------------------------------------------------------
  // the grammar
  // -------------------------------------------------------------------------

  test("a BARE key parses to owner + name with NO descriptor — a field's total identity, and an overload SET") {
    val k = MemberKey.of("com.foo.Bar#baz")
    assertEquals(k.owner, "com.foo.Bar")
    assertEquals(k.name, "baz")
    assertEquals(k.descriptor, scala.None)
    assert(k.isBare)
    assertEquals(k.render, "com.foo.Bar#baz")
  }

  test("a PRECISE key carries its parameter spelling and round-trips") {
    val k = MemberKey.of("com.foo.Bar#baz(int,String,Class)")
    assertEquals(k.descriptor.map(_.params),
      Some(List(Param.Prim("int"), Param.Named("String"), Param.Named("Class"))))
    assertEquals(k.render, "com.foo.Bar#baz(int,String,Class)")
  }

  test("the NO-ARGUMENT overload is `name()` and is NOT the same key as the bare form") {
    val nullary = MemberKey.of("com.foo.Bar#baz()")
    assertEquals(nullary.descriptor, Some(Descriptor(Nil)))
    assertNotEquals(nullary, MemberKey.of("com.foo.Bar#baz"))
    assertEquals(nullary.bare, MemberKey.of("com.foo.Bar#baz"))
    assertEquals(nullary.render, "com.foo.Bar#baz()")
  }

  test("a NESTED owner and a CONSTRUCTOR are ordinary keys — `$` and `<init>` need no special case") {
    val k = MemberKey.of("com.foo.Outer$Inner#<init>(int,Class)")
    assertEquals(k.owner, "com.foo.Outer$Inner")
    assertEquals(k.name, "<init>")
    assertEquals(k.descriptor.map(_.arity), Some(2))
    assertEquals(k.render, "com.foo.Outer$Inner#<init>(int,Class)")
    // …and the bare constructor key, which is how a whole family of constructors is named.
    assertEquals(MemberKey.of("com.foo.Bar#<init>").name, "<init>")
  }

  test("`<init>` is a NAME with angle brackets — the type-argument refusal is per REGION, not per key") {
    // A whole-string test for `<` refused every constructor key in every manifest, which is exactly
    // the class of defect a refusal is supposed to prevent. The regions are checked separately, so a
    // constructor parses and a generic parameter still does not.
    assert(MemberKey.parse("com.foo.Bar#<init>(int)").isRight)
    assert(MemberKey.parse("com.foo.Bar#<clinit>").isRight)
    assert(MemberKey.parse("com.foo.Bar#<init>(Class<T>)").isLeft)
    assert(clue(MemberKey.parse("com.foo.Bar#<init>(Class<T>)").left.toOption.get.what).contains("parameter list"))
  }

  // -------------------------------------------------------------------------
  // D-a: an ARRAY parameter — the divergence, pinned from BOTH sides
  // -------------------------------------------------------------------------

  test("an ARRAY parameter is spelled `int[]` — Java's own spelling, and `Array` is a DIFFERENT key") {
    val k = MemberKey.of("com.foo.Owner#copy(int[])")
    assertEquals(k.descriptor.map(_.params), Some(List(Param.Arr(Param.Prim("int")))))
    assertEquals(k.render, "com.foo.Owner#copy(int[])")
    // THE TRAP, from the other side: the engine's own key grammar used to take the TYCON's name for
    // `AppliedType(scala.Array, [Int])` and spell this member `copy(Array)`. That is a different
    // member key and must stay one, or the two spellings quietly become interchangeable again.
    assertNotEquals(MemberKey.of("com.foo.Owner#copy(Array)"), k)
    assertEquals(MemberKey.of("com.foo.Owner#copy(Array)").descriptor.map(_.params),
      Some(List(Param.Named("Array"))))
  }

  test("a nested array nests, and an array of a type variable is ordinary") {
    assertEquals(MemberKey.of("X#m(int[][])").descriptor.map(_.render), Some("int[][]"))
    assertEquals(MemberKey.of("X#m(T[])").descriptor.map(_.params), Some(List(Param.Arr(Param.Named("T")))))
  }

  test("a VARARG is an ARRAY — `of(String...)` is the key `of(String[])`, and NOT `of(String)`") {
    // Java's own rule: `T…` is a `T[]` for every purpose except the call syntax, so a vararg member
    // and a declared-array member of the same name are the SAME member and spell the same. What
    // must not happen is the vararg key also naming the single-argument overload beside it.
    val vararg = MemberKey.of("com.foo.Owner#of(String[])")
    assertEquals(vararg.descriptor.map(_.params), Some(List(Param.Arr(Param.Named("String")))))
    assertNotEquals(vararg, MemberKey.of("com.foo.Owner#of(String)"))
    assertEquals(MemberKey.of("com.foo.Owner#of(String)").descriptor.map(_.arity), Some(1))
  }

  // -------------------------------------------------------------------------
  // malformed — each REFUSED, and refused with the reason, never read as a typo
  // -------------------------------------------------------------------------

  test("a TYPE ARGUMENT is MALFORMED and named AT the `<` — never silently NeverMatched") {
    val e = MemberKey.parse("com.foo.X#m(Class<T>)")
    assert(e.isLeft, clue(e))
    val what = e.left.toOption.get.what
    // the two readings contradict each other: "your key is a typo" sends its author looking for a
    // member that is right there. So the message has to say WHERE and WHAT to write instead.
    assertEquals("com.foo.X#m(Class<T>)".indexOf('<'), 17)
    assert(clue(what).contains("index 17"), clue(what))
    assert(clue(what).contains("`<T>`"), clue(what))
    assert(clue(what).contains("`Class`"), clue(what))
  }

  test("the other shapes that could never match are refused, each with its own reason") {
    def why(k: String): String = MemberKey.parse(k).left.toOption.map(_.what).getOrElse("PARSED")
    assert(clue(why("com.foo.Bar")).contains("no `#`"))
    assert(clue(why("#baz")).contains("owner is empty"))
    assert(clue(why("com.foo.Bar#")).contains("names a member"))
    assert(clue(why("com.foo.Bar#baz(int")).contains("unclosed"))
    assert(clue(why("com.foo.Bar#baz(int,)")).contains("empty parameter"))
    assert(clue(why("com.foo.Bar#baz(int,,String)")).contains("empty parameter"))
    // A key with two `#`s now splits at the LAST one — `com.foo.Bar#b#z` is owner `com.foo.Bar#b`,
    // member `z`. This is how an enum constant's body member is named (T23).
    assertEquals(clue(MemberKey.parse("com.foo.Bar#b#z")),
      Right(MemberKey("com.foo.Bar#b", "z", scala.None)))
    assert(clue(why("com.foo.Bar#baz(?)")).contains("unreadable"))
  }

  test("`of` throws on a malformed key — it is for literals in engine code, never for policy") {
    intercept[IllegalArgumentException](MemberKey.of("com.foo.X#m(Class<T>)"))
  }

  // -------------------------------------------------------------------------
  // ALL of them or none
  // -------------------------------------------------------------------------

  test("one Unresolved parameter poisons the WHOLE descriptor — never a half-guessed key") {
    assertEquals(Descriptor.total(List(Param.Prim("int"), Param.Unresolved)), scala.None)
    assertEquals(Descriptor.total(List(Param.Arr(Param.Unresolved))), scala.None)
    assertEquals(Descriptor.total(List(Param.Prim("int"))), Some(Descriptor(List(Param.Prim("int")))))
    assertEquals(Descriptor.total(Nil), Some(Descriptor.empty))
  }

  test("Java's primitives keep JAVA's spelling — `int`, never `Int`") {
    assertEquals(Descriptor.paramOf("int"), Param.Prim("int"))
    assertEquals(Descriptor.paramOf("boolean"), Param.Prim("boolean"))
    assertEquals(Descriptor.paramOf("Int"), Param.Named("Int")) // scala's name is an ordinary type name
  }

  // -------------------------------------------------------------------------
  // the QUALIFIED spelling a report shows, matched against the SIMPLE one this grammar is
  // -------------------------------------------------------------------------

  test("a QUALIFIED parameter matches the simple one — the trap two ports documented and neither could fix") {
    // Every report a policy author copies a key out of shows the QUALIFIED name: an external
    // member's `Symbol.fullName` is its interning key, so a boundary row prints
    // `…#identityHashCode(java.lang.Object)`. Compared by equality that key named nothing, and the
    // only advice was "write it bare" — exact for a one-overload member and wrong the day there are
    // two.
    val written = MemberKey.of("java.lang.System#identityHashCode(java.lang.Object)").descriptor.get
    val known   = MemberKey.of("java.lang.System#identityHashCode(Object)").descriptor.get
    assert(written.matches(known))
    assert(known.matches(written))
    assertNotEquals(written, known)
  }

  test("…cut at a SEPARATOR and only the LAST one, so a NESTED type spells as the parser does") {
    assertEquals(Param.Named("java.util.Map$Entry").simple, Param.Named("Entry"))
    assertEquals(Param.Named("Entry").simple, Param.Named("Entry"))
    assertEquals(Param.Arr(Param.Named("java.lang.String")).simple, Param.Arr(Param.Named("String")))
    assertEquals(Param.Prim("int").simple, Param.Prim("int"))
    // a prefix is not a separator: `com.foobar.X` cuts at its own last one (§4.56)
    assertEquals(Param.Named("com.foobar.X").simple, Param.Named("X"))
  }

  test("…and it is the IDENTITY on every key a manifest holds today — arity first, never a prefix") {
    val k = MemberKey.of("com.foo.Bar#baz(int,String,Class)").descriptor.get
    assert(k.matches(k))
    assert(!k.matches(MemberKey.of("com.foo.Bar#baz(int,String)").descriptor.get))
    assert(!k.matches(MemberKey.of("com.foo.Bar#baz(int,String,Object)").descriptor.get))
    // two DISTINCT simple names stay distinct — the leniency is about the PACKAGE and nothing else
    assert(!MemberKey.of("X#m(java.util.List)").descriptor.get
             .matches(MemberKey.of("X#m(Map)").descriptor.get))
  }
