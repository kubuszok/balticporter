package balticporter.corpus

import balticporter.core.{ RuntimeMode, RuntimePlan }
import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

import java.nio.file.{ Files, Path }

/** An override of a member a runtime support parent declares PARENLESS takes the parent's arity: `isEmpty()` on a class re-parented from `AbstractCollection` onto the collection support trait is
  * emitted `isEmpty`, and so is every call of it. Compiled against the vendored support sources and run.
  */
class ShimOverrideArityEndToEndSpec extends PortSuite:

  private val src =
    """package shimarity;
      |import java.util.AbstractCollection;
      |import java.util.Iterator;
      |public class Bag<T> extends AbstractCollection<T> {
      |  private final Object[] items;
      |  private int size;
      |  public Bag(int cap) { items = new Object[cap]; }
      |  public boolean add(T t) { items[size++] = t; return true; }
      |  public int size() { return size; }
      |  public boolean isEmpty() { return size == 0; }
      |  public Iterator<T> iterator() {
      |    return new Iterator<T>() {
      |      int i = 0;
      |      public boolean hasNext() { return i < size; }
      |      @SuppressWarnings("unchecked") public T next() { return (T) items[i++]; }
      |    };
      |  }
      |  boolean same(Bag<T> other) { return isEmpty() == other.isEmpty(); }
      |}
      |public class Sub<T> extends Bag<T> {
      |  public Sub() { super(4); }
      |  public boolean isEmpty() { return super.isEmpty(); }
      |}
      |public class Run {
      |  public static String run() {
      |    Bag<String> b = new Bag<>(4);
      |    boolean before = b.isEmpty();
      |    b.add("x");
      |    Sub<String> s = new Sub<>();
      |    return before + "," + b.isEmpty() + "," + b.same(s) + "," + s.isEmpty();
      |  }
      |}
      |""".stripMargin

  private lazy val ported = port(src, new CollectionsTransform)

  test("the override and its calls take the support parent's parenless arity") {
    assertEmits(ported, "override def isEmpty: scala.Boolean")
    assertNotEmits(ported, "def isEmpty()")
    assertEmits(ported, "other.isEmpty")
    assertNotEmits(ported, "isEmpty()")
    // a member the support parent declares WITH parens keeps them
    assertEmits(ported, "def size(): scala.Int")
  }

  test("the port compiles against the support trait and answers what java answered") {
    val dir     = Files.createTempDirectory("shim-arity-src")
    val runtime = RuntimePlan(ported.plan.required, RuntimeMode.Vendored).sources.values.toList
    val files   = (runtime ++ ported.after.units.map(ported.emitter.emitUnit)).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("shim-arity-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${ported.out}")
    val loader = new java.net.URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("shimarity.Run").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "true,false,false,true")
  }
