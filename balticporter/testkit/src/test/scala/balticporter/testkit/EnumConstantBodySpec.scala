package balticporter.testkit

/** A Java enum CONSTANT BODY, through the real pipeline.
  *
  * Java's enum constant may carry an anonymous class body, and that body is an ordinary class body:
  * it may override methods AND declare fields. JLS 8.1.3 allows `static final` ones there because
  * they are constant variables, which is exactly the form libraries use to keep a magic number
  * beside the constant that needs it.
  *
  * The frontend harvested only `CtMethod` from that body and dropped everything else in silence. A
  * field so lost is not visible to any count: the emitted `case object` is structurally correct, the
  * omissions check counts what the TIR CARRIES and this never reached the TIR, and the only symptom
  * is a `Not found` at the line that reads it — which is a compile error and therefore invisible
  * behind any earlier one (CLAUDE.md §3). Measured on noise4j's `RoomType.DefaultRoomType`: 4 of the
  * port's 6 errors, from two constants.
  *
  * A `case object`'s body IS the constant's scope in Scala, so the field needs no home of its own —
  * which is why this is a frontend harvest and not an emitter change.
  */
class EnumConstantBodySpec extends PortSuite:

  private val castle = """
    package demo;
    public enum Shape {
      SQUARE {
        @Override public int area(int side) { return side * side; }
      },
      CASTLE {
        public static final int MIN_SIZE = 7, MIN_TOWER = 3;
        @Override public int area(int side) { return side < MIN_SIZE ? 0 : side * side - MIN_TOWER; }
        @Override public boolean valid(int side) { return side >= MIN_SIZE; }
      };
      public abstract int area(int side);
      public boolean valid(int side) { return true; }
    }"""

  test("a `static final` field declared in an enum constant's body becomes a member of its case object") {
    val p = port(castle)
    assertEmits(p, "case object CASTLE extends Shape {")
    // both declarators of the one Java field declaration — a comma-separated pair is two fields, and
    // harvesting the first only would be the same defect one step further in.
    assertEmits(p, "inline val MIN_SIZE = 7")
    assertEmits(p, "inline val MIN_TOWER = 3")
  }

  test("the constant's own methods still read those fields UNQUALIFIED, as Java did") {
    val p = port(castle)
    // the point of putting them in the case object body: the reference needs no prefix and no
    // companion, so nothing downstream has to know where the field went.
    assertEmits(p, "MIN_SIZE")
    assertEmitsMatch(p, """def valid\(side: scala\.Int\): scala\.Boolean = \{\s*return side >= MIN_SIZE""")
  }

  test("NEGATIVE: a constant with no body of its own gains no members") {
    // The check that this harvest is not inventing anything. SQUARE declares one method and no
    // field; a fixture where every constant carried a field would pass with a harvest that put the
    // fields on the enum rather than on the constant.
    val p = port(castle)
    assertNotEmits(p, "case object SQUARE extends Shape {\n      inline val")
    val squareBody = p.out.split("case object SQUARE extends Shape \\{")(1).split("case object CASTLE")(0)
    assert(!squareBody.contains("MIN_SIZE"), clue(squareBody))
    assert(!squareBody.contains("val "), clue(squareBody))
  }

  test("NEGATIVE: an enum constant with no body at all contributes nothing of its OWN") {
    // The braces are not the test either. An enum with NO constant body is expressible as a scala 3
    // `enum` (`ENGINE-LIMITS.md` T21), so its constants are cases with no template at all — and
    // `ordinal()` comes from `java.lang.Enum` rather than from an override the lowering writes. What
    // this asserts is that the harvest adds nothing: no method of the enum's, no field, and nothing
    // from its sibling.
    val p = port("""
      package demo;
      public enum Bare {
        A, B;
        public int n() { return 1; }
      }""")
    assertEmits(p, "case A extends Bare\n")
    assertEmits(p, "case B extends Bare\n")
    assertNotEmits(p, "case A extends Bare {")
    assertNotEmits(p, "override def ordinal()")
  }
