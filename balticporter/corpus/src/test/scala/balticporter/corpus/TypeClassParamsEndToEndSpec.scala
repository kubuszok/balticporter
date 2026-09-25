package balticporter.corpus

import balticporter.testkit.PortFixture
import balticporter.transform.TypeClassParamsTransform
import balticporter.transform.TypeClassParamsTransform.{ ClassValue, Rule, Spelling }

import java.nio.file.{ Files, Path }

/** `type-class-params` end to end: the emitted Scala is checked in beside this spec as `tcfixture/Emitted.scala`, so the build COMPILES it against the fixture's type class and this suite RUNS it. */
class TypeClassParamsEndToEndSpec extends munit.FunSuite:

  private val pkg = "balticporter.corpus.tcfixture"

  private val sources = List(
    "Widget.java" ->
      s"""package $pkg;
         |public class Widget { public Widget() {} }
         |""".stripMargin,
    "Pools.java" ->
      s"""package $pkg;
         |import java.util.HashMap;
         |public class Pools {
         |  private final HashMap<Class<?>, Object> cache = new HashMap<>();
         |  public <T> T create (Class<T> type) {
         |    try { return type.newInstance(); } catch (InstantiationException | IllegalAccessException e) { return null; }
         |  }
         |  public <T> T shared (Class<T> type) {
         |    Object found = cache.get(type);
         |    if (found == null) { found = create(type); cache.put(type, found); }
         |    return (T) found;
         |  }
         |  public <T> T fresh (Class<T> type) throws Exception { return type.getDeclaredConstructor().newInstance(); }
         |  public int size () { return cache.size(); }
         |}
         |""".stripMargin,
    "KeptPools.java" ->
      s"""package $pkg;
         |import java.util.HashMap;
         |public class KeptPools {
         |  private final HashMap<Class<?>, Object> cache = new HashMap<>();
         |  public <T> T create (Class<T> type) {
         |    try { return type.getDeclaredConstructor().newInstance(); } catch (ReflectiveOperationException e) { return null; }
         |  }
         |  public <T> T shared (Class<T> type) {
         |    Object found = cache.get(type);
         |    if (found == null) { found = create(type); cache.put(type, found); }
         |    return (T) found;
         |  }
         |}
         |""".stripMargin,
    "User.java" ->
      s"""package $pkg;
         |public class User {
         |  public Object[] run (Pools p, KeptPools k, Class<Widget> w) throws Exception {
         |    Widget a = p.shared(Widget.class);
         |    Widget b = p.shared(Widget.class);
         |    Widget c = p.create(Widget.class);
         |    Widget d = k.shared(Widget.class);
         |    Widget e = k.create(w);
         |    Widget f = p.fresh(w);
         |    return new Object[] { a, b, c, d, e, f };
         |  }
         |}
         |""".stripMargin
  )

  private val phase = new TypeClassParamsTransform(
    List(
      Rule(
        Set(s"$pkg.Pools#create", s"$pkg.Pools#shared", s"$pkg.Pools#fresh"),
        s"$pkg.Factory",
        classValue = ClassValue.Tag,
        handles = Set("java.lang.InstantiationException", "java.lang.IllegalAccessException")
      ),
      Rule(Set(s"$pkg.KeptPools#create", s"$pkg.KeptPools#shared"), s"$pkg.Factory", spelling = Spelling.KeepParameter, handles = Set("java.lang.ReflectiveOperationException"))
    )
  )

  private lazy val ported = PortFixture.portAll(sources, phase)

  /** every unit under ONE package clause — a file may not repeat it. */
  private lazy val emitted: String =
    val header = s"package $pkg\n"
    header + "\n" + ported.after.units.map(u => ported.emitter.emitUnit(u).stripPrefix(header).trim).mkString("\n\n") + "\n"

  private val golden = Path.of(sys.props("balticporter.root"), "balticporter/corpus/src/test/scala/balticporter/corpus/tcfixture/Emitted.scala")

  test("the emitted Scala is the checked-in file the build compiles") {
    val checkedIn = Files.readString(golden)
    assert(emitted == checkedIn, s"the emission moved; if the change is intended, replace ${golden.getFileName} with:\n$emitted")
  }

  test("the compiled emission constructs through the type class, with no reflection, and keeps the cache keyed by the class") {
    val p   = new tcfixture.Pools()
    val out = new tcfixture.User().run(p, new tcfixture.KeptPools(), classOf[tcfixture.Widget])
    assert(out.forall(_.isInstanceOf[tcfixture.Widget]), out.toList)
    assert(out(0) eq out(1), "the shared instance is cached under its class")
    assert(!(out(2) eq out(0)), "create builds a fresh instance")
    assertEquals(p.size(), 1)
    assert(p.shared[tcfixture.Widget] eq out(0))
  }

  test("a type the type class cannot be derived for is a compile error, where java failed at run time") {
    val errors = scala.compiletime.testing.typeCheckErrors("new balticporter.corpus.tcfixture.Pools().create[balticporter.corpus.tcfixture.NeedsArgument]")
    assert(errors.exists(_.message.contains("no accessible no-argument constructor")), errors.map(_.message).mkString("\n"))
    val abstractOne = scala.compiletime.testing.typeCheckErrors("new balticporter.corpus.tcfixture.Pools().create[java.lang.Number]")
    assert(abstractOne.exists(_.message.contains("abstract")), abstractOne.map(_.message).mkString("\n"))
  }

  test("a call passing a `Class` value is refused, counted, and left as java wrote it") {
    ported.out
    val found = phase.policyReport.findings
    assert(found.exists(f => f.key == s"$pkg.Pools#fresh" && f.detail.contains("passes a `Class` VALUE")), found.mkString("\n"))
    assertEquals(found.size, 1, found.mkString("\n"))
    assert(emitted.contains("def fresh[T <: java.lang.Object](`type`: java.lang.Class[T]): T"), emitted)
  }
