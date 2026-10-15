package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.IdiomKind
import balticporter.transform.SamLambdaTransform

import java.nio.file.{ Files, Path }

/** A java `T...` the port DECLARES is emitted as scala's repeated `T*`: a call passing elements passes them, a call passing java's array spreads it, and the body reads the array java handed it — the
  * caller's own, so a write through it is seen by the caller as in java. Compiled and run, generic, primitive, constructor and enum shapes included.
  */
class OwnedVarargsEndToEndSpec extends PortSuite:

  private val src =
    """package va;
      |import java.util.*;
      |public class Rooms {
      |  final List<String> types = new ArrayList<>();
      |  public void addRoomTypes(String... ts) { for (String t : ts) types.add(t); }
      |  public static int sum(int... xs) { int s = 0; for (int i = 0; i < xs.length; i++) s += xs[i]; return s; }
      |  public static <T> T last(T... xs) { return xs.length == 0 ? null : xs[xs.length - 1]; }
      |  public static void fill(String v, String... slots) { for (int i = 0; i < slots.length; i++) slots[i] = v; }
      |  public static int count(Object... os) { return os == null ? -1 : os.length; }
      |}
      |public class Pair {
      |  final String[] parts;
      |  public Pair(String head, String... rest) { parts = new String[rest.length + 1]; parts[0] = head; System.arraycopy(rest, 0, parts, 1, rest.length); }
      |  int size() { return parts.length; }
      |}
      |public enum Kind {
      |  A("x", "y"), B();
      |  private final int n;
      |  Kind(String... names) { this.n = names.length; }
      |  int n() { return n; }
      |}
      |public interface Sink { int take(String... xs); }
      |public class Tex {
      |  final int n;
      |  public Tex(String... paths) { n = paths.length; }
      |  public Tex(Integer... ids) { n = 100 + ids.length; }
      |  static int of(String[] ps) { return new Tex(ps).n + new Tex("a").n + new Tex(1, 2).n + new Tex("b", "c") { }.n; }
      |}
      |public class Coll {
      |  static final Class<?>[] K = { String.class };
      |  final int n;
      |  public Coll(Class<?>... cs) { n = cs.length; }
      |  static Class<?>[] cat(Class<?>[] a, Class<?>[] b) { Class<?>[] r = new Class<?>[a.length + b.length]; System.arraycopy(a, 0, r, 0, a.length); System.arraycopy(b, 0, r, a.length, b.length); return r; }
      |}
      |public abstract class Infl {
      |  public List<String> ms;
      |  public Infl() { ms = new ArrayList<>(); }
      |  public Infl(String... ms) { this.ms = new ArrayList<>(); for (String m : ms) this.ms.add(m); }
      |  public Infl(Infl o) { this(copy(o.ms)); }
      |  static String[] copy(List<String> l) { String[] r = new String[l.size()]; for (int i = 0; i < r.length; i++) r[i] = l.get(i); return r; }
      |}
      |public class Single extends Infl {
      |  public Single() { super(); }
      |  public Single(String... ms) { super(ms); }
      |}
      |public class TA {
      |  final int n;
      |  public TA(String... paths) { this(handles(paths)); }
      |  public TA(Integer... ids) { n = ids.length; }
      |  static Integer[] handles(String... ps) { Integer[] r = new Integer[ps.length]; for (int i = 0; i < ps.length; i++) r[i] = i; return r; }
      |}
      |public class SubColl extends Coll {
      |  public SubColl(Class<?>... cs) { super(cs.length == 0 ? K : cat(K, cs)); }
      |}
      |public class Run {
      |  public static String run() {
      |    final int base = 10;
      |    Sink sink = new Sink() { public int take(String... xs) { return base + xs.length; } };
      |    Rooms r = new Rooms();
      |    r.addRoomTypes("a", "b");
      |    String[] more = { "c" };
      |    r.addRoomTypes(more);
      |    r.addRoomTypes();
      |    int[] nums = { 1, 2, 3 };
      |    String[] slots = new String[2];
      |    Rooms.fill("z", slots);
      |    return r.types + "," + Rooms.sum(nums) + "," + Rooms.sum(4, 5) + "," + Rooms.last("p", "q") + "," +
      |      slots[0] + slots[1] + "," + Rooms.count(nums) + "," + Rooms.count(1, 2) + "," + Rooms.count((Object[]) null) + "," +
      |      new Pair("h", "i", "j").size() + "," + Kind.A.n() + Kind.B.n() + "," + sink.take("u", "v") + "," + Tex.of(new String[] { "p", "q" }) + "," + new SubColl().n + new SubColl(Integer.class).n + "," +
      |      new Single("a", "b").ms.size() + new TA("p", "q", "r").n + "," + Lay.V.conv("a", "b") + Lay.H.conv(1) + "," +
      |      new Sub0().f("x") + new Sub0().f(1, 2);
      |  }
      |}
      |public interface Layout { int conv(String... xs); int conv(Integer... xs); }
      |public enum Lay implements Layout {
      |  V { @Override public int conv(Integer... xs) { return 10 + xs.length; } },
      |  H { @Override public int conv(Integer... xs) { return 20 + xs.length; } };
      |  @Override public int conv(String... xs) { Integer[] r = new Integer[xs.length]; for (int i = 0; i < r.length; i++) r[i] = i; return conv(r); }
      |}
      |public class Base0 { public int f(String... a) { return a.length; } }
      |public class Sub0 extends Base0 {
      |  @Override public int f(String... a) { return 100 + a.length; }
      |  public int f(Integer... b) { return 200 + b.length; }
      |}
      |""".stripMargin

  private lazy val ported = port(src, new SamLambdaTransform)

  test("the declaration is repeated and each call passes elements or spreads java's array") {
    assertEmits(ported, "def addRoomTypes(ts: java.lang.String*)")
    assertEmits(ported, "r.addRoomTypes(\"a\", \"b\")")
    assertEmits(ported, "r.addRoomTypes(more*)")
    assertEmits(ported, "def sum(xs: scala.Int*)")
    assertNotEmits(ported, "new scala.Array[java.lang.String](\"a\"")
    // a METHOD binds the array once; a CONSTRUCTOR reads it in place
    assertEmits(ported, "val ts$arr: scala.Array[java.lang.String] = (ts match {")
    assertEmits(ported, "class Pair(head$p: java.lang.String, rest$p: java.lang.String*)")
    assertEmits(ported, "enum Kind(names: java.lang.String*)")
  }

  test("overloads told apart only by the vararg's element type keep java's arrays — repeated, both erase to one `Seq`") {
    assertEmits(ported, "def this(paths: scala.Array[java.lang.String])")
    assertEmits(ported, "new Tex(scala.Array[java.lang.String](\"a\"))")
    assertEmits(ported, "new Tex(ps)")
  }

  test("an INHERITED overload counts, and an override takes the form of the member it overrides") {
    // the enum's own `conv(String...)` sits beside the interface's `conv(Integer...)`, and each
    // constant body overrides the interface's: all keep java's arrays
    assertEmits(ported, "override def conv(xs: scala.Array[java.lang.String]): scala.Int")
    assertEmits(ported, "override def conv(xs: scala.Array[java.lang.Integer]): scala.Int")
    assertNotEmits(ported, "def conv(xs: java.lang.Integer*)")
    // an override of a repeated parent member stays repeated; its new sibling takes the array
    assertEmits(ported, "override def f(a: java.lang.String*): scala.Int")
    assertEmits(ported, "def f(b: scala.Array[java.lang.Integer]): scala.Int")
  }

  test("an anonymous implementation of a repeated SAM stays a class — a function literal cannot declare `T*`") {
    assertIdiomRefuses(ported, IdiomKind.SamLambda, "RepeatedParam")
    assertEmits(ported, "override def take(xs: java.lang.String*): scala.Int")
  }

  test("the port compiles and runs as java did — a write through the parameter reaches the caller's array") {
    val dir   = Files.createTempDirectory("owned-varargs-src")
    val files = ported.after.units.map(ported.emitter.emitUnit).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("owned-varargs-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${ported.out}")
    val loader = new java.net.URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("va.Run").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "[a, b, c],6,9,q,zz,1,2,-1,3,20,12,107,12,23,1221,101202")
  }
