package balticporter.transform

import balticporter.emit.{InjectedSurface, TirEmitter}
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*
import munit.FunSuite

/** Calls into a dropped type follow the injected replacement's spelling (`DESIGN.md` §8.28). */
class InjectedSurfaceFollowTransformSpec extends FunSuite:
  private val javaSrc =
    """package q;
      |public class Pixmap {
      |  public enum Format { A, B }
      |  public int getWidth() { return 1; }
      |  public void setBlend(int b) {}
      |}
      |""".stripMargin + "\n" +
    """package q;
      |public class User {
      |  public String f(Pixmap p, Pixmap.Format fm) { p.setBlend(p.getWidth()); return fm.name(); }
      |}
      |""".stripMargin

  private def surface(): InjectedSurface.Surface =
    val dir = java.nio.file.Files.createTempDirectory("follow")
    java.nio.file.Files.createDirectories(dir.resolve("sge/graphics"))
    java.nio.file.Files.writeString(dir.resolve("sge/graphics/Pixmap.scala"),
      """package sge
        |package graphics
        |class Pixmap {
        |  def width: Int = 1
        |  var blend: Int = 0
        |  enum Format { case A, B }
        |}
        |""".stripMargin)
    InjectedSurface.fromRoots(List(dir))

  private lazy val emitted: String =
    val p0 = SpoonTir.fromSource(javaSrc)
    val phase = new InjectedSurfaceFollowTransform(surface(), Map("q.Pixmap" -> "sge.graphics.Pixmap"))
    val p1 = phase.run(p0)
    val user = p1.units.find(u => p1.symbolOf(u.symbol).exists(_.name == "User")).get
    new TirEmitter(p1.rebuilt(units = List(user))).emit

  test("a getter follows the injected property, parens gone") {
    assert(emitted.contains("p.width"), emitted); assert(!emitted.contains("getWidth"), emitted)
  }
  test("a setter follows the injected var as `x_=`") {
    assert(emitted.contains("blend_="), emitted)
  }
  test("a java enum's name() against a scala 3 enum replacement is toString") {
    assert(emitted.contains("fm.toString"), emitted)
  }
