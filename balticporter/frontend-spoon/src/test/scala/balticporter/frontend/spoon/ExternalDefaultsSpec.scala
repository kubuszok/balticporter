package balticporter.frontend.spoon

/** The frontend records the JLS 9.4.3 `default` methods of an EXTERNAL interface parent off the
  * class file, so the emitter's diamond forwarder ASKS instead of guessing which external parent
  * is concrete (`ENGINE-LIMITS.md` K39). Arity-only, keyed by parent FQN. */
class ExternalDefaultsSpec extends munit.FunSuite:

  private val src =
    """package extdef;
      |public class Cursor implements java.util.Iterator<String> {
      |  public boolean hasNext () { return false; }
      |  public String next () { return null; }
      |}
      |""".stripMargin

  test("an external interface parent's DEFAULT methods are recorded") {
    val defaults = SpoonTir.fromSource(src).internedDefaults
    val iter = defaults.getOrElse("java.util.Iterator", Set.empty)
    assert(clue(iter).contains(("remove", List(0))), "java.util.Iterator#remove is a JLS 9.4.3 default")
    assert(iter.contains(("forEachRemaining", List(1))))
  }

  test("an ABSTRACT interface method is NOT recorded — the guard is the default, not the parent") {
    val iter = SpoonTir.fromSource(src).internedDefaults.getOrElse("java.util.Iterator", Set.empty)
    assert(!clue(iter).exists((n, _) => n == "hasNext" || n == "next"))
  }

  test("no external interface parent is the no-op") {
    val plain = """package extdef;
                  |public class Plain { public void tick () { } }
                  |""".stripMargin
    assertEquals(SpoonTir.fromSource(plain).internedDefaults, Map.empty[String, Set[(String, List[Int])]])
  }
