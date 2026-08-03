package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** A `static { }` block is not a NAME, so it may never reach an `export` selector list.
  *
  * Java interface/parent constants are `static` and inherited; Scala companions are not, so the
  * emitter re-exports each static-bearing parent's companion and excludes the subclass's OWN static
  * names from that export (a subtype may legitimately redeclare a parent constant). The exclusion
  * list is built from the subclass's static MEMBERS — and a `static { … }` block is carried as one,
  * under the JVM's own name for it, `<clinit>`.
  *
  * No Scala identifier can spell that, backticks included: there is no member at that name to hide.
  * Emitted, it is `export P.{<clinit> => _, *}`, which dotty's parser reads as an XML start tag
  * ("an identifier expected, but $XMLSTART$< found") and the whole file fails to compile.
  *
  * It needs BOTH halves at once — a class that inherits statics AND declares an initializer block —
  * which is why six ports never produced it: libGDX core has 605 types and not one of them, while
  * gdx-gltf's attribute hierarchy has three (`PBRColorAttribute`, `PBRCubemapAttribute`,
  * `PBRTextureAttribute`, each extending a libGDX `Attribute` subclass whose constants it
  * re-exports and each registering its own aliases in a `static { }`).
  *
  * The NEGATIVE half is asserted beside it: an ordinary static field of the subclass must still be
  * excluded, or this fix would have bought the syntax error back as a duplicate definition.
  */
class StaticExportInitBlockSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |/** the parent carries the statics an heir must re-export. */
      |class Parent {
      |  public static final long BASE = 1L;
      |  public static final String SHARED = "shared";
      |}
      |/** inherits statics AND declares an initializer block — both halves at once. */
      |class Heir extends Parent {
      |  public static final String SHARED = "mine";
      |  public static long REGISTERED;
      |  static { REGISTERED = BASE + 1; }
      |}
      |""".stripMargin

  private val out = new TirEmitter(SpoonTir.fromSource(src)).emit

  test("a static initializer block never appears in an export selector") {
    assert(clue(out).contains("export demo.Parent."), "the heir must still re-export the parent's companion")
    assert(!out.contains("<clinit>"), s"`<clinit>` reached an export selector:\n$out")
    assert(!out.contains("<initblock>"), s"`<initblock>` reached an export selector:\n$out")
  }

  test("an ordinary redeclared static IS still excluded — the exclusion itself is not disabled") {
    assert(clue(out).contains("SHARED => _"),
      "the heir redeclares SHARED, so the parent's must be hidden or the export is a duplicate definition")
  }
