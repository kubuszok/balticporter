package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Pipeline, RuleScope }
import balticporter.transform.ClassTableTransform

import java.nio.file.{ Files, Path }

/** A MINTED name→class table end to end: the table and its callers are COMPILED by the Scala compiler and RUN, answering what java answers — a listed name resolves, an unknown one throws the
  * library's wrapper around `ClassNotFoundException`, and listing a class does not run its initialiser.
  */
class ClassTableEndToEndSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Probe { public static int inits = 0; }
      |public class Seeded {
      |  static { Probe.inits++; }
      |  public Seeded() { }
      |  public String hello() { return "hi"; }
      |}
      |public class NoCtor { public NoCtor(int x) { } }
      |public class Failure extends RuntimeException {
      |  public Failure(String message, Throwable cause) { super(message, cause); }
      |}
      |public class Reflect {
      |  public static Class forName(String name) {
      |    try { return Class.forName(name); } catch (ClassNotFoundException e) { throw new Failure("Class not found: " + name, e); }
      |  }
      |  public static Object newInstance(String name) { return null; }
      |}
      |public class Use {
      |  static String fail(String name, boolean make) {
      |    try {
      |      if (make) Reflect.newInstance(name); else Reflect.forName(name);
      |      return "none";
      |    } catch (Failure e) {
      |      return e.getMessage() + "<" + e.getCause().getClass().getName();
      |    }
      |  }
      |  public static String run() {
      |    int before = Probe.inits;
      |    Class c = Reflect.forName("demo.Seeded");
      |    int afterLookup = Probe.inits;
      |    Seeded s = (Seeded) Reflect.newInstance("demo.Seeded");
      |    int afterNew = Probe.inits;
      |    return before + "," + afterLookup + "," + afterNew + "," + (c == Seeded.class) + "," + s.hello() + "," +
      |      fail("demo.Nope", false) + "," + fail("demo.Nope", true) + "," + fail("demo.NoCtor", true) + "," + Reflect.forName("demo.NoCtor").getName();
      |  }
      |}
      |""".stripMargin

  private val phase = new ClassTableTransform(
    Map("demo.Reflect#forName" -> "demo.Names#classFor", "demo.Reflect#newInstance" -> "demo.Names#create"),
    RuleScope.Only(Set("demo.Use")),
    List(
      ClassTableTransform.Table(
        "demo.Names",
        List("demo.Seeded", "demo.NoCtor"),
        construct = Some("create"),
        missing = Some(ClassTableTransform.Missing("demo.Failure", "Class not found: ", "Could not instantiate: "))
      )
    )
  )

  private lazy val compiled: Path =
    val p     = Pipeline.run(SpoonTir.fromSource(src), List(phase))
    val em    = new TirEmitter(p)
    val dir   = Files.createTempDirectory("class-table-e2e-src")
    val files = p.units.zipWithIndex.map { (u, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, em.emitUnit(u)); f.toString
    }
    val out      = Files.createTempDirectory("class-table-e2e-out")
    val stdlib   = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val reporter = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(
      !reporter.hasErrors,
      s"the emission does not compile:\n${reporter.allErrors.map(_.msg.message).mkString("\n")}\n${files.map(f => Files.readString(Path.of(f))).mkString("\n")}"
    )
    out

  test("the minted table compiles and runs with java's answers, and listing a class does not initialise it") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("demo.Use").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(
      got,
      List(
        "0", // nothing initialised yet
        "0", // the lookup answered the class, and did not initialise it
        "1", // constructing it did
        "true",
        "hi",
        "Class not found: demo.Nope<java.lang.ClassNotFoundException",
        "Class not found: demo.Nope<java.lang.ClassNotFoundException",
        "Could not instantiate: demo.NoCtor<java.lang.InstantiationException",
        "demo.NoCtor"
      ).mkString(",")
    )
    assert(phase.policyReport.findings.exists(_.key == "demo.NoCtor"))
  }
