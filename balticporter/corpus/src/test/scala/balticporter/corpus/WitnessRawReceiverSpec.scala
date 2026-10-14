package balticporter.corpus

import balticporter.core.FrontendConfig
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Pipeline, PolicyBinder, RunScope }
import balticporter.transform.ElementWitnessTransform

import java.nio.file.Files

/** A dependent's call into a BASE class whose formal is the base's class parameter, on a raw receiver the witness phase filled: the argument takes java's unchecked conversion at the receiver's
  * argument.
  */
class WitnessRawReceiverSpec extends munit.FunSuite:

  private val base = Map(
    "b/Keeper.java" ->
      """package b;
        |public abstract class Keeper<A> {
        |  public void free (A a) {}
        |  protected abstract A make ();
        |}
        |""".stripMargin
  )

  private val dep = Map(
    "d/Holder.java" ->
      """package d;
        |import b.Keeper;
        |public class Holder<T> {
        |  T[] items;
        |  public Holder (int n) { items = (T[])new Object[n]; }
        |  public void put (Cell<T> c) { Cell.keep.free(c); }
        |  static class Cell<T> {
        |    T v;
        |    static final Keeper<Cell> keep = new Keeper<Cell>() { protected Cell make () { return new Cell(); } };
        |  }
        |}
        |""".stripMargin
  )

  test("the unchecked conversion reads a base class-parameter formal at a raw receiver's filled argument") {
    val root                                           = Files.createTempDirectory("witness-raw-receiver")
    def put(under: String, files: Map[String, String]) = files.foreach { (rel, body) =>
      val p = root.resolve(under).resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, body)
    }
    put("base", base)
    put("dep", dep)
    val types      = SpoonTir.buildModel(FrontendConfig(root.resolve("dep"), dep.keys.toList, Nil, resolutionRoots = List(root.resolve("base"))), lenient = true)
    val p          = SpoonTir.fromTypes(types)
    val own        = p.units.filter(u => p.symbolOf(u.symbol).exists(_.fullName.startsWith("d."))).map(_.symbol).toSet
    val phase      = new ElementWitnessTransform(witness = "demo.MkArray", subjectTypes = Map("d.Holder" -> List(0)), dropBound = Set("d.Holder"), boxedWitness = None)
    val (after, _) = Pipeline.runTraced(p, List(phase), new PolicyBinder(p, p.members, RunScope.of(own, Map.empty)))
    val out        = new TirEmitter(after).emit
    assert(
      clue(out.linesIterator.filter(_.contains("keep.free(")).mkString("\n")).contains("free(c.asInstanceOf[d.Holder.Cell[java.lang.Object]])")
    )
  }
