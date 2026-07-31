package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*


/** Re-point every reference to one type at ANOTHER type, at a different fully-qualified name.
  *
  * ==The gap this fills==
  * A port has three ways to deal with a type it must not translate, and until now all three were
  * only available to the module that OWNS the type:
  *
  *   - `Substitutions.dropTypes` + `inject` — replace it at the SAME FQN;
  *   - `StaticForwarderTransform` — inline a static wrapper into its first argument;
  *   - `ClassTableTransform` — re-point one reflective METHOD at an explicit table.
  *
  * A DEPENDENT module has none of them. When a base drops a type outright — no replacement, because
  * the base itself had no remaining use for it — a dependent that DOES use it is stuck: it cannot
  * inject at the base's FQN (exactly one module ships each replacement, or the two definitions
  * collide on the Scala.js and Native linkers), and it cannot un-drop it.
  *
  * The worked example is Ashley's `PooledEngine.ComponentPools`, whose `ReflectionPool` uses are
  * all TYPE occurrences — a field's type, a local's type, a `new`, and several cast targets — so
  * [[MethodBodyTransform]] cannot reach them; a body seam cannot change a field's type. libGDX drops
  * `ReflectionPool` because reflective pooling is the one thing Scala.js and Native cannot do, and
  * every remaining libGDX use went with it. Ashley's did not.
  *
  * ==What the dependent must supply==
  * A type of its own, at its own FQN, SHAPE-COMPATIBLE with the one being replaced: the same arity,
  * a constructor with the same parameters, and the members the referring code calls. The engine does
  * not and cannot verify that — the target is ordinary Scala the port ships, and the target compiler
  * is the gate. What the engine does guarantee is that every reference moves together, so a partial
  * redirect is impossible.
  *
  * ==Kind==
  * CLAUDE.md §1(b). The MECHANISM — rewrite every occurrence of one type symbol to another — is a
  * fact about namespacing. WHICH type becomes WHICH is a fact about one library and arrives as a
  * constructor parameter. An empty map is a no-op.
  *
  * ==Why this is safe where a rename is not==
  * [[PackageRenameTransform]] rewrites a symbol the program OWNS and must therefore run last, after
  * every phase whose policy is written in the upstream namespace. This does the opposite: it
  * re-points references at a type the program does NOT own, leaving the original symbol untouched
  * and still resolvable. It is therefore ordering-insensitive, and a phase that runs after it sees
  * the redirected type — which is what a later portability or substitution check needs to see.
  */
final class TypeRedirectTransform(redirects: Map[String, String] = Map.empty) extends Phase, PolicySource, SurfacePolicy:
  def name: String = "type-redirect"

  /** Re-pointing a type CHANGES EMITTED SIGNATURES — a field, a parameter and a return type all
    * move — so it is part of the shared surface and two modules must not disagree about it. */
  def surfaceFingerprint: String = redirects.toList.sorted.map((f, t) => s"$f->$t").mkString(",")

  private var mapping: Map[SymId, SymId] = Map.empty
  private var report: PolicyReport       = PolicyReport.empty

  /** Declared sources that occur nowhere — the redirect silently did not happen and the dependency
    * the port was configured to remove is still there. The symmetric failure to every other (b)
    * seam's, and the reason [[PolicyReport]] exists. */
  def policyReport: PolicyReport = report

  override def run(program: Program): Program =
    if redirects.isEmpty then
      report = PolicyReport.empty; mapping = Map.empty
      return program

    val byName = program.symbols.all.iterator.map(s => s.fullName -> s).toMap
    var table  = program.symbols
    var next   = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1

    // Reuse the target symbol when the program already refers to it, else mint one. Minting is what
    // makes a redirect to a type NOT otherwise mentioned work at all — the dependent's injected
    // replacement is never parsed, so nothing has interned it.
    //
    // `NoType` and `SymId.None` are not placeholders here: together with a dotted, `#`-free name
    // they are exactly the shape the emitter reads as "an external TYPE", which is what lets the
    // minted symbol stand in a static-access receiver position. Giving it a self `TypeRef` — the
    // `info` a DECLARED class carries — reads as a term instead and emits an unqualified name.
    def targetOf(fqn: String): SymId = byName.get(fqn).map(_.id).getOrElse {
      val id = SymId(next); next += 1
      table = table.updated(Symbol(id, fqn.split('.').last, fqn, Flags(), SymId.None, TypeRepr.NoType))
      id
    }

    val missing = redirects.keys.toList.sorted.filterNot(byName.contains)
    report = PolicyReport(missing.map(from =>
      PolicyFinding(name, "TypeRedirectTransform", from, PolicyIssue.NeverMatched,
        s"no type of that name occurs in this program, so nothing was re-pointed at " +
          s"`${redirects(from)}` and every reference still names the original")))

    mapping = redirects.collect { case (from, to) if byName.contains(from) => byName(from).id -> targetOf(to) }

    // THE MEMBERS MOVE WITH THE TYPE, and there are TWO ways a member reference is rendered.
    //
    //   - a redirected type the frontend NEVER PARSED (a jar on the classpath) reaches its statics
    //     through an explicit `Select(Ident(type), member)`, and `transformIdent` below moves that;
    //   - a redirected type the frontend DID parse — a resolution root, which is the ordinary case
    //     for a dependent port — is re-qualified BY THE EMITTER from the member symbol's OWNER
    //     (`TirEmitter.staticThroughInstance`), which deliberately ignores the qualifier in the
    //     tree. Remapping the qualifier alone is therefore undone one layer later, silently.
    //
    // Re-pointing the member's owner covers the second, and is exact for the same reason the rest
    // of the phase is: the contract is that the port does NOT ship the redirected type, so the only
    // references to its members are the ones being redirected. The member keeps its `SymId`, so no
    // tree node has to move, and its NAME is rebuilt by replacing the owner's prefix — carrying the
    // `#`/`$` separators across verbatim (CLAUDE.md §4.56) rather than re-deriving them.
    val membersOf: Map[SymId, List[SymId]] = mapping.map { (fromType, toType) =>
      // Read BOTH names out of `table`, never out of `program`: a MINTED target is in the former
      // only, and read from the latter it is the empty string — which silently leaves every member
      // named after the type the redirect just removed.
      val fromFqn = table.get(fromType).map(_.fullName).getOrElse("")
      val toFqn   = table.get(toType).map(_.fullName).getOrElse("")
      val moved   = program.symbols.all.filter(_.owner == fromType).toList
      moved.foreach { m =>
        table = table.updated(m.copy(
          owner    = toType,
          fullName = if m.fullName.startsWith(fromFqn) then toFqn + m.fullName.drop(fromFqn.length)
                     else m.fullName,
        ))
      }
      fromType -> moved.map(_.id)
    }

    // DECISION PROVENANCE, one row per (DECLARATION, redirect entry). `RetypedSignature` and not
    // `RedirectedCall`: what moves is a TYPE occurrence — a field's type, a parameter's, a `new`,
    // a cast target — so the thing an agent sees changed in the emitted file is the declaration's
    // signature, and no call was re-pointed at all. Read from the PRE-rewrite program, which is
    // the only one where a usage still names the type this phase is redirecting AWAY from.
    //
    // A declaration that only CALLS a static of the redirected type uses the member symbol and
    // never the type's own, so the type's usages alone under-report it — the members are folded in
    // here for the same reason they are re-pointed above.
    redirects.toList.sorted.foreach { (from, to) =>
      byName.get(from).foreach { sym =>
        val sites = (sym.id :: membersOf.getOrElse(sym.id, Nil)).flatMap(Decision.declarationsUsing(program, _))
          .groupBy(_._1).toList
          .flatMap((encl, os) => os.map(_._2).minByOption(o => (o.javaPath, o.line)).map(encl -> _))
          .sortBy((encl, o) => (o.javaPath, o.line, encl.raw))
        sites.foreach { (encl, origin) =>
          record(Decision(
            kind       = Decision.Kind.RetypedSignature,
            subject    = encl,
            subjectFqn = Decision.fqnOf(program, encl, from),
            detail     = Map(
              "from" -> from,
              "to"   -> to,
              "key"  -> from,
              "why"  -> ("a module this port depends on drops this type outright, and exactly one " +
                "module may ship a replacement at a given FQN — so every reference is re-pointed " +
                "at a shape-compatible type this port declares itself"),
            ),
            reason = Reason.Configured(name, s"$from -> $to"),
            origin = origin,
          ))
        }
      }
    }

    if mapping.isEmpty then
      new Program(program.units, table, program.xref)
    else
      given Program = new Program(program.units, table, program.xref)
      // The STANDARD traversal, so every type occurrence the tree has — parents, self-types, tpts,
      // type arguments, `new`, ascriptions — and every symbol `info` is routed through
      // `transformType`. A private walk here would reach the ones it remembered and miss whichever
      // node kind is added next (CLAUDE.md §3).
      val units   = program.units.map(u => StandardTraversal.mapClassDef(this, u))
      val symbols = StandardTraversal.mapSymbols(this, table)
      new Program(units, symbols, program.xref) // xref rebuilt by the Pipeline

  override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
    case TypeRepr.TypeRef(p, s) if mapping.contains(s) => TypeRepr.TypeRef(p, mapping(s))
    case other                                         => other

  /** A TYPE also occurs as a TERM: the receiver of a static access is an `Ident` of the type's own
    * symbol (`Preconditions.checkArgument(x)`, `SlidingDirection.UP`), and the emitter renders it
    * from that SYMBOL, not from the node's `TypeRepr`. `transformType` therefore cannot see it, and
    * a redirect that stopped there left the static half of the surface naming the very type the
    * port was configured not to have — a PARTIAL redirect, which this phase's contract says is
    * impossible. It went unmeasured until a library redirected a type with STATICS: the first one
    * to use the phase had none, and no count moves for it (the emitted file is uncompilable at
    * exactly the sites the redirect was supposed to fix).
    *
    * Only TYPE symbols are in `mapping`, so a term of the same name cannot be caught by accident.
    * The MEMBER symbol keeps its identity — a static of the replacement is reached through the
    * replacement, which is precisely what "shape-compatible" above requires of it. */
  override def transformIdent(t: Tree.Ident)(using Program): Term =
    if mapping.contains(t.sym) then t.copy(sym = mapping(t.sym)) else t
