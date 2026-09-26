package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.RuleScope
import balticporter.transform.{ BeanPropertyTransform, NullaryArityTransform }

import java.nio.file.{ Files, Path }

/** A method reference to an accessor a phase respelled — a bean pair renamed to a property, a getter made parenless — is lowered to a lambda over the member's FINAL name and arity, bound and unbound,
  * so neither phase refuses on it. Compiled and run.
  */
class AccessorMethodRefEndToEndSpec extends PortSuite:

  private val src =
    """package g;
      |import java.util.*;
      |import java.util.function.*;
      |public class Edge<V> {
      |  private float weight;
      |  public Edge(float w) { this.weight = w; }
      |  public float getWeight() { return weight; }
      |  public void setWeight(float w) { this.weight = w; }
      |}
      |public class Size {
      |  private final int n;
      |  public Size(int n) { this.n = n; }
      |  public int getN() { return n; }
      |}
      |public class Run {
      |  public static String run() {
      |    List<Edge<String>> es = new ArrayList<>();
      |    es.add(new Edge<>(3f));
      |    es.add(new Edge<>(1f));
      |    es.sort(Comparator.comparing(Edge<String>::getWeight));
      |    BiConsumer<Edge<String>, Float> set = Edge::setWeight;
      |    set.accept(es.get(0), 5f);
      |    Edge<String> e = es.get(1);
      |    Supplier<Float> s = e::getWeight;
      |    Size z = new Size(7);
      |    Supplier<Integer> zs = z::getN;
      |    Function<Size, Integer> zf = Size::getN;
      |    return es.get(0).getWeight() + "," + s.get() + "," + zs.get() + "," + zf.apply(z);
      |  }
      |}
      |""".stripMargin

  private lazy val ported = port(
    src,
    new BeanPropertyTransform(Map("g.Edge#weight" -> "getWeight/setWeight")),
    new NullaryArityTransform(RuleScope.Only(Set("g.Size")))
  )

  test("the references read the property and the parenless getter, never java's accessor") {
    assertEmits(ported, "self$.weight)")
    assertEmits(ported, "self$.weight_=(a0$)")
    assertEmits(ported, "(() => recv$.weight)")
    assertEmits(ported, "def getN: scala.Int")
    assertEmits(ported, "(() => recv$.getN)")
    assertEmits(ported, "self$.getN)")
    assertNotEmits(ported, "getWeight")
    assertNotEmits(ported, "getN()")
  }

  test("the port compiles and each reference answers what java's did") {
    val dir   = Files.createTempDirectory("accessor-ref-src")
    val files = ported.after.units.map(ported.emitter.emitUnit).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("accessor-ref-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${ported.out}")
    val loader = new java.net.URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("g.Run").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "5.0,3.0,7,7")
  }
