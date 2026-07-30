package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*
import balticporter.tir.TypeRepr.NoType

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
    def targetOf(fqn: String): SymId = byName.get(fqn).map(_.id).getOrElse {
      val id = SymId(next); next += 1
      table = table.updated(Symbol(id, fqn.split('.').last, fqn, Flags(), SymId.None, NoType))
      id
    }

    val missing = redirects.keys.toList.sorted.filterNot(byName.contains)
    report = PolicyReport(missing.map(from =>
      PolicyFinding(name, "TypeRedirectTransform", from, PolicyIssue.NeverMatched,
        s"no type of that name occurs in this program, so nothing was re-pointed at " +
          s"`${redirects(from)}` and every reference still names the original")))

    mapping = redirects.collect { case (from, to) if byName.contains(from) => byName(from).id -> targetOf(to) }

    // DECISION PROVENANCE, one row per (DECLARATION, redirect entry). `RetypedSignature` and not
    // `RedirectedCall`: what moves is a TYPE occurrence — a field's type, a parameter's, a `new`,
    // a cast target — so the thing an agent sees changed in the emitted file is the declaration's
    // signature, and no call was re-pointed at all. Read from the PRE-rewrite program, which is
    // the only one where a usage still names the type this phase is redirecting AWAY from.
    redirects.toList.sorted.foreach { (from, to) =>
      byName.get(from).foreach { sym =>
        Decision.declarationsUsing(program, sym.id).foreach { (encl, origin) =>
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
