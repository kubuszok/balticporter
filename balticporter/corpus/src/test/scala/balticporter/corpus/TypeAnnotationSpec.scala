package balticporter.corpus

import balticporter.core.AnnotationPolicy
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite

/** A TYPE's argument-bearing annotation, from the harvest to the emitted text — `ENGINE-LIMITS.md`
  * T16. */
class TypeAnnotationSpec extends PortSuite:

  private val src =
    """package demo;
      |@interface Ser { Class<?> using(); String why() default ""; }
      |@Ser(using = Model.Codec.class, why = "framework")
      |interface Model { class Codec {} }
      |""".stripMargin

  private def emitted(policy: AnnotationPolicy): String =
    new TirEmitter(SpoonTir.fromSource(src, annotations = policy)).emit

  test("claimed: the annotation reaches the emitted type, with its arguments") {
    val out = emitted(AnnotationPolicy(List("demo.")))
    assert(clue(out).contains("@demo.Ser("))
    assert(out.contains("classOf[demo.Model.Codec]") || out.contains("classOf[demo.Model$Codec]"), clue(out))
    assert(out.contains("\"framework\""))
  }

  test("A NAMED ELEMENT GOES THROUGH `esc` — `using` is a Scala keyword") {
    // Java's identifier space is not Scala's, and this is not hypothetical: the FIRST
    // argument-bearing annotation the engine ever carried on a type names its element `using`.
    // Un-escaped it is a parse error in the middle of a declaration — which is worse than the
    // dropped annotation it replaced, because it takes the file with it and names nothing.
    val out = emitted(AnnotationPolicy(List("demo.")))
    assert(clue(out).contains("`using` = "), "the element name was written raw")
    assert(!out.contains("(using = "), "an un-escaped `using` opens a using clause")
  }

  test("unclaimed: nothing is emitted, and the drop is REPORTED rather than emitted bare") {
    // §1(b)'s default. `@Ser` where java wrote `@Ser(using = …)` is a DIFFERENT annotation, so the
    // marker form is never the fallback; `omissions` is where a port reads the residue.
    val out = emitted(AnnotationPolicy.none)
    assert(!clue(out).contains("@demo.Ser"))
    val p = SpoonTir.fromSource(src)
    val s = p.symbols.all.find(_.fullName == "demo.Model").getOrElse(fail("no demo.Model"))
    assertEquals(s.droppedAnnotations, List("demo.Ser"))
  }
