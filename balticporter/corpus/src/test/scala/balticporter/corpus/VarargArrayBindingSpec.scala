package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.HeapPollutionCheck

import java.nio.file.{ Files, Path }

/** The array a `T*` parameter binds is one of the RUNTIME class java's would have: a caller's `ArraySeq` is taken only where its backing array is that class (an empty one's is not), anything else is
  * copied at the element's `ClassTag`; a generic element's array is built by the java CALLER at its own type; a java `null` at the slot is a null ARRAY. Compiled and run.
  */
class VarargArrayBindingSpec extends PortSuite:

  private val src =
    """package vb;
      |public class Anim<T> {
      |  final T[] frames;
      |  public Anim(float d, T... f) { frames = f; }
      |  public T[] getFrames() { return frames; }
      |}
      |public class Types {
      |  public static int count(Class<?>... cs) { Class<?>[] a = cs; return a.length; }
      |  public static int nulls(String... xs) { return xs == null ? -1 : xs.length; }
      |  public static int first(String... xs) { return xs == null ? -1 : (xs[0] == null ? 0 : 1); }
      |}
      |public class Run {
      |  public static String run() {
      |    String[] got = new Anim<String>(0.1f, "a", "b", "c").getFrames();
      |    return got.length + "," + Types.count() + "," + Types.count(String.class) + "," + Types.nulls(null) + "," + Types.first((String) null);
      |  }
      |}
      |""".stripMargin

  private val driver =
    """package vb
      |object Drive {
      |  def run(): String = {
      |    val empty = Types.count(scala.collection.immutable.ArraySeq.empty[java.lang.Class[?]]*)
      |    val listed = Types.count(List(classOf[String], classOf[Integer])*)
      |    s"${Run.run()}|$empty,$listed"
      |  }
      |}
      |""".stripMargin

  private lazy val ported = port(src)

  test("an ArraySeq's backing array is taken only at the element's runtime class") {
    assertEmits(
      ported,
      "case a$: scala.collection.immutable.ArraySeq[?] if a$.unsafeArray.getClass.getComponentType eq scala.reflect.classTag["
    )
  }

  test("a generic element's array is built by the java caller, at the caller's type, and spread") {
    assertEmits(ported, "new vb.Anim[java.lang.String](0.1f, scala.Array[java.lang.String](\"a\", \"b\", \"c\")*)")
  }

  test("a java `null` at the slot is a null array, spread — never one null element") {
    assertEmits(ported, "vb.Types.nulls(null*)")
    assertEmits(ported, "vb.Types.first(null.asInstanceOf[java.lang.String])")
  }

  test("a generic vararg nothing can build an array of is counted") {
    val rows = HeapPollutionCheck.check(ported.after, ported.after.units).filter(_.issue == HeapPollutionCheck.Issue.UnreifiedCopy)
    assertEquals(clue(rows).map(_.owner), List("vb.Anim#<init>"))
  }

  test("the port compiles and runs as java did") {
    val dir   = Files.createTempDirectory("vararg-binding-src")
    val files = (ported.after.units.map(ported.emitter.emitUnit) :+ driver).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("vararg-binding-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${ported.out}")
    val loader = new java.net.URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader)
    assertEquals(loader.loadClass("vb.Drive").getMethod("run").invoke(null).asInstanceOf[String], "3,0,1,-1,0|0,2")
  }
