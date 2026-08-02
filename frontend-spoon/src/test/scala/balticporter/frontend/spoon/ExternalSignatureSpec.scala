package balticporter.frontend.spoon

import balticporter.core.FrontendConfig
import balticporter.tir.*
import balticporter.tir.TypeRepr.*

import java.nio.file.Files

/** An EXTERNAL member's `MethodType` — `ENGINE-LIMITS.md` K15's frontend half.
  *
  * Every external member the frontend interned used to carry `NoType`, so no phase could ask what a
  * method the program does not declare TAKES or RETURNS. That is what blocked the consumer half of
  * the collection boundary: a retyped scala collection handed to a class file's `java.util.*` formal
  * is a break nothing in the pipeline could see.
  *
  * Both directions are pinned here, because only one of them is a feature. A signature that is
  * WRONG is worse than none: the rendering may not consult the CALLER's type-parameter scope, and a
  * class file the parse could only partially resolve must leave the member signature-less rather
  * than produce an arity-correct-looking answer with a hole in it.
  */
class ExternalSignatureSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |import java.util.List;
      |import java.util.Set;
      |import java.util.ArrayList;
      |class Uses<E> {
      |  java.util.regex.Matcher m(java.util.regex.Pattern p, CharSequence cs) { return p.matcher(cs); }
      |  String join(Iterable<String> parts) { return String.join(",", parts); }
      |  int find(String s) { return s.indexOf("x"); }
      |  List<String> made() { return new ArrayList<String>(10); }
      |  void addTo(Set<E> xs, E e) { xs.add(e); }
      |  Object keep(Object o) { return java.util.Objects.requireNonNull(o); }
      |  boolean self(Object a, Object b) { return a.equals(b); }
      |  int own(int a) { return a; }
      |  int callsOwn() { return own(1); }
      |}
      |""".stripMargin

  private val program = SpoonTir.fromSource(src)

  /** an EXTERNAL member's symbol, by its owner's fully-qualified name and its own simple name.
    *
    * An external member's `fullName` is the INTERNING KEY (`@8#matcher(java.lang.CharSequence)`),
    * never `owner#name` — that is deliberate (`ENGINE-LIMITS.md` P4: the key is what the emitter,
    * the nested-path builder and the rename all read). So the lookup goes through the owner, which
    * is the field P4 added for exactly this. */
  private def external(owner: String, name: String): Symbol =
    val found = program.symbols.all.toList.filter { s =>
      s.name == name && s.owner != SymId.None &&
        program.symbols.get(s.owner).exists(_.fullName == owner)
    }
    found match
      case List(one) => one
      case Nil       => fail(s"no external member $owner#$name")
      case many      => fail(s"$owner#$name is ambiguous here: ${many.map(_.fullName)}")

  private def fqn(t: TypeRepr): String = t match
    case TypeRef(_, s)       => program.symbols.get(s).map(_.fullName).getOrElse("?")
    case AppliedType(tc, as) => s"${fqn(tc)}[${as.map(fqn).mkString(",")}]"
    case TypeBounds(NoType, NoType) => "?"
    case TypeBounds(NoType, hi)     => s"? <: ${fqn(hi)}"
    case TypeBounds(lo, NoType)     => s"? >: ${fqn(lo)}"
    case NoType              => "<none>"
    case other               => other.toString

  private def sig(s: Symbol): Option[(List[String], String)] = s.info match
    case MethodType(ps, ret, _) => Some(ps.map((_, t) => fqn(t)) -> fqn(ret))
    case _                      => scala.None

  // -- the feature: a class file's signature is now readable ---------------------------------

  test("a fully-resolvable external method carries its MethodType") {
    assertEquals(sig(external("java.util.regex.Pattern", "matcher")),
                 Some(List("java.lang.CharSequence") -> "java.util.regex.Matcher"))
  }

  test("a GENERIC formal keeps its exact head and records `?` for what the class file erased") {
    // `String.join(CharSequence, Iterable<? extends CharSequence>)`. Spoon reconstructs a shadow
    // type by REFLECTION, so the second parameter arrives as `Iterable<T>` — the interface's own
    // formal, echoed — and there is no `CharSequence` bound left to read. `Iterable[?]` is what was
    // actually read: the head exact, the argument saying the class file does not say. Recording the
    // head is the whole point, because the head is the whole of the question a boundary asks.
    assertEquals(sig(external("java.lang.String", "join")),
                 Some(List("java.lang.CharSequence", "java.lang.Iterable[?]") -> "java.lang.String"))
  }

  test("a type variable at an ARGUMENT is `?`; the same variable at the SLOT is a refusal") {
    // Two faces of one fact, and they must not answer the same way. `Collections.unmodifiableList`
    // takes a `List<T>`: the HEAD is a fact about the class file and the argument is not, so the
    // slot records `java.util.List[?]`. `Set.add(E)` IS the variable, so there is no head to
    // record and nothing honest to say — see the refusal tests below.
    val p = SpoonTir.fromSource(
      """package demo;
        |class C { java.util.List<String> u(java.util.List<String> s) {
        |  return java.util.Collections.unmodifiableList(s); } }
        |""".stripMargin)
    val m = p.symbols.all.toList.filter(s =>
      s.name == "unmodifiableList" && s.owner != SymId.None &&
        p.symbols.get(s.owner).exists(_.fullName == "java.util.Collections"))
    assertEquals(m.size, 1)
    m.head.info match
      case MethodType(List((_, AppliedType(TypeRef(_, h), List(TypeBounds(NoType, NoType))))), _, _) =>
        assertEquals(p.symbols.get(h).map(_.fullName), Some("java.util.List"))
      case other => fail(s"expected java.util.List[?] at the formal, got $other")
  }

  test("…a primitive result, and a primitive formal") {
    assertEquals(sig(external("java.lang.String", "indexOf")),
                 Some(List("java.lang.String") -> "scala.Int"))
  }

  test("an external CONSTRUCTOR's result is Unit — the grammar execDef uses for our own") {
    assertEquals(sig(external("java.util.ArrayList", "<init>")), Some(List("scala.Int") -> "scala.Unit"))
  }

  // -- the refusals: never a guess ------------------------------------------------------------

  test("a formal that is the CALLEE's type variable leaves the member signature-less") {
    // `java.util.Set<E>.add(E)`. The caller `Uses<E>` declares an `E` of its OWN, and an external
    // symbol is interned ONCE and never clobbered — so a scope-directed rendering would let the
    // first call site in the run decide this signature, with the wrong `E` in it.
    assertEquals(external("java.util.Set", "add").info, NoType)
  }

  test("a type-variable RESULT leaves it signature-less too") {
    // `<T> T requireNonNull(T)` — and this is the shape `CollectionsTransform.passesThrough`
    // answers structurally today precisely because it could not be asked here.
    assertEquals(external("java.util.Objects", "requireNonNull").info, NoType)
  }

  test("the refusal is ALL-OR-NONE: one unrenderable slot and there is no signature at all") {
    // A partially-resolvable class file is one the parse was LENIENT about, so the slots that DID
    // resolve are not evidence either. `Set#add` above is the one-parameter face of this; a member
    // with a resolvable slot BESIDE an unresolvable one must be just as empty.
    val p = SpoonTir.fromSource(
      """package demo;
        |class C {
        |  void go(java.util.Map<String, Integer> m, String k) { m.getOrDefault(k, 0); }
        |}
        |""".stripMargin)
    val getOrDefault = p.symbols.all.filter(s =>
      s.name == "getOrDefault" && s.owner != SymId.None &&
        p.symbols.get(s.owner).exists(_.fullName == "java.util.Map"))
    assertEquals(getOrDefault.size, 1)
    // `getOrDefault(Object key, V defaultValue)` — slot 0 renders, slot 1 is `V`.
    assertEquals(getOrDefault.head.info, NoType)
  }

  // -- what must NOT have moved ----------------------------------------------------------------

  test("a member the PROGRAM declares keeps the signature execDef built for it") {
    val own = program.symbols.all.find(_.fullName == "demo.Uses#own").getOrElse(fail("no demo.Uses#own"))
    assertEquals(sig(own), Some(List("scala.Int") -> "scala.Int"))
  }

  test("`equals(Object)` still renders its parameter as scala's Any — the frontend's own retyping") {
    // `execDef` retypes a 1-argument `equals(Object)`'s parameter to `scala.Any`. The external
    // `Object#equals` this class CALLS is a different symbol and reads java's own `Object`, which
    // is the point: one is a declaration this program emits, the other is a class file.
    assertEquals(sig(external("java.lang.Object", "equals")),
                 Some(List("java.lang.Object") -> "scala.Boolean"))
  }

  test("an external member still carries its OWNER and its interning-key fullName (P4)") {
    val s = external("java.util.regex.Pattern", "matcher")
    assert(s.fullName.startsWith("@"), s"external fullName is the interning key, got ${s.fullName}")
    assert(program.definitionOf(s.id).isEmpty)
  }

  // -- the case this was built for: a REAL classpath, one class file of which is incomplete ------

  test("a PARTIALLY-RESOLVABLE class file leaves its members signature-less; a whole one does not") {
    // The measured shape, and the reason both directions are asserted in ONE fixture: a run where
    // everything is signature-less looks exactly like a run where the feature regressed. Here the
    // classpath holds two compiled classes and one of the two references a third whose class file
    // has been removed — which is the ordinary case for a generated parser compiled with
    // `-implicit:none` against sources that are not on the frontend's classpath.
    //
    // Spoon reconstructs a shadow declaration only for the class it can resolve WHOLE, so the
    // incomplete one yields no declaration at all and every member of it stays at `NoType` — the
    // state every external member was in before this feature existed. Nothing guesses an arity
    // from the reference's own erased formals, which a lenient parse would supply and which would
    // read as a fact.
    val root = Files.createTempDirectory("external-signature")
    val jsrc = root.resolve("jsrc")
    val cls  = root.resolve("classes")
    Files.createDirectories(jsrc.resolve("ext"))
    Files.createDirectories(cls)
    Files.writeString(jsrc.resolve("ext/Gone.java"), "package ext; public class Gone { }")
    Files.writeString(jsrc.resolve("ext/Partial.java"),
      """package ext;
        |public class Partial {
        |  public void needsGone(Gone g) { }
        |  public void plain(String s) { }
        |}""".stripMargin)
    Files.writeString(jsrc.resolve("ext/Whole.java"),
      """package ext;
        |public class Whole {
        |  public void feed(java.util.Set<String> xs) { }
        |}""".stripMargin)
    val javac = javax.tools.ToolProvider.getSystemJavaCompiler
    assertEquals(javac.run(null, null, null, "-d", cls.toString,
      jsrc.resolve("ext/Gone.java").toString, jsrc.resolve("ext/Partial.java").toString,
      jsrc.resolve("ext/Whole.java").toString), 0)
    Files.delete(cls.resolve("ext/Gone.class"))

    val srcRoot = root.resolve("src")
    Files.createDirectories(srcRoot.resolve("demo"))
    Files.writeString(srcRoot.resolve("demo/Caller.java"),
      """package demo;
        |public class Caller {
        |  void go(ext.Partial p, ext.Whole w, java.util.Set<String> s) {
        |    p.plain("x");
        |    w.feed(s);
        |  }
        |}""".stripMargin)
    val p = SpoonTir.fromTypes(
      SpoonTir.buildModel(FrontendConfig(srcRoot, List("demo/Caller.java"), List(cls)), lenient = true))

    def member(owner: String, name: String): Symbol =
      p.symbols.all.toList.filter(s =>
        s.name == name && s.owner != SymId.None &&
          p.symbols.get(s.owner).exists(_.fullName == owner)) match
        case List(one) => one
        case other     => fail(s"expected one $owner#$name, got ${other.map(_.fullName)}")

    // the REFUSAL — and `plain(String)` is the one that makes it a refusal rather than a gap: its
    // own parameter resolves perfectly, and it is still signature-less, because the class it hangs
    // off is the thing that could not be reconstructed.
    assertEquals(member("ext.Partial", "plain").info, NoType)
    assertEquals(member("ext.Partial", "plain").descriptor, scala.None)
    // …and the FEATURE, on the same classpath, in the same run. A class file compiled from source
    // carries its generic signature, so this one is exact all the way into the type argument —
    // which is what makes `java.util.Set[String]` at a third party's formal a seam a phase can now
    // close instead of only count (`ENGINE-LIMITS.md` K15).
    val feed = member("ext.Whole", "feed")
    feed.info match
      case MethodType(List((_, AppliedType(TypeRef(_, h), List(TypeRef(_, a))))), _, _) =>
        assertEquals(p.symbols.get(h).map(_.fullName), Some("java.util.Set"))
        assertEquals(p.symbols.get(a).map(_.fullName), Some("java.lang.String"))
      case other => fail(s"expected java.util.Set[String] at ext.Whole#feed's formal, got $other")
  }
