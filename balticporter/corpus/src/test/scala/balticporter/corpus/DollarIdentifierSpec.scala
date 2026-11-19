package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

import java.nio.file.{ Files, Path }

/** Scala 3.9 warns on a DEFINITION that spells `$` bare, and the emitter mints `$`-bearing names (renames, temporaries, labels, record and vararg binders). Every construct that mints one is emitted
  * here, COMPILED under `-Werror`, and RUN: the answer is java's own, so a binder spelled `` `n` @ (_: T) `` binds what `n: T` bound.
  */
class DollarIdentifierSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |import java.util.function.Function;
      |import java.util.function.Supplier;
      |class Res implements AutoCloseable {
      |  static StringBuilder log = new StringBuilder();
      |  final String n;
      |  Res(String n) { this.n = n; }
      |  String name() { return n; }
      |  public void close() { log.append("close-" + n + ";"); }
      |}
      |record Pt(int x, int y) {}
      |interface Op { int ap(int a, int b); }
      |public class Dollars {
      |  int size;
      |  int[] items = new int[4];
      |  int size() { return size; }
      |  int idx() { return 1; }
      |  static int add(int a, int b) { return a + b; }
      |  static int sum(int... xs) { int s = 0; for (int x : xs) s += x; return s; }
      |  static int firstNeg(Iterable<Integer> xs) { for (int x : xs) { if (x < 0) return x; } return 0; }
      |  public static String run() {
      |    StringBuilder sb = new StringBuilder();
      |    try (Res a = new Res("a"); Res b = new Res("b")) { sb.append("body;"); } catch (RuntimeException e) { sb.append("caught;"); }
      |    outer:
      |    for (int i = 0; i < 3; i++) {
      |      for (int j = 0; j < 3; j++) {
      |        if (j == 1) continue outer;
      |        if (i == 2) break outer;
      |        sb.append(i).append(j).append(';');
      |      }
      |    }
      |    Dollars d = new Dollars();
      |    d.items[d.idx()]++;
      |    int prev = d.size++;
      |    sb.append(prev).append(d.size).append(d.items[1]).append(d.size()).append(';');
      |    sb.append(new Pt(1, 2).equals(new Pt(1, 2))).append(new Pt(1, 2).hashCode() == new Pt(1, 2).hashCode()).append(';');
      |    sb.append(sum(1, 2, 3)).append(';');
      |    Function<String, Res> mk = Res::new;
      |    Function<Res, String> nm = Res::name;
      |    Op op = Dollars::add;
      |    sb.append(nm.apply(mk.apply("z"))).append(op.ap(2, 3)).append(';');
      |    Supplier<Integer> s = () -> { if (d.size > 0) return 1; return 2; };
      |    sb.append(s.get()).append(';');
      |    lbl: { if (d.size > 0) break lbl; sb.append("no;"); }
      |    int k = switch (prev) { case 0 -> { if (d.size > 0) yield 10; yield 20; } default -> 30; };
      |    sb.append(k).append(';');
      |    switch (prev) { case 0: if (d.size > 0) break; sb.append("x;"); default: sb.append("y;"); }
      |    sb.append(firstNeg(java.util.Arrays.asList(1, -2, 3))).append(';');
      |    return sb.toString() + Res.log;
      |  }
      |}
      |""".stripMargin

  private lazy val emitted: List[String] =
    val p  = Pipeline.run(SpoonTir.fromSource(src), Nil)
    val em = new TirEmitter(p)
    p.units.map(em.emitUnit)

  private lazy val compiled: Path =
    val dir   = Files.createTempDirectory("dollar-identifier-src")
    val files = emitted.zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("dollar-identifier-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val args   = "-Werror" :: "-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files
    val rep    = dotty.tools.dotc.Main.process(args.toArray)
    assert(
      !rep.hasErrors,
      s"the emission does not compile warning-free:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${emitted.mkString("\n")}"
    )
    out

  test("a pattern binder holding `$` is bound with `@`, a definition is backticked, a plain name is untouched") {
    assertEquals(TirEmitter.patBinder("thrown$1", "java.lang.Throwable"), "`thrown$1` @ (_: java.lang.Throwable)")
    assertEquals(TirEmitter.patBinder("s$"), "`s$` @ _")
    assertEquals(TirEmitter.patBinder("e", "T"), "e: T")
    assertEquals(TirEmitter.patBinder("other_", "T"), "other_ : T")
    assertEquals(TirEmitter.patBinder("_", "T"), "_: T")
    assertEquals(TirEmitter.escDef("size$field"), "`size$field`")
    assertEquals(TirEmitter.escDef("type"), "`type`")
    assertEquals(TirEmitter.escDef("size"), "size")
  }

  test("every minted `$` name compiles warning-free under scala 3.9 and the port answers what java answers") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("demo.Dollars").getMethod("run").invoke(null).asInstanceOf[String]
    // javac's own output for the same source
    assertEquals(got, "body;00;10;0111;truetrue;6;z5;1;10;-2;close-b;close-a;")
  }
