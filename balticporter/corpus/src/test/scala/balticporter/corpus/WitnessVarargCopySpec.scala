package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.{ ElementWitnessCheck, ElementWitnessTransform, GlobalsToImplicitsTransform }

import java.nio.file.{ Files, Path }

/** A repeated parameter at an element type whose `Object` bound the witness phase dropped: a caller's `Seq` that is not an array wrapper (every literal vararg on Scala.js) is copied into an array the
  * WITNESS makes, so a primitive element lands in a primitive array. Compiled and run on the JVM with a `List` spread, which reaches the copy exactly as a JS literal does.
  */
class WitnessVarargCopySpec extends PortSuite:

  private val src =
    """package wv;
      |public class Bag<T> {
      |  T[] items;
      |  int size;
      |  public Bag(int n) { items = (T[]) new Object[n]; }
      |  public void addAll(T... xs) { System.arraycopy(xs, 0, items, size, xs.length); size += xs.length; }
      |  public T get(int i) { return items[i]; }
      |}
      |public class Pile<E> {
      |  int n;
      |  public void put(E... xs) { n += xs.length; }
      |}
      |""".stripMargin

  private val witness =
    """package demo
      |trait MkArray[T] { def create(n: Int): Array[T] }
      |object MkArray {
      |  given MkArray[Int] with { def create(n: Int): Array[Int] = new Array[Int](n) }
      |  given MkArray[String] with { def create(n: Int): Array[String] = new Array[String](n) }
      |}
      |""".stripMargin

  private val driver =
    """package wv
      |object Drive {
      |  def run(): String = {
      |    val ints = new Bag[Int](4)
      |    ints.addAll(List(1, 2, 3)*)
      |    val strs = new Bag[String](2)
      |    strs.addAll(List("a", "b")*)
      |    s"${ints.items.getClass.getSimpleName}:${ints.get(2)}:${strs.get(1)}"
      |  }
      |}
      |""".stripMargin

  private def phase = new ElementWitnessTransform(
    witness = "demo.MkArray",
    subjectTypes = Map("wv.Bag" -> List(0)),
    dropBound = Set("wv.Bag", "wv.Pile"),
    boxedWitness = None
  )

  private lazy val (ported, witnessPhase) =
    val p        = phase
    val threaded = new GlobalsToImplicitsTransform(requiredGivens = ElementWitnessTransform.constructorGivens(p.subjectTypes, p.witness))
    (port(src, threaded, p), p)

  test("the copy of a non-array Seq is made by the witness, never boxed through `toArray[Any]`") {
    assertEmits(ported, "val c$: scala.Array[T] = scala.Predef.summon[demo.MkArray[T]].create(s$.length); s$.copyToArray(c$); c$")
  }

  test("an unbound element with no witness in scope keeps the boxed copy, counted") {
    val rows = witnessPhase.refusals(ported.after, ported.after.units).filter(_.issue == ElementWitnessCheck.Issue.VarargCopy)
    assertEquals(clue(rows).map(_.subject), List("wv.Pile"))
    assertEmits(ported, "def put(xs: E*)")
    assertEmits(ported, "s$.toArray[scala.Any].asInstanceOf[scala.Array[E]]")
  }

  test("the port compiles and a primitive element lands in a primitive array") {
    val dir   = Files.createTempDirectory("witness-vararg-src")
    val files = (ported.after.units.map(ported.emitter.emitUnit) ++ List(witness, driver)).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("witness-vararg-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${ported.out}")
    val loader = new java.net.URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("wv.Drive").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "int[]:3:b")
  }
