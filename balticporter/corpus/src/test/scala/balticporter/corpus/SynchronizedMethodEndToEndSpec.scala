package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

import java.nio.file.{ Files, Path }

/** A java `synchronized` METHOD keeps its monitor: the port is COMPILED under `-Werror` and two threads race a read-modify-write through instance and static synchronized methods, which lose updates
  * when the body runs unlocked. The same methods keep their value and their early `return`s.
  */
class SynchronizedMethodEndToEndSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Counter {
      |  private int n;
      |  private static int s;
      |  public synchronized int bump() {
      |    int v = n;
      |    if (v < 0) return -1;
      |    Thread.yield();
      |    n = v + 1;
      |    return n;
      |  }
      |  public static synchronized void bumpStatic() {
      |    int v = s;
      |    Thread.yield();
      |    s = v + 1;
      |  }
      |  public static synchronized int staticValue() { return s; }
      |  public synchronized int firstAbove(int[] xs, int k) {
      |    for (int x : xs) { if (x > k) return x; }
      |    return -1;
      |  }
      |  public synchronized int spin(int k) {
      |    int i = 0;
      |    while (true) { i++; if (i > k) return i; }
      |  }
      |  public int value() { return n; }
      |  public Runnable anon() {
      |    return new Runnable() { public synchronized void run() { n = n + 0; } };
      |  }
      |}
      |public class Race {
      |  public static String run() throws Exception {
      |    final Counter c = new Counter();
      |    final int per = 20000;
      |    Runnable r = new Runnable() {
      |      public void run() { for (int i = 0; i < per; i++) { c.bump(); Counter.bumpStatic(); } }
      |    };
      |    Thread a = new Thread(r);
      |    Thread b = new Thread(r);
      |    a.start(); b.start(); a.join(); b.join();
      |    c.anon().run();
      |    return c.value() + "," + Counter.staticValue() + "," + c.firstAbove(new int[] { 1, 5, 9 }, 4) + "," +
      |      c.firstAbove(new int[] { 1 }, 4) + "," + c.spin(3);
      |  }
      |}
      |""".stripMargin

  private lazy val emitted: List[String] =
    val p  = Pipeline.run(SpoonTir.fromSource(src), Nil)
    val em = new TirEmitter(p)
    p.units.map(em.emitUnit)

  private lazy val compiled: Path =
    val dir   = Files.createTempDirectory("synchronized-method-e2e-src")
    val files = emitted.zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("synchronized-method-e2e-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val args   = "-Werror" :: "-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files
    val rep    = dotty.tools.dotc.Main.process(args.toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${emitted.mkString("\n")}")
    out

  test("an instance method locks `this` and a static one the companion object, around the whole body") {
    val all = emitted.mkString("\n")
    assert(clue(all).contains("def bump(): scala.Int = this.synchronized {"))
    assert(all.contains("def bumpStatic(): scala.Unit = Counter.synchronized {"), all)
    // the method nothing marked `synchronized` stays unlocked
    assert(all.contains("def value(): scala.Int = {"), all)
  }

  test("two threads through the synchronized methods lose no update, and the value and early returns survive") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("demo.Race").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "40000,40000,5,-1,4")
  }
