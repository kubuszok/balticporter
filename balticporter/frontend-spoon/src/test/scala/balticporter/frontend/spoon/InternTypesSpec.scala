package balticporter.frontend.spoon

import balticporter.tir.*

/** Proves `FrontendConfig.internTypes` mints classpath types with `isFinal` and parents in the
  * xref — so `CollectionsTransform.mint` inherits them and `provablyUnrelated` can decide (K18). */
class InternTypesSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Holder { String s; }
      |""".stripMargin

  test("internTypes mints a final JDK class with isFinal and parents") {
    val program = SpoonTir.fromSource(src, internTypes = Set("java.lang.String"))
    val stringSym = program.symbols.all.find(_.fullName == "java.lang.String")
    assert(stringSym.isDefined, "java.lang.String must be in symbols")
    assert(stringSym.get.flags.isFinal, "java.lang.String must be final")
    val defn = program.definitionOf(stringSym.get.id)
    assert(defn.isDefined, "java.lang.String must have a definition from internTypes")
    defn.get match
      case cd: Tree.ClassDef =>
        assert(cd.parents.nonEmpty, "interned String must have parents")
        val parentFqns = cd.parents.flatMap {
          case tt: TypeTree => tt.tpe match
            case TypeRepr.TypeRef(_, sym) => program.symbolOf(sym).map(_.fullName)
            case TypeRepr.AppliedType(TypeRepr.TypeRef(_, sym), _) => program.symbolOf(sym).map(_.fullName)
            case _ => None
          case _ => None
        }
        assert(parentFqns.contains("java.lang.CharSequence"),
          s"String parents must include CharSequence, got: $parentFqns")
      case other => fail(s"expected ClassDef, got ${other.getClass}")
  }

  test("internTypes mints a non-final JDK class without isFinal") {
    val program = SpoonTir.fromSource(src, internTypes = Set("java.util.ArrayList"))
    val alSym = program.symbols.all.find(_.fullName == "java.util.ArrayList")
    assert(alSym.isDefined, "java.util.ArrayList must be in symbols")
    assert(!alSym.get.flags.isFinal, "java.util.ArrayList must NOT be final")
    val defn = program.definitionOf(alSym.get.id)
    assert(defn.isDefined, "interned ArrayList must have a definition")
  }

  test("internTypes empty is a no-op") {
    val without = SpoonTir.fromSource(src, internTypes = Set.empty)
    val withSet = SpoonTir.fromSource(src, internTypes = Set.empty)
    assertEquals(without.symbols.all.size, withSet.symbols.all.size)
  }

  test("internTypes silently skips types not on the classpath") {
    val program = SpoonTir.fromSource(src, internTypes = Set("com.nonexistent.FakeType"))
    val fakeSym = program.symbols.all.find(_.fullName == "com.nonexistent.FakeType")
    assert(fakeSym.isEmpty, "non-existent type must not be interned")
  }

  test("interned type participates in OverrideGraph ancestry") {
    val program = SpoonTir.fromSource(src, internTypes = Set("java.lang.String"))
    val stringSym = program.symbols.all.find(_.fullName == "java.lang.String").get
    val og = OverrideGraph.build(program)
    // internedDefs are traversed by OverrideGraph.build, so String has a node with parents.
    val extAnc = og.externalAncestorsOf(stringSym.id)
    assert(extAnc.contains("java.lang.CharSequence"),
      s"OverrideGraph must report CharSequence as an external ancestor, got: $extAnc")
  }

  test("internedDefs are not in program.units") {
    val program = SpoonTir.fromSource(src, internTypes = Set("java.lang.String"))
    val unitSymbols = program.units.map(_.symbol).toSet
    val stringSym = program.symbols.all.find(_.fullName == "java.lang.String").get
    assert(!unitSymbols.contains(stringSym.id),
      "interned types must not appear in program.units (they are not emitted)")
    assert(program.internedDefs.nonEmpty,
      "internedDefs must be populated")
  }
