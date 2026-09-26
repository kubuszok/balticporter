package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.{ NullabilityTransform, SamLambdaTransform }
import balticporter.transform.NullabilityTransform.Target

import java.nio.file.{ Files, Path }

/** An anonymous implementation of a functional interface whose method was retyped to the wrapper follows the retype once it is a LAMBDA: the parameters take the wrapper, the nested result `def` takes
  * it, and each valued `return` is coerced — the anonymous class no longer exists for the override graph to find. Compiled with a minimal wrapper and run.
  */
class NullabilitySamOverrideSpec extends PortSuite:

  private val W = "lowlevel.Nullable"

  private val src =
    """package demo;
      |public interface Filter {
      |  String filter(Item item, Item other);
      |  Filter defaultFilter = new Filter() {
      |    @Override public String filter(Item item, Item other) { return "slide"; }
      |  };
      |}
      |public class Item {}
      |public class World {
      |  static String check(final Item seen, final Item other, final Filter filter) {
      |    Filter f = new Filter() {
      |      @Override public String filter(Item item, Item o) {
      |        if (o == seen) return null;
      |        if (filter == null) return defaultFilter.filter(item, o);
      |        return filter.filter(item, o);
      |      }
      |    };
      |    return f.filter(new Item(), other);
      |  }
      |  public static String run() {
      |    final String mark = "cross";
      |    Item a = new Item();
      |    Filter mine = new Filter() {
      |      @Override public String filter(Item item, Item o) { return o == null ? null : mark; }
      |    };
      |    Filter lam = (item, o) -> { if (o == null) return null; return "lam"; };
      |    return check(a, a, null) + "," + check(new Item(), a, null) + "," + check(new Item(), a, mine) + "," + check(new Item(), a, lam);
      |  }
      |}
      |""".stripMargin

  private def phase = new NullabilityTransform(
    target = Target.Named(W),
    nullableMembers = Set("demo.Filter#filter", "demo.Filter#filter#item", "demo.Filter#filter#other")
  )

  private lazy val ported = port(src, new SamLambdaTransform, phase)

  private val wrapper =
    """package lowlevel
      |opaque type Nullable[+A] = A | Null
      |object Nullable:
      |  def apply[A](a: A | Null): Nullable[A] = a
      |  def empty[A]: Nullable[A] = null
      |  extension [A](n: Nullable[A])
      |    def isEmpty: Boolean = n == null
      |    def get: A = if n == null then throw new NoSuchElementException("empty") else n.asInstanceOf[A]
      |    def orNull: A = n.asInstanceOf[A]
      |""".stripMargin

  private lazy val compiled: Path =
    val dir   = Files.createTempDirectory("nullability-sam-src")
    val files = (wrapper :: ported.after.units.map(ported.emitter.emitUnit)).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("nullability-sam-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val args   = "-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files
    val rep    = dotty.tools.dotc.Main.process(args.toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${ported.out}")
    out

  test("the lambda's parameters and its nested result take the SAM's retyped slots") {
    assertEmits(ported, s"(item: $W[demo.Item], o: $W[demo.Item]) =>")
    assertEmits(ported, s"def body$$1(): $W[java.lang.String]")
    assertNotEmits(ported, "(item: demo.Item, o: demo.Item) =>")
  }

  test("a valued return is coerced at the return, and the retyped parameters pass straight to the SAM call") {
    assertEmits(ported, s"return $W.empty")
    assertEmits(ported, "return demo.Filter.defaultFilter.filter(item, o)")
  }

  test("the port compiles and the lambdas answer what java's anonymous classes answered") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("demo.World").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "null,slide,cross,lam")
  }
