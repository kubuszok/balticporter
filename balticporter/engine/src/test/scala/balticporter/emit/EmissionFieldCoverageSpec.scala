package balticporter.emit

import balticporter.tir.*
import balticporter.tir.TypeRepr.*

import scala.jdk.CollectionConverters.*

/** EMISSION FIELD COVERAGE — the second, independent instrument beside the obligation log.
  *
  * WHAT IT CATCHES, and why nothing else can. `JS-S13` and `JS-E05` are the same defect shape: *a
  * TIR node field the frontend populates, every phase carries and the EMITTER never renders*. No
  * obligation catches one — the frontend discharged its obligation correctly and the loss is
  * downstream — and no count moves, because the output compiles perfectly with the field's meaning
  * gone. `ENGINE-LIMITS.md` F5 is that failure written by somebody who lost the bet: `Tree.Try
  * .resources` was populated, carried through every phase and printed by `TirPrinter` in the debug
  * view, *so every diagnostic said the resources were there*, while `TirEmitter.tryStr` computed
  * their text into a local and never interpolated it.
  *
  * THE ENUMERATION IS DERIVED, TWICE OVER, and that is the whole design:
  *
  *   - the NODE KINDS come from the class files (`nodeKinds`/`aggregates`), exactly as
  *     `NodeKindTotalitySpec` reads Spoon's taxonomy out of its jar. A `Tree` case added tomorrow
  *     has no probe and fails here;
  *   - the FIELDS come from `productElementNames` on the fixture itself. A field added to an
  *     EXISTING node — which is precisely what `Tree.Try.resources` was — is in neither the moved
  *     set nor the [[Why]] list, and the default for an unknown field is NOT COVERED.
  *
  * A hand-listed enumeration would have reproduced the exact defect it exists to catch: nothing
  * about adding a field to a case class forces anyone to touch a list.
  *
  * WHAT A PROBE ASSERTS. For each field: perturb it in a minimal fixture and the EMITTED TEXT must
  * change. That is a statement about the emitter and not about the tree — the comparison is
  * text-to-text for the same reason `TriviaCheck`'s is (CLAUDE.md §4.58): counting nodes proves the
  * frontend populated something and proves nothing about what was written.
  *
  * WHERE A FIELD LEGITIMATELY DOES NOT MOVE TEXT it goes on [[Why]], which admits exactly two
  * reasons and no third — [[Why.Indirect]] cites the emitter SYMBOL that reads it, so the entry
  * dies with the code it points at rather than outliving it, and [[Why.Metadata]] states what the
  * field is instead. An entry with neither is a suppression, and this file is where the spec could
  * be defanged in one commit.
  *
  * A FIELD MAY NEED A SECOND HOST. `ClassDef.enumCases` renders only for an enum-flagged symbol,
  * and `ClassDef.parents` renders only for one that is not. So probes are GROUPED by node kind and
  * the coverage question is asked of the group: every field is moved by SOME probe or reasoned
  * about, never both. Per probe, only that a key names a real field.
  */
class EmissionFieldCoverageSpec extends munit.FunSuite:

  // ============================================================================================
  // THE DERIVED INVENTORY
  // ============================================================================================

  /** every class file in `balticporter.tir` — the same technique `NodeKindTotalitySpec` uses on
    * Spoon's jar, and for the same reason: a `Class.forName` sweep over a hand-written list is the
    * shape that cannot see a node nobody thought to name. Works from a classes DIRECTORY (sbt) and
    * from a jar (a published artifact), because those are the two ways this code is ever loaded. */
  private lazy val tirClasses: List[Class[?]] =
    val loc  = classOf[Tree].getProtectionDomain.getCodeSource.getLocation
    val root = java.nio.file.Path.of(loc.toURI)
    val names: List[String] =
      if java.nio.file.Files.isDirectory(root) then
        val dir = root.resolve("balticporter").resolve("tir")
        val s   = java.nio.file.Files.list(dir)
        try s.iterator.asScala.map(_.getFileName.toString).toList
        finally s.close()
      else
        val zf = java.util.zip.ZipFile(root.toFile)
        try
          zf.entries().asScala.map(_.getName)
            .filter(_.startsWith("balticporter/tir/"))
            .map(n => n.substring(n.lastIndexOf('/') + 1))
            .toList
        finally zf.close()
    names
      .filter(_.endsWith(".class"))
      .map(_.stripSuffix(".class"))
      // a synthetic (`Tree$$anon$1`, a lambda) is not a declaration; a module class (`Tree$`) is
      // not a product. Both are excluded by SHAPE and neither is a name anybody maintains.
      .filter(n => !n.contains("$$") && !n.endsWith("$"))
      .flatMap(n =>
        try List(Class.forName(s"balticporter.tir.$n", false, classOf[Tree].getClassLoader))
        catch case _: ClassNotFoundException => Nil)
      .filter(c => classOf[Product].isAssignableFrom(c))
      .filter(c => !java.lang.reflect.Modifier.isAbstract(c.getModifiers) && !c.isInterface)

  /** the case's own name — cut at the LAST separator, whichever it is. `Tree$Ident` is `Ident` and
    * the top-level `balticporter.tir.TypeTree` is `TypeTree`; reading only the `$` leaves the whole
    * package path on every `Tree` that is not nested, which is the one node kind most likely to be
    * forgotten. */
  private def simpleName(c: Class[?]): String =
    val n = c.getName
    n.substring(math.max(n.lastIndexOf('$'), n.lastIndexOf('.')) + 1)

  /** every concrete `Tree` node kind, by simple name. */
  private lazy val nodeKinds: Set[String] =
    tirClasses.filter(c => classOf[Tree].isAssignableFrom(c)).map(simpleName).toSet

  /** the case classes declared inside `object Tree` that are NOT themselves `Tree`s — the
    * aggregates a node carries (`CaseDef`, `CatchCase`, `AnonClass`, `EnumCase`). Their fields are
    * emitted text exactly as a node's are, and perturbing the node's LIST field does not exercise
    * them: dropping a `CaseDef` from `Match.cases` says nothing about whether `CaseDef.guard` is
    * rendered. */
  /** every `TypeRepr` case, by simple name — the fourth obligation surface's emitter-side keys.
    *
    * Derived from the class files for `nodeKinds`' reason, and it needs its OWN scan because
    * [[tirClasses]] drops every name ending in `$`: that filter is right for a `Tree` (all of whose
    * cases are case CLASSES, so a trailing `$` is a module class or a synthetic) and wrong here,
    * where `NoType` and `NoPrefix` are case OBJECTS and are two of the cases `TirEmitter.tpe`
    * dispatches on. Reading the module class and stripping the trailing `$` is what recovers them;
    * a hand-written pair beside the scan would be the list the next case object is not on. */
  private lazy val typeReprKinds: Set[String] =
    val loc  = classOf[Tree].getProtectionDomain.getCodeSource.getLocation
    val root = java.nio.file.Path.of(loc.toURI)
    val names: List[String] =
      if java.nio.file.Files.isDirectory(root) then
        val dir = root.resolve("balticporter").resolve("tir")
        val s   = java.nio.file.Files.list(dir)
        try s.iterator.asScala.map(_.getFileName.toString).toList
        finally s.close()
      else
        val zf = java.util.zip.ZipFile(root.toFile)
        try
          zf.entries().asScala.map(_.getName)
            .filter(_.startsWith("balticporter/tir/"))
            .map(n => n.substring(n.lastIndexOf('/') + 1))
            .toList
        finally zf.close()
    names
      .filter(_.endsWith(".class"))
      .map(_.stripSuffix(".class"))
      .filter(n => n.startsWith("TypeRepr$") && !n.contains("$$"))
      .flatMap(n =>
        try List(Class.forName(s"balticporter.tir.$n", false, classOf[Tree].getClassLoader))
        catch case _: ClassNotFoundException => Nil)
      .filter(c => classOf[TypeRepr].isAssignableFrom(c) && classOf[Product].isAssignableFrom(c))
      .filter(c => !java.lang.reflect.Modifier.isAbstract(c.getModifiers) && !c.isInterface)
      // the trailing `$` comes off BEFORE the cut, not after: `simpleName` slices at the LAST
      // separator, so a module class `TypeRepr$NoType$` cut first yields the empty string — which
      // is a kind nothing attaches to and would have made this assertion silently weaker for
      // exactly the two cases it was added to cover.
      .map(c => c.getName.stripSuffix("$"))
      .map(n => n.substring(math.max(n.lastIndexOf('$'), n.lastIndexOf('.')) + 1))
      .toSet

  private lazy val aggregates: Set[String] =
    tirClasses
      .filter(c => c.getName.startsWith("balticporter.tir.Tree$"))
      .filterNot(c => classOf[Tree].isAssignableFrom(c))
      .map(simpleName).toSet

  // ============================================================================================
  // THE HOST PROGRAM
  // ============================================================================================

  private val O  = Origin("Host.java", 3, 1)
  private val O2 = Origin("Other.java", 41, 7)

  private val HOST  = SymId(1);  private val RUN   = SymId(2);  private val INT   = SymId(3)
  private val UNIT  = SymId(4);  private val STR   = SymId(5);  private val A     = SymId(6)
  private val B     = SymId(7);  private val ARR   = SymId(8);  private val OTHER = SymId(9)
  private val OTHER2= SymId(10); private val M1    = SymId(11); private val M2    = SymId(12)
  private val P1    = SymId(13); private val P2    = SymId(14); private val L1    = SymId(15)
  private val L2    = SymId(16); private val TP    = SymId(17); private val TP2   = SymId(18)
  private val EXC   = SymId(19); private val EXC2  = SymId(20); private val ENUMT = SymId(21)
  private val EC1   = SymId(22); private val EC2   = SymId(23); private val ANON  = SymId(24)
  private val BOOL  = SymId(25); private val OBJ   = SymId(26); private val ARRT  = SymId(27)
  private val ENUM2 = SymId(28); private val TALIAS= SymId(29); private val TALIA2= SymId(30)
  private val NESTED= SymId(31); private val NM    = SymId(32)

  private val tInt  = TypeRef(NoPrefix, INT)
  private val tUnit = TypeRef(NoPrefix, UNIT)
  private val tStr  = TypeRef(NoPrefix, STR)
  private val tBool = TypeRef(NoPrefix, BOOL)
  private val tOth  = TypeRef(NoPrefix, OTHER)
  private val tOth2 = TypeRef(NoPrefix, OTHER2)
  private val tExc  = TypeRef(NoPrefix, EXC)
  private val tExc2 = TypeRef(NoPrefix, EXC2)
  private val tArrI = AppliedType(TypeRef(NoPrefix, ARRT), List(tInt))

  private def tt(t: TypeRepr) = TypeTree(t, O)

  private val symbols = SymbolTable(List(
    Symbol(HOST,  "Host",   "demo.Host",              Flags(), SymId.None, tOth),
    Symbol(RUN,   "run",    "demo.Host#run",          Flags(), HOST, MethodType(Nil, tUnit)),
    Symbol(INT,   "Int",    "scala.Int",              Flags(), SymId.None, NoType),
    Symbol(UNIT,  "Unit",   "scala.Unit",             Flags(), SymId.None, NoType),
    Symbol(STR,   "String", "java.lang.String",       Flags(), SymId.None, NoType),
    Symbol(BOOL,  "Boolean","scala.Boolean",          Flags(), SymId.None, NoType),
    Symbol(OBJ,   "Object", "java.lang.Object",       Flags(), SymId.None, NoType),
    Symbol(ARRT,  "Array",  "scala.Array",            Flags(), SymId.None, NoType),
    Symbol(A,     "a",      "demo.Host#a",            Flags(isMutable = true), HOST, tInt),
    Symbol(B,     "b",      "demo.Host#b",            Flags(isMutable = true), HOST, tInt),
    Symbol(ARR,   "arr",    "demo.Host#arr",          Flags(isMutable = true), HOST, tArrI),
    Symbol(OTHER, "Other",  "demo.Other",             Flags(), SymId.None, tOth),
    Symbol(OTHER2,"Other2", "demo.Other2",            Flags(), SymId.None, tOth2),
    Symbol(M1,    "m1",     "demo.Other#m1",          Flags(), OTHER, MethodType(List(("x", tInt)), tInt)),
    Symbol(M2,    "m2",     "demo.Other#m2",          Flags(), OTHER, MethodType(List(("x", tInt)), tInt)),
    Symbol(P1,    "p1",     "demo.Host#run(p1)",      Flags(isParam = true), RUN, tInt),
    Symbol(P2,    "p2",     "demo.Host#run(p2)",      Flags(isParam = true), RUN, tInt),
    Symbol(L1,    "l1",     "demo.Host#run(l1)",      Flags(isMutable = true), RUN, tInt),
    Symbol(L2,    "l2",     "demo.Host#run(l2)",      Flags(isMutable = true), RUN, tInt),
    Symbol(TP,    "T",      "demo.Host#T",            Flags(isParam = true), HOST, NoType),
    Symbol(TP2,   "U",      "demo.Host#U",            Flags(isParam = true), HOST, NoType),
    Symbol(TALIAS,"Alias",  "demo.Host#Alias",        Flags(), HOST, NoType),
    Symbol(TALIA2,"Alias2", "demo.Host#Alias2",       Flags(), HOST, NoType),
    Symbol(EXC,   "Exception","java.lang.Exception",  Flags(), SymId.None, tExc),
    Symbol(EXC2,  "RuntimeException","java.lang.RuntimeException", Flags(), SymId.None, tExc2),
    Symbol(ENUMT, "Colour", "demo.Colour",            Flags(isEnum = true), SymId.None, TypeRef(NoPrefix, ENUMT)),
    Symbol(ENUM2, "Shade",  "demo.Shade",             Flags(isEnum = true), SymId.None, TypeRef(NoPrefix, ENUM2)),
    Symbol(EC1,   "RED",    "demo.Colour#RED",        Flags(isStatic = true), ENUMT, TypeRef(NoPrefix, ENUMT)),
    Symbol(EC2,   "BLUE",   "demo.Colour#BLUE",       Flags(isStatic = true), ENUMT, TypeRef(NoPrefix, ENUMT)),
    Symbol(ANON,  "$anon",  "demo.Host$$anon",        Flags(), HOST, tOth),
    Symbol(NESTED,"Inner",  "demo.Host$Inner",        Flags(), HOST, TypeRef(NoPrefix, NESTED)),
    Symbol(NM,    "read",   "demo.Host$Inner#read",   Flags(), NESTED, MethodType(Nil, tInt)),
  ))

  private def emitOf(units: List[Tree.ClassDef]): String =
    new TirEmitter(new Program(units, symbols, Xref.build(units), MemberIndex.empty)).emit

  /** the node IS the compilation unit. */
  private def hostTop(cd: Tree.ClassDef): String = emitOf(List(cd))

  /** the node is a MEMBER of an ordinary class. */
  private def hostMember(s: Statement): String =
    emitOf(List(Tree.ClassDef(HOST, Nil, None, List(s), O)))

  /** the node is the BODY of a method — every `Term` probe's host. */
  private def hostTerm(t: Term): String =
    hostMember(Tree.DefDef(RUN, List(Nil), tt(tUnit), Some(t), O))

  // shorthand terms
  private def iLit(n: Int)    = Tree.Literal(Constant.IntC(n), tInt, O)
  private def sLit(s: String) = Tree.Literal(Constant.StringC(s), tStr, O)
  private val refA            = Tree.Select(Tree.This(HOST, tOth, O), A, tInt, O)
  private val refB            = Tree.Select(Tree.This(HOST, tOth, O), B, tInt, O)
  private val refArr          = Tree.Select(Tree.This(HOST, tOth, O), ARR, tArrI, O)
  private val unitLit         = Tree.Literal(Constant.UnitC, tUnit, O)
  private def triv(s: String) = Trivia(TriviaKind.Line, s"// $s")

  // ============================================================================================
  // THE PROBE
  // ============================================================================================

  /** why a field legitimately does not move the emitted text. TWO reasons and no third — a bare
    * string would make this list a suppression list, which is what the audit point asks about. */
  private enum Why:
    /** not RENDERED, but a named emitter symbol READS it to select a rendering. The entry cites
      * that symbol, so it dies with the code it points at. */
    case Indirect(reads: String)
    /** metadata by construction — the emitter is structurally incapable of printing it. */
    case Metadata(what: String)

  private final class Probe[T <: Product](
      node: T,
      host: T => String,
      moved: Seq[(String, T)],
      val notEmitted: Map[String, Why],
  ):
    val owner: String                = node.productPrefix
    val fields: List[String]         = node.productElementNames.toList
    lazy val base: String            = host(node)
    lazy val movedText: Map[String, String] = moved.iterator.map((k, v) => k -> host(v)).toMap
    val movedKeys: Set[String]       = moved.map(_._1).toSet

  private def probe[T <: Product](node: T, host: T => String)(
      moved: (String, T)*)(notEmitted: (String, Why)*): Probe[T] =
    new Probe(node, host, moved, notEmitted.toMap)

  import Why.*

  // -- shared reasons, written once so the same fact is not phrased three ways ------------------
  private val originIsMetadata =
    Metadata("provenance, never rendered — it reaches `srcmap.tsv` and a finding's location, and " +
      "an emitted file that moved because a Java line moved would be a diff nobody reads")
  private val tpeIsMetadata =
    Metadata("the term's TYPE, carried for phases to read; the emitter renders the term's SHAPE " +
      "and lets scalac re-infer, which is `CLAUDE.md` §6's fully-qualified-no-imports rule seen " +
      "from the other side")

  // ============================================================================================
  // THE PROBES — one group per node kind
  // ============================================================================================

  private val probes: List[Probe[?]] = List(

    // ---- TypeTree ---------------------------------------------------------------------------
    probe(tt(tInt), (x: TypeTree) => hostMember(Tree.DefDef(RUN, List(Nil), x, Some(unitLit), O)))(
      "tpe" -> tt(tStr),
    )("origin" -> originIsMetadata),

    // ---- ClassDef, the ordinary shape --------------------------------------------------------
    probe(
      Tree.ClassDef(HOST, List(tt(tOth)), Some(tt(tOth2)), List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O,
        tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)),
        enumCases = Nil, leading = List(triv("the class")), unitLeading = List(triv("the file"))),
      hostTop)(
      "symbol"      -> Tree.ClassDef(OTHER2, List(tt(tOth)), Some(tt(tOth2)), List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O,
                         tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)),
                         leading = List(triv("the class")), unitLeading = List(triv("the file"))),
      "parents"     -> Tree.ClassDef(HOST, Nil, Some(tt(tOth2)), List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O,
                         tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)),
                         leading = List(triv("the class")), unitLeading = List(triv("the file"))),
      "selfType"    -> Tree.ClassDef(HOST, List(tt(tOth)), None, List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O,
                         tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)),
                         leading = List(triv("the class")), unitLeading = List(triv("the file"))),
      "body"        -> Tree.ClassDef(HOST, List(tt(tOth)), Some(tt(tOth2)), Nil, O,
                         tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)),
                         leading = List(triv("the class")), unitLeading = List(triv("the file"))),
      "tparams"     -> Tree.ClassDef(HOST, List(tt(tOth)), Some(tt(tOth2)), List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O,
                         tparams = Nil,
                         leading = List(triv("the class")), unitLeading = List(triv("the file"))),
      "leading"     -> Tree.ClassDef(HOST, List(tt(tOth)), Some(tt(tOth2)), List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O,
                         tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)),
                         leading = Nil, unitLeading = List(triv("the file"))),
      "unitLeading" -> Tree.ClassDef(HOST, List(tt(tOth)), Some(tt(tOth2)), List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O,
                         tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)),
                         leading = List(triv("the class")), unitLeading = Nil),
    )("origin" -> originIsMetadata),

    // ---- ClassDef, the ENUM shape — the only host in which `enumCases` renders -----------------
    probe(
      Tree.ClassDef(ENUMT, Nil, None, Nil, O,
        enumCases = List(Tree.EnumCase(EC1, Nil, Nil, O), Tree.EnumCase(EC2, Nil, Nil, O))),
      hostTop)(
      "enumCases" -> Tree.ClassDef(ENUMT, Nil, None, Nil, O, enumCases = List(Tree.EnumCase(EC1, Nil, Nil, O))),
    )(),

    // ---- TypeDef ------------------------------------------------------------------------------
    probe(Tree.TypeDef(TALIAS, tt(tInt), O), (x: Tree.TypeDef) => hostMember(x))(
      "symbol" -> Tree.TypeDef(TALIA2, tt(tInt), O),
      "rhs"    -> Tree.TypeDef(TALIAS, tt(tStr), O),
    )("origin" -> originIsMetadata),

    // ---- DefDef -------------------------------------------------------------------------------
    probe(
      Tree.DefDef(RUN, List(List(Tree.ValDef(P1, tt(tInt), None, O))), tt(tUnit), Some(unitLit), O,
        tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)), leading = List(triv("doc"))),
      (x: Tree.DefDef) => hostMember(x))(
      "symbol"    -> Tree.DefDef(M2, List(List(Tree.ValDef(P1, tt(tInt), None, O))), tt(tUnit), Some(unitLit), O,
                       tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)), leading = List(triv("doc"))),
      "paramss"   -> Tree.DefDef(RUN, List(Nil), tt(tUnit), Some(unitLit), O,
                       tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)), leading = List(triv("doc"))),
      "returnTpt" -> Tree.DefDef(RUN, List(List(Tree.ValDef(P1, tt(tInt), None, O))), tt(tStr), Some(sLit("s")), O,
                       tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)), leading = List(triv("doc"))),
      "rhs"       -> Tree.DefDef(RUN, List(List(Tree.ValDef(P1, tt(tInt), None, O))), tt(tUnit), None, O,
                       tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)), leading = List(triv("doc"))),
      "tparams"   -> Tree.DefDef(RUN, List(List(Tree.ValDef(P1, tt(tInt), None, O))), tt(tUnit), Some(unitLit), O,
                       tparams = Nil, leading = List(triv("doc"))),
      "leading"   -> Tree.DefDef(RUN, List(List(Tree.ValDef(P1, tt(tInt), None, O))), tt(tUnit), Some(unitLit), O,
                       tparams = List(Tree.TypeDef(TP, tt(TypeBounds(NoType, NoType)), O)), leading = Nil),
    )("origin" -> originIsMetadata),

    // ---- ValDef -------------------------------------------------------------------------------
    probe(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O, leading = List(triv("doc"))),
      (x: Tree.ValDef) => hostMember(x))(
      "symbol"  -> Tree.ValDef(B, tt(tInt), Some(iLit(1)), O, leading = List(triv("doc"))),
      "tpt"     -> Tree.ValDef(A, tt(tStr), Some(sLit("s")), O, leading = List(triv("doc"))),
      "rhs"     -> Tree.ValDef(A, tt(tInt), Some(iLit(2)), O, leading = List(triv("doc"))),
      "leading" -> Tree.ValDef(A, tt(tInt), Some(iLit(1)), O, leading = Nil),
    )("origin" -> originIsMetadata),

    // ---- Ident --------------------------------------------------------------------------------
    probe(Tree.Ident(OTHER, tOth, O), (x: Tree.Ident) => hostTerm(Tree.Select(x, M1, tInt, O)))(
      "sym" -> Tree.Ident(OTHER2, tOth2, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Select -------------------------------------------------------------------------------
    probe(Tree.Select(Tree.This(HOST, tOth, O), A, tInt, O), hostTerm)(
      "qual" -> Tree.Select(Tree.Ident(OTHER, tOth, O), A, tInt, O),
      "sym"  -> Tree.Select(Tree.This(HOST, tOth, O), B, tInt, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Literal ------------------------------------------------------------------------------
    probe(Tree.Literal(Constant.IntC(1), tInt, O), hostTerm)(
      "const" -> Tree.Literal(Constant.IntC(2), tInt, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- This ---------------------------------------------------------------------------------
    //
    // `thisRef` renders the bare keyword for the INNERMOST class, so a top-level host cannot tell
    // one `cls` from another: the difference java has (`Outer.this` vs `this`) only exists inside a
    // NESTED class, which is therefore what this probe has to build. A probe that cannot distinguish
    // the field would have had to excuse it, and the excuse would have been false.
    probe(Tree.This(HOST, tOth, O),
      (x: Tree.This) => hostTop(Tree.ClassDef(HOST, Nil, None, List(
        Tree.ValDef(A, tt(tInt), Some(iLit(1)), O),
        Tree.ClassDef(NESTED, Nil, None, List(
          Tree.DefDef(NM, List(Nil), tt(tInt), Some(Tree.Select(x, A, tInt, O)), O)), O)), O)))(
      "cls" -> Tree.This(NESTED, TypeRef(NoPrefix, NESTED), O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Super --------------------------------------------------------------------------------
    probe(Tree.Super(HOST, tOth, O), (x: Tree.Super) => hostTerm(Tree.Select(x, A, tInt, O)))(
    )("cls" -> Indirect("TirEmitter's `Tree.Super` arm renders the keyword `super`; the class is " +
        "the receiver's own and Scala names it by position, never by symbol"),
      "tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- New / AnonClass ----------------------------------------------------------------------
    probe(Tree.New(tt(tOth), tOth, O, anon = Some(Tree.AnonClass(ANON, List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O))),
      hostTerm)(
      "tpt"  -> Tree.New(tt(tOth2), tOth, O, anon = Some(Tree.AnonClass(ANON, List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O))),
      "anon" -> Tree.New(tt(tOth), tOth, O, anon = None),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Apply --------------------------------------------------------------------------------
    probe(Tree.Apply(Tree.Select(Tree.Ident(OTHER, tOth, O), M1, tInt, O), List(iLit(1)), M1, tInt, O), hostTerm)(
      "fun"    -> Tree.Apply(Tree.Select(Tree.Ident(OTHER, tOth, O), M2, tInt, O), List(iLit(1)), M1, tInt, O),
      "args"   -> Tree.Apply(Tree.Select(Tree.Ident(OTHER, tOth, O), M1, tInt, O), List(iLit(9)), M1, tInt, O),
    )("method" -> Indirect("the CALLEE's symbol, read by `TirEmitter.applyStr` (and by the vararg " +
        "spread and static-forwarder decisions) to choose a rendering; the NAME on the page comes " +
        "from `fun`"),
      "tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- TypeApply ----------------------------------------------------------------------------
    probe(Tree.TypeApply(Tree.Select(Tree.Ident(OTHER, tOth, O), M1, tInt, O), List(tt(tInt)), tInt, O), hostTerm)(
      "fun"   -> Tree.TypeApply(Tree.Select(Tree.Ident(OTHER, tOth, O), M2, tInt, O), List(tt(tInt)), tInt, O),
      "targs" -> Tree.TypeApply(Tree.Select(Tree.Ident(OTHER, tOth, O), M1, tInt, O), List(tt(tStr)), tInt, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Assign -------------------------------------------------------------------------------
    probe(Tree.Assign(refA, iLit(1), tUnit, O), hostTerm)(
      "lhs" -> Tree.Assign(refB, iLit(1), tUnit, O),
      "rhs" -> Tree.Assign(refA, iLit(2), tUnit, O),
      "compound" -> Tree.Assign(refA, iLit(1), tUnit, O, compound = Some(("+", None))),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Block --------------------------------------------------------------------------------
    probe(Tree.Block(List(Tree.Assign(refA, iLit(1), tUnit, O)), iLit(7), tInt, O, trailing = List(triv("tail"))),
      hostTerm)(
      "stats"    -> Tree.Block(List(Tree.Assign(refB, iLit(1), tUnit, O)), iLit(7), tInt, O, trailing = List(triv("tail"))),
      "expr"     -> Tree.Block(List(Tree.Assign(refA, iLit(1), tUnit, O)), iLit(8), tInt, O, trailing = List(triv("tail"))),
      "trailing" -> Tree.Block(List(Tree.Assign(refA, iLit(1), tUnit, O)), iLit(7), tInt, O, trailing = Nil),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Lambda -------------------------------------------------------------------------------
    //
    // The base body is a value-returning `return`, which is the ONE shape `resultTpt` is visible
    // in: a java lambda body is a method body, so the emitter interposes a nested `def` (`JS-S21`)
    // and that `def`'s result type is the SAM METHOD's. With no `return` the field is invisible by
    // construction, and a probe built on `iLit(1)` would have declared it metadata — which is
    // exactly the mistake `ENGINE-LIMITS.md` I9 was: the emitter could not read a type nothing
    // carried, and nothing said so.
    probe(Tree.Lambda(List(Tree.ValDef(P1, tt(tInt), None, O)), Tree.Return(Some(iLit(1)), tInt, O),
                      tOth, O, resultTpt = Some(tt(tInt))), hostTerm)(
      "params"    -> Tree.Lambda(List(Tree.ValDef(P2, tt(tInt), None, O)), Tree.Return(Some(iLit(1)), tInt, O),
                                 tOth, O, resultTpt = Some(tt(tInt))),
      "body"      -> Tree.Lambda(List(Tree.ValDef(P1, tt(tInt), None, O)), Tree.Return(Some(iLit(2)), tInt, O),
                                 tOth, O, resultTpt = Some(tt(tInt))),
      "resultTpt" -> Tree.Lambda(List(Tree.ValDef(P1, tt(tInt), None, O)), Tree.Return(Some(iLit(1)), tInt, O),
                                 tOth, O, resultTpt = Some(tt(tStr))),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- If -----------------------------------------------------------------------------------
    probe(Tree.If(Tree.Literal(Constant.BoolC(true), tBool, O), iLit(1), iLit(2), tInt, O), hostTerm)(
      "cond"  -> Tree.If(Tree.Literal(Constant.BoolC(false), tBool, O), iLit(1), iLit(2), tInt, O),
      "thenp" -> Tree.If(Tree.Literal(Constant.BoolC(true), tBool, O), iLit(3), iLit(2), tInt, O),
      "elsep" -> Tree.If(Tree.Literal(Constant.BoolC(true), tBool, O), iLit(1), iLit(4), tInt, O),
    )(
      // JS-E05's other half, and it is NOT the failure the proposal predicted: the conditional's
      // JLS-computed type is applied by the FRONTEND, to each OPERAND (`SpoonTir.promotedBranch`),
      // because a conversion goes where java performed it and an emitter-side ascription is a CAST
      // — which is the whole of K17. After that the `if` really HAS java's type and the emitter has
      // nothing to ascribe.
      "tpe" -> Metadata("the branches carry the type: `SpoonTir.promotedBranch` converts each " +
        "OPERAND to java's computed type, so this field is what that pass decided and never a " +
        "rendering instruction"),
      "origin" -> originIsMetadata),

    // ---- Typed --------------------------------------------------------------------------------
    probe(Tree.Typed(iLit(1), tt(tInt), tInt, O), hostTerm)(
      "expr" -> Tree.Typed(iLit(2), tt(tInt), tInt, O),
      "tpt"  -> Tree.Typed(iLit(1), tt(tStr), tInt, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Repeated -----------------------------------------------------------------------------
    probe(Tree.Repeated(List(iLit(1), iLit(2)), tArrI, O),
      (x: Tree.Repeated) => hostTerm(Tree.Apply(Tree.Select(Tree.Ident(OTHER, tOth, O), M1, tInt, O), List(x), M1, tInt, O)))(
      "elems" -> Tree.Repeated(List(iLit(1)), tArrI, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Spread -------------------------------------------------------------------------------
    probe(Tree.Spread(refArr, tArrI, O),
      (x: Tree.Spread) => hostTerm(Tree.Apply(Tree.Select(Tree.Ident(OTHER, tOth, O), M1, tInt, O), List(x), M1, tInt, O)))(
      "expr" -> Tree.Spread(Tree.Select(Tree.This(HOST, tOth, O), A, tArrI, O), tArrI, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Return -------------------------------------------------------------------------------
    probe(Tree.Return(Some(iLit(1)), tInt, O), hostTerm)(
      "expr" -> Tree.Return(None, tInt, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- While --------------------------------------------------------------------------------
    probe(Tree.While(Tree.Literal(Constant.BoolC(true), tBool, O), Tree.Assign(refA, iLit(1), tUnit, O), tUnit, O,
      label = Some("outer")),
      (x: Tree.While) => hostTerm(Tree.Block(List(x), unitLit, tUnit, O)))(
      "cond"  -> Tree.While(Tree.Literal(Constant.BoolC(false), tBool, O), Tree.Assign(refA, iLit(1), tUnit, O), tUnit, O, label = Some("outer")),
      "body"  -> Tree.While(Tree.Literal(Constant.BoolC(true), tBool, O), Tree.Assign(refB, iLit(1), tUnit, O), tUnit, O, label = Some("outer")),
      "label" -> Tree.While(Tree.Literal(Constant.BoolC(true), tBool, O), Tree.Block(List(Tree.Break(Some("outer"), tUnit, O)), unitLit, tUnit, O), tUnit, O, label = Some("inner")),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Throw --------------------------------------------------------------------------------
    probe(Tree.Throw(Tree.New(tt(tExc), tExc, O), tUnit, O), hostTerm)(
      "expr" -> Tree.Throw(Tree.New(tt(tExc2), tExc2, O), tUnit, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- InstanceOf ---------------------------------------------------------------------------
    probe(Tree.InstanceOf(refA, tt(tOth), tBool, O), hostTerm)(
      "expr" -> Tree.InstanceOf(refB, tt(tOth), tBool, O),
      "tpt"  -> Tree.InstanceOf(refA, tt(tOth2), tBool, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- ArrayAccess --------------------------------------------------------------------------
    probe(Tree.ArrayAccess(refArr, iLit(1), tInt, O), hostTerm)(
      "array" -> Tree.ArrayAccess(Tree.Select(Tree.This(HOST, tOth, O), A, tArrI, O), iLit(1), tInt, O),
      "index" -> Tree.ArrayAccess(refArr, iLit(2), tInt, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- ArrayLength --------------------------------------------------------------------------
    probe(Tree.ArrayLength(refArr, tInt, O), hostTerm)(
      "array" -> Tree.ArrayLength(Tree.Select(Tree.This(HOST, tOth, O), A, tArrI, O), tInt, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- NewArray -----------------------------------------------------------------------------
    probe(Tree.NewArray(tt(tInt), List(iLit(3)), None, tArrI, O), hostTerm)(
      "elem" -> Tree.NewArray(tt(tStr), List(iLit(3)), None, tArrI, O),
      "dims" -> Tree.NewArray(tt(tInt), List(iLit(4)), None, tArrI, O),
      "init" -> Tree.NewArray(tt(tInt), Nil, Some(List(iLit(1), iLit(2))), tArrI, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- ForEach ------------------------------------------------------------------------------
    probe(Tree.ForEach(Tree.ValDef(L1, tt(tInt), None, O), refArr, Tree.Assign(refA, iLit(1), tUnit, O), tUnit, O,
      label = Some("outer")),
      (x: Tree.ForEach) => hostTerm(Tree.Block(List(x), unitLit, tUnit, O)))(
      "binding"  -> Tree.ForEach(Tree.ValDef(L2, tt(tInt), None, O), refArr, Tree.Assign(refA, iLit(1), tUnit, O), tUnit, O, label = Some("outer")),
      "iterable" -> Tree.ForEach(Tree.ValDef(L1, tt(tInt), None, O), Tree.Select(Tree.This(HOST, tOth, O), A, tArrI, O), Tree.Assign(refA, iLit(1), tUnit, O), tUnit, O, label = Some("outer")),
      "body"     -> Tree.ForEach(Tree.ValDef(L1, tt(tInt), None, O), refArr, Tree.Assign(refB, iLit(1), tUnit, O), tUnit, O, label = Some("outer")),
      "label"    -> Tree.ForEach(Tree.ValDef(L1, tt(tInt), None, O), refArr, Tree.Block(List(Tree.Break(Some("outer"), tUnit, O)), unitLit, tUnit, O), tUnit, O, label = Some("inner")),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- For ----------------------------------------------------------------------------------
    probe(Tree.For(List(Tree.ValDef(L1, tt(tInt), Some(iLit(0)), O)),
      Some(Tree.Literal(Constant.BoolC(true), tBool, O)),
      List(Tree.Assign(refA, iLit(1), tUnit, O)),
      Tree.Assign(refB, iLit(1), tUnit, O), tUnit, O, label = Some("outer")),
      (x: Tree.For) => hostTerm(Tree.Block(List(x), unitLit, tUnit, O)))(
      "init"   -> Tree.For(List(Tree.ValDef(L2, tt(tInt), Some(iLit(0)), O)), Some(Tree.Literal(Constant.BoolC(true), tBool, O)),
                    List(Tree.Assign(refA, iLit(1), tUnit, O)), Tree.Assign(refB, iLit(1), tUnit, O), tUnit, O, label = Some("outer")),
      // NOT `None`: an ABSENT condition means `true` (JLS 14.14.1), so `None` and `Some(true)`
      // render the same loop — correctly, and a probe reading that as "the field is not emitted"
      // would have excused a field the emitter does render.
      "cond"   -> Tree.For(List(Tree.ValDef(L1, tt(tInt), Some(iLit(0)), O)), Some(Tree.Literal(Constant.BoolC(false), tBool, O)),
                    List(Tree.Assign(refA, iLit(1), tUnit, O)), Tree.Assign(refB, iLit(1), tUnit, O), tUnit, O, label = Some("outer")),
      "update" -> Tree.For(List(Tree.ValDef(L1, tt(tInt), Some(iLit(0)), O)), Some(Tree.Literal(Constant.BoolC(true), tBool, O)),
                    Nil, Tree.Assign(refB, iLit(1), tUnit, O), tUnit, O, label = Some("outer")),
      "body"   -> Tree.For(List(Tree.ValDef(L1, tt(tInt), Some(iLit(0)), O)), Some(Tree.Literal(Constant.BoolC(true), tBool, O)),
                    List(Tree.Assign(refA, iLit(1), tUnit, O)), Tree.Assign(refA, iLit(9), tUnit, O), tUnit, O, label = Some("outer")),
      "label"  -> Tree.For(List(Tree.ValDef(L1, tt(tInt), Some(iLit(0)), O)), Some(Tree.Literal(Constant.BoolC(true), tBool, O)),
                    List(Tree.Assign(refA, iLit(1), tUnit, O)),
                    Tree.Block(List(Tree.Break(Some("outer"), tUnit, O)), unitLit, tUnit, O), tUnit, O, label = Some("inner")),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Try / CatchCase ----------------------------------------------------------------------
    probe(Tree.Try(List(Tree.ValDef(L1, tt(tOth), Some(Tree.New(tt(tOth), tOth, O)), O)),
      Tree.Assign(refA, iLit(1), tUnit, O),
      List(Tree.CatchCase(Tree.ValDef(L2, tt(tExc), None, O), Tree.Assign(refB, iLit(1), tUnit, O))),
      Some(Tree.Assign(refA, iLit(2), tUnit, O)), tUnit, O),
      (x: Tree.Try) => hostTerm(Tree.Block(List(x), unitLit, tUnit, O)))(
      "resources" -> Tree.Try(Nil, Tree.Assign(refA, iLit(1), tUnit, O),
                       List(Tree.CatchCase(Tree.ValDef(L2, tt(tExc), None, O), Tree.Assign(refB, iLit(1), tUnit, O))),
                       Some(Tree.Assign(refA, iLit(2), tUnit, O)), tUnit, O),
      "body"      -> Tree.Try(List(Tree.ValDef(L1, tt(tOth), Some(Tree.New(tt(tOth), tOth, O)), O)),
                       Tree.Assign(refA, iLit(9), tUnit, O),
                       List(Tree.CatchCase(Tree.ValDef(L2, tt(tExc), None, O), Tree.Assign(refB, iLit(1), tUnit, O))),
                       Some(Tree.Assign(refA, iLit(2), tUnit, O)), tUnit, O),
      "catches"   -> Tree.Try(List(Tree.ValDef(L1, tt(tOth), Some(Tree.New(tt(tOth), tOth, O)), O)),
                       Tree.Assign(refA, iLit(1), tUnit, O), Nil,
                       Some(Tree.Assign(refA, iLit(2), tUnit, O)), tUnit, O),
      "finalizer" -> Tree.Try(List(Tree.ValDef(L1, tt(tOth), Some(Tree.New(tt(tOth), tOth, O)), O)),
                       Tree.Assign(refA, iLit(1), tUnit, O),
                       List(Tree.CatchCase(Tree.ValDef(L2, tt(tExc), None, O), Tree.Assign(refB, iLit(1), tUnit, O))),
                       None, tUnit, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata,
      "id" -> Metadata("the identity token `Tree.Try.id` — excluded from `equals`/`hashCode`, " +
        "reaching no emitted text, no artifact and no printer; it exists so `TryResourceCheck` and " +
        "the emitter can name the SAME `try` across a traversal that rebuilds every node")),

    probe(Tree.CatchCase(Tree.ValDef(L2, tt(tExc), None, O), Tree.Assign(refB, iLit(1), tUnit, O)),
      (x: Tree.CatchCase) => hostTerm(Tree.Block(List(
        Tree.Try(Nil, Tree.Assign(refA, iLit(1), tUnit, O), List(x), None, tUnit, O)), unitLit, tUnit, O)))(
      "param" -> Tree.CatchCase(Tree.ValDef(L1, tt(tExc2), None, O), Tree.Assign(refB, iLit(1), tUnit, O)),
      "body"  -> Tree.CatchCase(Tree.ValDef(L2, tt(tExc), None, O), Tree.Assign(refB, iLit(9), tUnit, O)),
    )(),

    // ---- Match / CaseDef ----------------------------------------------------------------------
    probe(Tree.Match(refA, List(
      Tree.CaseDef(List(iLit(1)), None, Tree.Assign(refB, iLit(1), tUnit, O), isDefault = false),
      Tree.CaseDef(Nil, None, Tree.Assign(refB, iLit(2), tUnit, O), isDefault = true)), tUnit, O),
      (x: Tree.Match) => hostTerm(Tree.Block(List(x), unitLit, tUnit, O)))(
      "scrutinee" -> Tree.Match(refB, List(
        Tree.CaseDef(List(iLit(1)), None, Tree.Assign(refB, iLit(1), tUnit, O), isDefault = false),
        Tree.CaseDef(Nil, None, Tree.Assign(refB, iLit(2), tUnit, O), isDefault = true)), tUnit, O),
      "cases"     -> Tree.Match(refA, List(
        Tree.CaseDef(Nil, None, Tree.Assign(refB, iLit(2), tUnit, O), isDefault = true)), tUnit, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata,
      "id" -> Metadata("the identity token `Tree.Match.id` — the same contract as `Tree.Try.id`, so " +
        "`SwitchNullCheck` can ask the emitter whether it guarded THIS switch across a traversal " +
        "that rebuilds every node")),

    // …and a SECOND host for `isExpr`, because the field moves text only where the arm holds a
    // non-tail `yield`: java's switch EXPRESSION opens a value-carrying `boundary` for one and its
    // switch STATEMENT does not (JLS 14.21 re-binds a `yield` at an expression and nowhere else),
    // so the arms above — which hold no yield at all — render identically either way. The
    // group-coverage rule this file states is what makes a second probe the right shape.
    probe(Tree.Match(refA, List(Tree.CaseDef(Nil, None,
        Tree.Block(List(Tree.Yield(iLit(1), tInt, O)), iLit(0), tInt, O), isDefault = true)),
        tInt, O, isExpr = true),
      (x: Tree.Match) => hostTerm(Tree.Block(List(x), unitLit, tUnit, O)))(
      "isExpr" -> Tree.Match(refA, List(Tree.CaseDef(Nil, None,
        Tree.Block(List(Tree.Yield(iLit(1), tInt, O)), iLit(0), tInt, O), isDefault = true)),
        tInt, O, isExpr = false),
    )(),

    probe(Tree.CaseDef(List(iLit(1)), Some(Tree.Literal(Constant.BoolC(true), tBool, O)),
      Tree.Assign(refB, iLit(1), tUnit, O), isDefault = false),
      (x: Tree.CaseDef) => hostTerm(Tree.Block(List(
        Tree.Match(refA, List(x, Tree.CaseDef(Nil, None, unitLit, isDefault = true)), tUnit, O)), unitLit, tUnit, O)))(
      "labels"    -> Tree.CaseDef(List(iLit(2)), Some(Tree.Literal(Constant.BoolC(true), tBool, O)),
                       Tree.Assign(refB, iLit(1), tUnit, O), isDefault = false),
      "guard"     -> Tree.CaseDef(List(iLit(1)), None, Tree.Assign(refB, iLit(1), tUnit, O), isDefault = false),
      "body"      -> Tree.CaseDef(List(iLit(1)), Some(Tree.Literal(Constant.BoolC(true), tBool, O)),
                       Tree.Assign(refB, iLit(9), tUnit, O), isDefault = false),
      "isDefault" -> Tree.CaseDef(List(iLit(1)), Some(Tree.Literal(Constant.BoolC(true), tBool, O)),
                       Tree.Assign(refB, iLit(1), tUnit, O), isDefault = true),
    )(),

    // ---- Yield --------------------------------------------------------------------------------
    // A NON-TAIL `yield` (JLS 14.21), which is the only shape that reaches the IR — the frontend
    // peels a tail one into the arm's value. It renders only inside a switch-EXPRESSION arm, which
    // is what makes this host a `Match` with the node standing as a STATEMENT of the arm's block:
    // put in the block's result position it would be a tail yield, and `Jumps.yieldsOut` would
    // correctly report that no boundary is needed and nothing would render.
    probe(Tree.Yield(iLit(1), tInt, O),
      (x: Tree.Yield) => hostTerm(Tree.Block(List(
        Tree.Match(refA, List(Tree.CaseDef(Nil, None,
          Tree.Block(List(x), iLit(0), tInt, O), isDefault = true)), tInt, O, isExpr = true)), unitLit, tUnit, O)))(
      "value" -> Tree.Yield(iLit(2), tInt, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- TypePattern --------------------------------------------------------------------------
    // A java TYPE PATTERN as a case LABEL (JLS 14.11.1) — the only position it is valid in, which is
    // why the host is a `Match` arm's label list and not the arm's body.
    probe(Tree.TypePattern(L1, tt(tStr), tStr, O),
      (x: Tree.TypePattern) => hostTerm(Tree.Block(List(
        Tree.Match(refA, List(
          Tree.CaseDef(List(x), None, Tree.Assign(refB, iLit(1), tUnit, O), isDefault = false),
          Tree.CaseDef(Nil, None, unitLit, isDefault = true)), tUnit, O)), unitLit, tUnit, O)))(
      "bind" -> Tree.TypePattern(L2, tt(tStr), tStr, O),
      "tpt"  -> Tree.TypePattern(L1, tt(tOth), tStr, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- RecordPattern ------------------------------------------------------------------------
    // A java RECORD PATTERN as a case LABEL (JLS 14.30.1), lowered to scala's constructor pattern
    // over the extractor `JS-C43` derives. Same host as `TypePattern` for the same reason — it is
    // valid in a label position and nowhere else — and `tpt` is what NAMES the extractor, so
    // perturbing it moves the emitted text even though nothing else about the pattern changes.
    probe(Tree.RecordPattern(tt(tStr), List(Tree.BindPattern(L1, tStr, O)), tStr, O),
      (x: Tree.RecordPattern) => hostTerm(Tree.Block(List(
        Tree.Match(refA, List(
          Tree.CaseDef(List(x), None, Tree.Assign(refB, iLit(1), tUnit, O), isDefault = false),
          Tree.CaseDef(Nil, None, unitLit, isDefault = true)), tUnit, O)), unitLit, tUnit, O)))(
      "tpt"      -> Tree.RecordPattern(tt(tOth), List(Tree.BindPattern(L1, tStr, O)), tStr, O),
      "patterns" -> Tree.RecordPattern(tt(tStr), List(Tree.BindPattern(L2, tStr, O)), tStr, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- BindPattern --------------------------------------------------------------------------
    // An UNCONDITIONAL component of a record pattern (JLS 14.30.2) — the binding alone, which is
    // what makes it a different node from `TypePattern` rather than one with a trivial test. Hosted
    // INSIDE a `RecordPattern`, which is the only position it is valid in.
    probe(Tree.BindPattern(L1, tStr, O),
      (x: Tree.BindPattern) => hostTerm(Tree.Block(List(
        Tree.Match(refA, List(
          Tree.CaseDef(List(Tree.RecordPattern(tt(tStr), List(x), tStr, O)), None,
            Tree.Assign(refB, iLit(1), tUnit, O), isDefault = false),
          Tree.CaseDef(Nil, None, unitLit, isDefault = true)), tUnit, O)), unitLit, tUnit, O)))(
      "bind" -> Tree.BindPattern(L2, tStr, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- MethodRef ----------------------------------------------------------------------------
    // `referent` is the JLS 15.13.1 split at `Type::name` and it is a FIELD precisely because it
    // decides the emitted shape by itself: `Static(n)` is a qualified NAME for every `n` but ZERO,
    // where scala refuses to eta-expand a nullary method and the form becomes a lambda too
    // (`ENGINE-LIMITS.md` G32); `Instance(n)` is an (n+1)-parameter lambda. Perturbing the ARITY
    // alone moves the text in both cases, which is the half a symbol with no `MethodType` cannot
    // supply (`Tree.MethodRef.referent`).
    probe(Tree.MethodRef(Left(tt(tOth)), M1, tOth, O, Referent.Instance(0)), hostTerm)(
      "qualifier" -> Tree.MethodRef(Right(Tree.Ident(OTHER, tOth, O)), M1, tOth, O, Referent.Instance(0)),
      "method"    -> Tree.MethodRef(Left(tt(tOth)), M2, tOth, O, Referent.Instance(0)),
      "referent"  -> Tree.MethodRef(Left(tt(tOth)), M1, tOth, O, Referent.Static(0)),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Break --------------------------------------------------------------------------------
    probe(Tree.Break(None, tUnit, O),
      (x: Tree.Break) => hostTerm(Tree.Block(List(
        Tree.While(Tree.Literal(Constant.BoolC(true), tBool, O), Tree.Block(List(x), unitLit, tUnit, O), tUnit, O,
          label = Some("outer"))), unitLit, tUnit, O)))(
      "label" -> Tree.Break(Some("outer"), tUnit, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Continue -----------------------------------------------------------------------------
    probe(Tree.Continue(None, tUnit, O),
      (x: Tree.Continue) => hostTerm(Tree.Block(List(
        Tree.While(Tree.Literal(Constant.BoolC(true), tBool, O), Tree.Block(List(x), unitLit, tUnit, O), tUnit, O,
          label = Some("outer"))), unitLit, tUnit, O)))(
      "label" -> Tree.Continue(Some("outer"), tUnit, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Labeled ------------------------------------------------------------------------------
    probe(Tree.Labeled("here", Tree.Block(List(Tree.Break(Some("here"), tUnit, O)), unitLit, tUnit, O), tUnit, O),
      (x: Tree.Labeled) => hostTerm(Tree.Block(List(x), unitLit, tUnit, O)))(
      "stmt" -> Tree.Labeled("here", Tree.Block(List(Tree.Break(Some("here"), tUnit, O), Tree.Assign(refA, iLit(1), tUnit, O)), unitLit, tUnit, O), tUnit, O),
    )("name" -> Indirect("`TirEmitter.labelNeedsBoundary` (whether a boundary is emitted at all — " +
        "a label nobody breaks to is not control flow) and the `labelBreak` binding under it, " +
        "which is what makes `break name` resolve to THIS statement. The emitted boundary is named " +
        "`lbl$N` and never java's label: java's label lives in java's own namespace and could " +
        "collide with anything the statement contains"),
      "tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Assert -------------------------------------------------------------------------------
    probe(Tree.Assert(Tree.Literal(Constant.BoolC(true), tBool, O), Some(sLit("boom")), tUnit, O), hostTerm)(
      "cond" -> Tree.Assert(Tree.Literal(Constant.BoolC(false), tBool, O), Some(sLit("boom")), tUnit, O),
      "msg"  -> Tree.Assert(Tree.Literal(Constant.BoolC(true), tBool, O), None, tUnit, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- IncDec -------------------------------------------------------------------------------
    probe(Tree.IncDec(refA, "+", post = true, tInt, O), hostTerm)(
      "target" -> Tree.IncDec(refB, "+", post = true, tInt, O),
      "op"     -> Tree.IncDec(refA, "-", post = true, tInt, O),
      "post"   -> Tree.IncDec(refA, "+", post = false, tInt, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- DoWhile ------------------------------------------------------------------------------
    probe(Tree.DoWhile(Tree.Assign(refA, iLit(1), tUnit, O), Tree.Literal(Constant.BoolC(true), tBool, O), tUnit, O,
      label = Some("outer")),
      (x: Tree.DoWhile) => hostTerm(Tree.Block(List(x), unitLit, tUnit, O)))(
      "body"  -> Tree.DoWhile(Tree.Assign(refB, iLit(1), tUnit, O), Tree.Literal(Constant.BoolC(true), tBool, O), tUnit, O, label = Some("outer")),
      "cond"  -> Tree.DoWhile(Tree.Assign(refA, iLit(1), tUnit, O), Tree.Literal(Constant.BoolC(false), tBool, O), tUnit, O, label = Some("outer")),
      "label" -> Tree.DoWhile(Tree.Block(List(Tree.Break(Some("outer"), tUnit, O)), unitLit, tUnit, O), Tree.Literal(Constant.BoolC(true), tBool, O), tUnit, O, label = Some("inner")),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Synchronized -------------------------------------------------------------------------
    probe(Tree.Synchronized(Tree.This(HOST, tOth, O), Tree.Assign(refA, iLit(1), tUnit, O), tUnit, O), hostTerm)(
      "lock" -> Tree.Synchronized(Tree.Ident(OTHER, tOth, O), Tree.Assign(refA, iLit(1), tUnit, O), tUnit, O),
      "body" -> Tree.Synchronized(Tree.This(HOST, tOth, O), Tree.Assign(refB, iLit(1), tUnit, O), tUnit, O),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Unportable, OPEN — the marker refuses the emission, so `inner` is DELIBERATELY not on
    //      the page: that is chunk 4's gate, not a dropped field. Everything the reader needs to
    //      act on is.
    probe(Tree.Unportable.open(iLit(1), UnportableKind.RawGenericConversion, None, "a raw generic", tInt, O2),
      hostTerm)(
      "kind"  -> Tree.Unportable.open(iLit(1), UnportableKind.JdkBoundaryFlow, None, "a raw generic", tInt, O2),
      "diff"  -> Tree.Unportable.open(iLit(1), UnportableKind.RawGenericConversion,
                   Some(balticporter.catalog.DiffId(balticporter.catalog.Area.G, 2)), "a raw generic", tInt, O2),
      "what"  -> Tree.Unportable.open(iLit(1), UnportableKind.RawGenericConversion, None, "something else", tInt, O2),
      "state" -> Tree.Unportable.open(iLit(1), UnportableKind.RawGenericConversion, None, "a raw generic", tInt, O2)
                   .resolved("APhase", "rewrote it"),
      // the marker is the ONE node whose origin reaches the page: `Unportable.fence` and the
      // `compiletime.error` both name the Java position, and `Unportable.open` REFUSES a synthetic
      // one precisely so the marker can be pointed at.
      "origin" -> Tree.Unportable.open(iLit(1), UnportableKind.RawGenericConversion, None, "a raw generic", tInt,
                   Origin("Elsewhere.java", 9, 2)),
    )("tpe" -> tpeIsMetadata),

    // ---- Unportable, RESOLVED — the second host, and the only one in which `inner` renders. A
    //      discharged marker emits the replacement term and nothing else, which is exactly what
    //      tells a DISCHARGE from an ERASURE (`MarkerCheck`); a probe that only ever built an open
    //      marker would have had to excuse `inner` on the strength of the gate.
    probe(Tree.Unportable.open(iLit(1), UnportableKind.RawGenericConversion, None, "a raw generic", tInt, O2)
            .resolved("APhase", "rewrote it"),
      hostTerm)(
      "inner" -> Tree.Unportable.open(iLit(2), UnportableKind.RawGenericConversion, None, "a raw generic", tInt, O2)
                   .resolved("APhase", "rewrote it"),
    )(),

    // ---- Opaque -------------------------------------------------------------------------------
    probe(Tree.Opaque("f( " + "0" + " )", tInt, O, holes = List(iLit(1))), hostTerm)(
      "raw"   -> Tree.Opaque("g( " + "0" + " )", tInt, O, holes = List(iLit(1))),
      "holes" -> Tree.Opaque("f( " + "0" + " )", tInt, O, holes = List(iLit(2))),
    )("tpe" -> tpeIsMetadata, "origin" -> originIsMetadata),

    // ---- Commented ----------------------------------------------------------------------------
    probe(Tree.Commented(List(triv("note")), Tree.Assign(refA, iLit(1), tUnit, O)),
      (x: Tree.Commented) => hostTerm(Tree.Block(List(x), unitLit, tUnit, O)))(
      "leading" -> Tree.Commented(List(triv("other note")), Tree.Assign(refA, iLit(1), tUnit, O)),
      "stmt"    -> Tree.Commented(List(triv("note")), Tree.Assign(refB, iLit(1), tUnit, O)),
    )(),

    // ---- AnonClass ----------------------------------------------------------------------------
    probe(Tree.AnonClass(ANON, List(Tree.ValDef(A, tt(tInt), Some(iLit(1)), O)), O, dropped = Nil),
      (x: Tree.AnonClass) => hostTerm(Tree.New(tt(tOth), tOth, O, anon = Some(x))))(
      "body" -> Tree.AnonClass(ANON, List(Tree.ValDef(B, tt(tInt), Some(iLit(1)), O)), O, dropped = Nil),
    )("symbol" -> Indirect("the anonymous type's OWNER symbol, read by `TirEmitter`'s member " +
        "indexing so the body's members key under it rather than under the enclosing class; Scala " +
        "spells an anonymous class by position and never by name"),
      "dropped" -> Indirect("what the FRONTEND could not translate, read by `OmissionCheck` so a " +
        "lost member is reported instead of vanishing — a residue about the input, never output"),
      "sam" -> Indirect("JAVA'S OWN single-abstract-method answer about the target's CLASS FILE " +
        "(`Sam.Answer`), computed by the frontend because nothing in the TIR can — an interface " +
        "whose method the program never calls has no interned members at all. Read by " +
        "`SamLambda.decide` and by nothing in the emitter: an anonymous class emits identically " +
        "whatever this says, and the node the CONVERSION produces is a `Lambda` rather than this " +
        "one"),
      "origin" -> originIsMetadata),

    // ---- EnumCase -----------------------------------------------------------------------------
    probe(Tree.EnumCase(EC1, Nil, Nil, O, leading = List(triv("the constant"))),
      (x: Tree.EnumCase) => hostTop(Tree.ClassDef(ENUMT, Nil, None, Nil, O, enumCases = List(x))))(
      "symbol"   -> Tree.EnumCase(EC2, Nil, Nil, O, leading = List(triv("the constant"))),
      "ctorArgs" -> Tree.EnumCase(EC1, List(iLit(1)), Nil, O, leading = List(triv("the constant"))),
      "body"     -> Tree.EnumCase(EC1, Nil, List(Tree.DefDef(M1, List(Nil), tt(tInt), Some(iLit(1)), O)), O,
                      leading = List(triv("the constant"))),
      "leading"  -> Tree.EnumCase(EC1, Nil, Nil, O, leading = Nil),
    )("origin" -> originIsMetadata),
  )

  // ============================================================================================
  // THE ASSERTIONS
  // ============================================================================================

  private lazy val byOwner: Map[String, List[Probe[?]]] = probes.groupBy(_.owner)

  test("the class-file scan actually found the node set — a silent zero makes every assertion below vacuous") {
    // pinned by SHAPE, never by a total: a total is the constant `PortabilityCheck`'s phantom
    // "34 rules" is the lesson about.
    assert(nodeKinds.sizeIs > 30, s"only ${nodeKinds.size} Tree node kinds found — the class layout has moved")
    assert(nodeKinds("Ident"), nodeKinds)
    assert(nodeKinds("TypeTree"), nodeKinds)   // a Tree that is NOT under `object Tree`
    assert(nodeKinds("Unportable"), nodeKinds) // the marker
    assertEquals(aggregates, Set("EnumCase", "AnonClass", "CatchCase", "CaseDef"),
      "the case classes inside `object Tree` that are not Trees have changed")
  }

  test("TOTALITY: every Tree node kind and every aggregate has a probe, and every probe a node") {
    val expected = nodeKinds ++ aggregates
    val probed   = byOwner.keySet
    assertEquals((expected -- probed).toList.sorted, Nil,
      "these TIR nodes have no emission probe — add one, or the emitter may already be dropping a field of theirs")
    assertEquals((probed -- expected).toList.sorted, Nil,
      "these probes name a node the class files do not have — a renamed or deleted case")
  }

  test("COVERAGE: every FIELD of every node is either moved by a probe or reasoned about — never both, never neither") {
    val unknown  = List.newBuilder[String]
    val doubled  = List.newBuilder[String]
    val phantom  = List.newBuilder[String]
    for (owner, ps) <- byOwner do
      val fields   = ps.head.fields
      val moved    = ps.flatMap(_.movedKeys).toSet
      val reasoned = ps.flatMap(_.notEmitted.keys).toSet
      for p <- ps; k <- p.movedKeys ++ p.notEmitted.keySet if !p.fields.contains(k) do
        phantom += s"$owner.$k"
      for f <- fields do
        if !moved(f) && !reasoned(f) then unknown += s"$owner.$f"
        if moved(f) && reasoned(f) then doubled += s"$owner.$f"
    assertEquals(phantom.result().sorted, Nil, "a probe names a field the node does not have")
    assertEquals(unknown.result().sorted, Nil,
      "these fields are neither perturbed nor on the `notEmitted` list. The default for an unknown " +
        "field is NOT COVERED: either the emitter renders it (add a perturbation) or it does not " +
        "(say which of the two admissible reasons applies)")
    assertEquals(doubled.result().sorted, Nil,
      "a field cannot both move the emitted text and be excused from doing so")
  }

  test("EMISSION: perturbing a covered field CHANGES the emitted text") {
    val silent = List.newBuilder[String]
    for p <- probes; (f, text) <- p.movedText if text == p.base do silent += s"${p.owner}.$f"
    assertEquals(silent.result().sorted, Nil,
      "these fields were perturbed and the emitted text did not move — which is `ENGINE-LIMITS.md` " +
        "F5's shape exactly: the frontend populates the field, every phase carries it, and the " +
        "emitter never renders it. Either fix the emitter or say why the field is not emitted")
  }

  test("every catalog row attaching to the RENDERING dispatch names a Tree kind that exists") {
    // The registry's half of this derivation, asserted here because here is where the node set is
    // already derived — a second, hand-written list of node names in the catalog's own suite is the
    // shape this file exists to refuse. A misspelt `Attaches.Rendered` kind attaches to no node, so
    // it owes no consult, produces no hole, and reports `unreached` on every port forever: the
    // failure is indistinguishable from a branch the corpus does not exercise.
    val phantom = (balticporter.catalog.Differences.renderedKinds -- nodeKinds).toList.sorted
    assertEquals(phantom, Nil,
      s"these rows attach to a `Tree` kind the IR does not have: ${phantom.mkString(", ")}")
  }

  test("every catalog row attaching to the TYPE dispatch names a `TypeRepr` case that exists") {
    // The same derivation for the FOURTH obligation surface's emitter half. The scan is pinned by
    // SHAPE first, because a filter that silently matched nothing would make the assertion below
    // vacuous — and `TypeRepr$`-prefixed names are exactly the kind of predicate that goes quiet
    // when a file moves.
    assert(typeReprKinds.sizeIs > 10, s"only ${typeReprKinds.size} TypeRepr cases found — the class layout has moved")
    assert(typeReprKinds("TypeRef"), typeReprKinds)
    assert(typeReprKinds("AppliedType"), typeReprKinds)
    assert(typeReprKinds("TypeBounds"), typeReprKinds)
    assert(typeReprKinds("NoType"), typeReprKinds)      // a case OBJECT — the half a `$` filter drops
    val phantom = (balticporter.catalog.Differences.renderedTypeKinds -- typeReprKinds).toList.sorted
    assertEquals(phantom, Nil,
      s"these rows attach to a `TypeRepr` case the algebra does not have: ${phantom.mkString(", ")}")
  }

  test("every `notEmitted` reason is one of the two admissible kinds, and says something") {
    val thin = List.newBuilder[String]
    for p <- probes; (f, why) <- p.notEmitted do
      val text = why match
        case Why.Indirect(reads) => reads
        case Why.Metadata(what)  => what
      if text.length < 40 then thin += s"${p.owner}.$f"
    assertEquals(thin.result().sorted, Nil,
      "a one-word reason is a suppression with a label on it")
  }
