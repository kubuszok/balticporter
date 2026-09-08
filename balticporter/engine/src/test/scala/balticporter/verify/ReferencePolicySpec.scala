package balticporter.verify

import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*
import munit.FunSuite

/** `ReferencePolicy.derive` reads SPELLING off a reference tree: an opaque slot, a nullable
  * member, a parenless accessor; overloads that disagree are counted, never guessed
  * (`PROGRESS.md` §13.31 step 1). */
class ReferencePolicySpec extends FunSuite:

  private val javaSrc =
    """package com.example.gfx;
      |public class Clock {
      |  public float delta;
      |  public float getDelta() { return delta; }
      |  public void update(float dt, int width) {}
      |  public String label() { return null; }
      |  public int size() { return 0; }
      |  public static int count() { return 0; }
      |  public void set(float a) {}
      |  public void set(int a) {}
      |}
      |""".stripMargin

  private val reference =
    """package sge.gfx
      |import sge.utils.Seconds
      |class Clock {
      |  var delta: Seconds = Seconds.zero
      |  def getDelta: Seconds = delta
      |  def update(dt: Seconds, width: Pixels)(using ctx: Sge): Unit = ()
      |  def label(): Nullable[String] = Nullable.empty
      |  def size: Int = 0
      |  def set(a: Seconds): Unit = ()
      |  def set(a: Int): Unit = ()
      |}
      |object Clock { def count: Int = 0 }
      |""".stripMargin

  private def refDecls(src: String): List[ApiParityCheck.SurfaceDecl] =
    val dir = java.nio.file.Files.createTempDirectory("refpolicy")
    java.nio.file.Files.writeString(dir.resolve("Clock.scala"), src)
    ApiParityCheck.parseSurface(List(dir)).toOption.get

  private def derive(src: String = reference, targets: Set[String] = Set("sge.utils.Seconds", "sge.Pixels")) =
    val p = SpoonTir.fromSource(javaSrc)
    ReferencePolicy.derive(p, refDecls(src), p.units.map(_.symbol).toSet, Map.empty, Set.empty, targets)

  test("a primitive slot the reference spells at an opaque target becomes a seed — parameter, result and field") {
    val r = derive()
    assertEquals(r.policy.opaqueSeeds("sge.utils.Seconds"),
      Set("com.example.gfx.Clock#delta", "com.example.gfx.Clock#getDelta", "com.example.gfx.Clock#update#dt"))
    assertEquals(r.policy.opaqueSeeds("sge.Pixels"), Set("com.example.gfx.Clock#update#width"))
  }

  test("a using clause is not part of the arity a java member is matched against") {
    // `update(dt, width)(using ctx)` still matches java's `update(float, int)`
    assert(derive().policy.opaqueSeeds("sge.Pixels").nonEmpty)
  }

  test("a member the reference returns wrapped is a nullable member; a nullary def written without () is parenless") {
    val r = derive()
    assertEquals(r.policy.nullableMembers, Set("com.example.gfx.Clock#label"))
    assertEquals(r.policy.parenless, Set("com.example.gfx.Clock#getDelta", "com.example.gfx.Clock#size"))
  }

  test("a static is never parenless (its arity lives on the companion, K51) and `label()` with parens is not") {
    val r = derive()
    assert(!r.policy.parenless.contains("com.example.gfx.Clock#count"))
    assert(!r.policy.parenless.contains("com.example.gfx.Clock#label"))
  }

  test("overloads at one key that disagree derive only the agreed rows and are counted") {
    val r = derive()
    // set(Seconds) vs set(Int): java's two `set(float)`/`set(int)` both see both candidates
    assert(!r.policy.opaqueSeeds("sge.utils.Seconds").exists(_.startsWith("com.example.gfx.Clock#set")))
    assertEquals(r.findings.count(_.check == ReferencePolicy.LaneAmbiguous), 2)
  }

  test("an OVERLOADED java name's rows are keyed by descriptor, so one overload's spelling never reaches another") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Attrs {
        |  public String get(long type) { return null; }
        |  public String get(String out, long type) { return out; }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |class Attrs {
        |  def get(tpe: Long): Nullable[String] = Nullable.empty
        |  def get(out: String, tpe: Long): String = out
        |}
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    assertEquals(r.policy.nullableMembers, Set("com.example.gfx.Attrs#get(long)"))
    val one = p.symbols.all.find(s => s.name == "get" && s.descriptor.exists(_.render == "long")).get
    val two = p.symbols.all.find(s => s.name == "get" && s.descriptor.exists(_.render != "long")).get
    assert(DerivedPolicy.keysOf(p, one).contains("com.example.gfx.Attrs#get(long)"))
    assert(!DerivedPolicy.keysOf(p, two).contains("com.example.gfx.Attrs#get(long)"))
  }

  test("a java type with no reference twin is counted, and two targets sharing a simple name derive nothing") {
    val r = derive(src = reference.replace("class Clock", "class Timer").replace("object Clock", "object Timer"))
    assertEquals(r.policy.rows, Nil)
    assertEquals(r.findings.count(_.check == ReferencePolicy.LaneUnmatched), 1)
    val same = derive(targets = Set("sge.utils.Seconds", "other.Seconds"))
    assert(same.policy.opaqueSeeds("sge.utils.Seconds").isEmpty)
  }

  test("a java static is read under the companion, a static nested type under either the class or the companion") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Timer {
        |  public static float now() { return 0f; }
        |  public static class Tick { public float at; }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |class Timer
        |object Timer {
        |  def now: Seconds = Seconds.zero
        |  class Tick { var at: Seconds = Seconds.zero }
        |}
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set("sge.utils.Seconds"))
    assertEquals(r.policy.opaqueSeeds("sge.utils.Seconds"), Set("com.example.gfx.Timer#now", "com.example.gfx.Timer$Tick#at"))
    assertEquals(r.findings.count(_.check == ReferencePolicy.LaneUnmatched), 0)
  }

  test("two reference types sharing a simple name are told apart by the RENAMED java package") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Effect { public void run(float dt) {} }
        |""".stripMargin
    val ref =
      """package sge.gfx
        |class Effect { def run(dt: Seconds): Unit = () }
        |""".stripMargin
    val other =
      """package sge.audio
        |class Effect { def run(dt: Float): Unit = () }
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val decls = refDecls(ref) ++ refDecls(other)
    val r = ReferencePolicy.derive(p, decls, p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set("sge.utils.Seconds"),
      packageRenames = Map("com.example" -> "sge"))
    assertEquals(r.policy.opaqueSeeds("sge.utils.Seconds"), Set("com.example.gfx.Effect#run#dt"))
    assertEquals(r.findings.count(_.check == ReferencePolicy.LaneAmbiguous), 0)
  }

  test("a java constructor's slots derive from the reference's constructors or its companion apply") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Delay {
        |  public Delay(float duration) {}
        |  public Delay(float duration, int reps) {}
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |class Delay(duration: Seconds) {
        |  def this(duration: Seconds, reps: Pixels) = this(duration)
        |}
        |object Delay { def apply(duration: Seconds): Delay = new Delay(duration) }
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set("sge.utils.Seconds", "sge.Pixels"))
    // two constructors are an overload set: their rows are keyed by descriptor
    assertEquals(r.policy.opaqueSeeds("sge.utils.Seconds"),
      Set("com.example.gfx.Delay#<init>(float)#duration", "com.example.gfx.Delay#<init>(float,int)#duration"))
    assertEquals(r.policy.opaqueSeeds("sge.Pixels"), Set("com.example.gfx.Delay#<init>(float,int)#reps"))
  }

  test("a java accessor pair is read at the hand port's property: getter at the def or var, setter at the var or x_=") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Screen {
        |  public int getWidth() { return 0; }
        |  public void setWidth(int w) {}
        |  public boolean isVisible() { return true; }
        |  public float getScale() { return 1f; }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |class Screen {
        |  var width: Pixels = Pixels.zero
        |  def visible: Boolean = true
        |  def scale: Seconds = Seconds.zero
        |}
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set("sge.utils.Seconds", "sge.Pixels"))
    assertEquals(r.policy.opaqueSeeds("sge.Pixels"), Set("com.example.gfx.Screen#getWidth", "com.example.gfx.Screen#setWidth#w"))
    assertEquals(r.policy.opaqueSeeds("sge.utils.Seconds"), Set("com.example.gfx.Screen#getScale"))
    // a property match says nothing about parens (the bean step decides the shape); a def match does
    assertEquals(r.policy.parenless, Set("com.example.gfx.Screen#isVisible", "com.example.gfx.Screen#getScale"))
  }

  test("an indexed getter is read at the property name with its index — `getX(pointer)` at `x(pointer)`") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class In {
        |  public int getX() { return 0; }
        |  public int getX(int pointer) { return 0; }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |class In {
        |  def x: Pixels = Pixels.zero
        |  def x(pointer: Int): Pixels = Pixels.zero
        |}
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set("sge.Pixels"))
    assertEquals(r.policy.opaqueSeeds("sge.Pixels"), Set("com.example.gfx.In#getX()", "com.example.gfx.In#getX(int)"))
  }

  test("a java accessor pair the reference spells as a `var` derives a property pair, fluent setter included") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Conf {
        |  private int maxLength;
        |  public int getMaxLength() { return maxLength; }
        |  public Conf setMaxLength(int m) { maxLength = m; return this; }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |class Conf { var maxLength: Int = 0 }
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    assertEquals(r.policy.propertyPairs, List(("com.example.gfx.Conf", "maxLength", "getMaxLength", Some("setMaxLength"))))
  }

  test("a nullary method the reference keeps WITH its parens derives a KeepParens row, a parenless one a Parenless row") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Dist {
        |  public int size() { return 1; }
        |  public float total() { return 1f; }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |class Dist { def size(): Int = 1; def total: Float = 1f }
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    val fams = r.policy.rows.map(row => row.upstream -> row.family).toMap
    assertEquals(fams.get("com.example.gfx.Dist#size"), Some(DerivedPolicy.Family.KeepParens))
    assertEquals(fams.get("com.example.gfx.Dist#total"), Some(DerivedPolicy.Family.Parenless))
  }

  test("a `Class<T>` parameter the reference turns into a `[T: ClassTag]` bound derives a ClassTagParam row") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class PM {
        |  public <T> T get(Class<T> c) { return null; }
        |  public <T> void add(Class<T> c, T v) { }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |import scala.reflect.ClassTag
        |class PM { def get[T: ClassTag]: T = ???; def add[T: ClassTag](v: T): Unit = () }
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    val fams = r.policy.rows.filter(_.family == DerivedPolicy.Family.ClassTagParam).map(_.upstream).sorted
    assertEquals(fams, List("com.example.gfx.PM#add", "com.example.gfx.PM#get"))
  }

  test("an acronym-cased accessor is matched at the reference's lowered spelling and folds to it") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Gfx {
        |  public boolean isGL30Available() { return false; }
        |  public String getGL30() { return null; }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |import lowlevel.Nullable
        |class Gfx { def gl30Available: Boolean = false; def gl30: Nullable[String] = Nullable.empty }
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    val props = r.policy.rows.filter(_.family == DerivedPolicy.Family.Property).map(row => row.upstream -> row.target).toMap
    assertEquals(props.get("com.example.gfx.Gfx#isGL30Available"), Some("gl30Available"))
    assertEquals(props.get("com.example.gfx.Gfx#getGL30"), Some("gl30"))
    assert(r.policy.rows.exists(row => row.family == DerivedPolicy.Family.NullableMember && row.upstream == "com.example.gfx.Gfx#getGL30"))
  }

  test("several reference constructors at one arity: the one whose parameter types match java's decides") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Font {
        |  public Font(String data, int[] regions, boolean integer) { }
        |  public Font(String data, String region, boolean integer) { }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |import lowlevel.Nullable
        |class Font(val data: String, regions: Nullable[Array[Int]], val integer: Boolean) {
        |  def this(data: String, region: String, integer: Boolean) = this(data, Nullable.empty, integer)
        |}
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    val nullables = r.policy.rows.filter(_.family == DerivedPolicy.Family.NullableMember).map(_.upstream)
    assert(clue(nullables).exists(_.contains("regions")), r.policy.rows.mkString("\n"))
  }

  test("a reference constructor with a trailing default answers the shorter java arity too") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Font {
        |  protected Font(String data) { }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |import lowlevel.Nullable
        |class Font(val data: String, regions: Nullable[String] = Nullable.empty)
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    assert(clue(r.policy.rows).exists(row => row.family == DerivedPolicy.Family.Public && row.upstream == "com.example.gfx.Font#<init>"))
  }

  test("a same-named field of ANOTHER type is no twin: the getter keeps java's name") {
    val javaSrc2 =
      """package com.example.gfx;
        |public class Cell {
        |  Object minWidth;
        |  public float getMinWidth() { return 0f; }
        |}
        |""".stripMargin
    val ref =
      """package sge.gfx
        |import lowlevel.Nullable
        |class Cell { var minWidth: Nullable[String] = Nullable.empty; def getMinWidth: Float = 0f }
        |""".stripMargin
    val p = SpoonTir.fromSource(javaSrc2)
    val r = ReferencePolicy.derive(p, refDecls(ref), p.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    assert(clue(r.policy.rows).exists(row => row.family == DerivedPolicy.Family.KeepName && row.upstream == "com.example.gfx.Cell#getMinWidth"))
  }

  test("the digest moves with the rows and is stable under row order") {
    val a = derive().policy
    val b = DerivedPolicy(a.rows.reverse)
    assertEquals(a.digest, b.digest)
    assertNotEquals(a.digest, DerivedPolicy(a.rows.tail).digest)
  }
