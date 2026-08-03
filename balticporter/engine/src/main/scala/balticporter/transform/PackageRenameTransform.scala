package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Move the port OUT of the upstream namespace: rewrite the package prefix of every symbol the
  * program itself declares — and, per TYPE, move one type at a time.
  *
  * A hand-maintained port does not live where its upstream did — it lives where the consuming
  * codebase already references it. Emitting the upstream namespace is therefore not a cosmetic
  * mismatch but an adoption blocker: the dependent code is written against the port's names, and
  * two namespaces cannot both be the one that compiles.
  *
  * A CLAUDE.md §1(b) phase. The MECHANISM — longest-prefix-wins over `Symbol.fullName`, applied to
  * owned symbols only — is a fact about namespacing and is the same for every library; WHICH prefix
  * becomes which is a fact about one port, so it arrives as a constructor parameter. An empty map is
  * a no-op, so "turned off" needs no code path.
  *
  * ==FOUR policy maps, ONE phase, ONE last position==
  * A package rename moves a whole namespace; a hand port routinely does three other things that a
  * whole-namespace map cannot say, and all four are the SAME rewrite — a prefix of `Symbol.fullName`
  * replaced, cut at a separator:
  *
  *   - `renames` — upstream package prefix → port prefix. The original knob.
  *   - `typeRenames` — ONE type's FQN → what it is called in the port. The worked example is a
  *     library with a type called `Map`, which is a name a Scala consumer cannot import beside the
  *     standard library's without qualifying every use.
  *   - `subPackages` — ONE type's FQN → a sub-package to nest it under, in place. The worked example
  *     is a port that hides its implementation types in an `internal` package the upstream did not
  *     have.
  *   - `flattenNestedTypes` — a NESTED type's FQN (`p.Outer$Inner`) promoted to top level. The
  *     worked example is a hand port that publishes a nested implementation class as an ordinary
  *     one, because a Scala consumer reads `Outer.Inner` as a path-dependent type.
  *
  * They are one phase and not four for the reason the ordering rule below states: EVERY one of them
  * has to run after every other phase, and `runsAfter` cannot say "after everything". Four phases
  * would be four things a porting program must remember to place last, and the one it forgets is a
  * phase whose policy silently matches nothing. The last three are DERIVED into per-type entries of
  * the same table the first one uses, so there is one rewrite loop, one `longestMatch` and one
  * position check.
  *
  * ==Every target is written in the UPSTREAM namespace==
  * A `typeRenames` or `subPackages` target names the type as the LIBRARY spells it, not as the port
  * emits it, and the package rename is then applied to it. That is CLAUDE.md §4.56's rule — "every
  * other phase's policy is written in the UPSTREAM namespace" — applied to this phase's own second
  * map, and it is what lets a port add a package rename later without rewriting every type entry.
  * A `typeRenames` value with no separator in it is a SIMPLE NAME and renames the type in place; a
  * dotted value is a whole FQN. Nothing else is legal, and a `#` in a target is refused.
  *
  * ==Why this renames SYMBOLS and not text==
  * The TIR is symbol-resolved: every reference in every tree and every `TypeRepr` names a `SymId`,
  * never a string. Renaming the symbol therefore reaches every reference by construction, including
  * ones no textual rewrite could find — a type argument nested inside a method type inside an
  * inherited signature. Trees and the xref are untouched on purpose: they are keyed by `SymId`, and
  * an id is exactly what a rename must NOT change. (`flattenNestedTypes` is the one exception, and
  * it is not a counter-example: promoting a nested type to top level is a change to the TREE — the
  * `ClassDef` leaves its enclosing body — which no name rewrite can express.)
  *
  * ==Only symbols the program OWNS==
  * A prefix match alone would silently rewrite the standard library — `PackageRenameTransform(Map(
  * "java" -> "j"))` must not turn `java.lang.String` into `j.lang.String`, and neither must an
  * honest map that happens to share a prefix. Ownership is decided structurally, the same way the
  * rest of the engine tells in-program from external: an external symbol is lazily interned by the
  * frontend with `owner = SymId.None` and no `Definition` in the xref, while everything the program
  * declares hangs — through the `owner` chain — off one of the top-level units. So a symbol is owned
  * iff climbing its owners reaches a `program.units` symbol. That is strictly stronger than "has a
  * definition", which anonymous-class symbols do not.
  *
  * The per-TYPE maps get the same rule with teeth: each key is bound through [[PolicyBinder]] as
  * `Ownership.Owned`, so an entry naming a JDK type — or a type this port DROPS, whose replacement
  * is injected Scala the frontend never parsed — is refused rather than silently succeeding.
  *
  * ==Where the prefix is cut==
  * `fullName` is a single string with THREE separators: `.` between packages and the top-level
  * type, `$` before a nested type (`p.Outer$Inner`), and `#` before a member (`p.Outer#m`). A
  * prefix therefore matches only at one of those boundaries — `com.foo` must not match
  * `com.foobar`, and neither must `com.foo.Bar` match `com.foo.Barn` — and everything after the cut
  * is carried across verbatim, so nested-type and member structure survives untouched. The emitter
  * derives the `package` clause, the nested-type path and the output file path from this one string,
  * so cutting anywhere else would show up only at emission.
  *
  * `Symbol.name` (the simple name) changes ONLY when the symbol IS the renamed entity, i.e. its
  * whole `fullName` equals the `from` prefix. A package rename never hits that case (packages have
  * no symbol of their own), so simple names are untouched; renaming a TYPE to a differently-named
  * type does, and then the simple name must follow or the emitter renders the old one.
  *
  * ==Ordering: this phase runs LAST==
  * Every other phase's policy is written in the UPSTREAM namespace — `ClassTableTransform`'s
  * redirects, `StaticForwarderTransform`'s wrappers, `Substitutions`' dropped types, an
  * `PrimitiveToOpaque` hint list are all FQNs from the library as it ships. Renaming first invalidates
  * all of them at once, silently, because a policy that matches nothing is a phase that does
  * nothing. `runsAfter` cannot express "after everything", so the porting program must place this
  * phase last in its list; `Pipeline.order` is stable in declaration order, so that is sufficient.
  * [[PackageRenameTransform.check]] is the guard: run before the phase it says what will move, run
  * after it must report every prefix as unmatched.
  *
  * ==What this phase does NOT reach==
  * Injected substitution sources (`Substitutions` replacement text) never pass through the TIR —
  * they are copied verbatim, carry their own `package` clause, and have no symbols to rename. A
  * port that both substitutes and renames must write those replacements in the renamed namespace
  * itself. This is the same blind spot `PortabilityCheck.inInjectedSource` exists for.
  */
final class PackageRenameTransform(
    renames: Map[String, String] = Map.empty,
    /** upstream TYPE FQN → its name in the port: a dotted FQN, or a bare simple name to rename it
      * in place. Inherited by dependents (CLAUDE.md §1.5) — it is a fact about the shared surface. */
    typeRenames: Map[String, String] = Map.empty,
    /** upstream TYPE FQN → a sub-package to nest it under, in place (`internal`, `impl.detail`). */
    subPackages: Map[String, String] = Map.empty,
    /** NESTED type FQNs (`p.Outer$Inner`) promoted to TOP LEVEL. */
    flattenNestedTypes: Set[String] = Set.empty,
    /** upstream TYPE FQNs whose BOUNDARY MOVE the port declares deliberate — see
      * [[PackageRenameTransform.Boundary]]. An entry that refuses nothing is itself reported. */
    allowPackageSplit: Set[String] = Set.empty,
) extends Phase,
      PolicyBound,
      PolicySource,
      SurfacePolicy:
  import PackageRenameTransform.*

  def name: String = "package-rename"

  /** Does this instance carry per-TYPE policy at all? When it does not, the phase is exactly what
    * it was before per-type renames existed and needs no binding to answer any question. */
  private val perTypeDeclared: Boolean =
    typeRenames.nonEmpty || subPackages.nonEmpty || flattenNestedTypes.nonEmpty

  // ---- bound state (§8.1: a phase decides from BOUND symbols, never from a raw string) ----

  /** the ACCEPTED table: upstream prefix → the name a symbol under it is emitted with. Per-type
    * targets are already composed through `renames`, so this is ONE longest-prefix map and the
    * rewrite, the check and [[emittedName]] read the same one. */
  private var accepted: Map[String, String] = renames
  /** the per-TYPE half of [[accepted]] — what distinguishes a `RenamedType` row from a
    * `RenamedPackage` one, and what [[check]] reports separately. */
  private var acceptedTypes: Map[String, String] = Map.empty
  /** accepted `flattenNestedTypes` keys, by the SymId of the nested type to promote. */
  private var promote: Map[SymId, String] = Map.empty
  /** declarations whose ACCESS BOUNDARY a declared move widens — one row each, recorded by [[run]]
    * so D3's qualifier derivation reads the widening rather than re-deriving it (DESIGN.md §8.7). */
  private var widenings: List[Widening] = Nil
  private var records: List[PolicyBinder.Record] = Nil
  private var extraFindings: List[PolicyFinding] = Nil
  private var didBind: Boolean = false

  /** A rename CHANGES EMITTED SIGNATURES — every reference to the type moves with it — so it is
    * part of the shared surface and two modules must not disagree about it. `PortRun` never places
    * this phase in a `surface` list (it takes the maps as data and appends the phase last), so this
    * is the belt to `ManifestAgreement`'s braces rather than the mechanism; it costs one method and
    * makes a hand-assembled pipeline comparable. */
  def surfaceFingerprint: String =
    def m(label: String, kv: Map[String, String]) =
      if kv.isEmpty then "" else kv.toList.sorted.map((f, t) => s"$f->$t").mkString(s"$label:", ",", ";")
    m("pkg", renames) + m("type", typeRenames) + m("sub", subPackages) +
      (if flattenNestedTypes.isEmpty then "" else flattenNestedTypes.toList.sorted.mkString("flat:", ",", ";")) +
      (if allowPackageSplit.isEmpty then "" else allowPackageSplit.toList.sorted.mkString("split:", ",", ";"))

  /** Declared per-type keys that named nothing, named only an external, or could never have been
    * carried out. Derived from the BINDING plus this phase's own structural refusals, so it is
    * complete before the pipeline runs and says the same thing whether or not the phase ran. */
  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(extraFindings)

  /** the ACCEPTED table, for the layers that must name a type in BOTH namespaces (§4.56) — the
    * dropped-type artifact, the source map's drop filter, the decision log's `emitted` column. */
  def upstreamTable: Map[String, String] = accepted

  /** what `fqn` is emitted as under this instance's ACCEPTED policy. */
  def emittedName(fqn: String): String = renamed(fqn, accepted)

  /** the boundary moves this run RECORDED as deliberate — `PortRun` reads nothing off this; it is
    * here so a spec can assert the record without a filesystem. */
  def recordedWidenings: List[Widening] = widenings

  // -------------------------------------------------------------------------
  // BINDING — every per-type key becomes a SYMBOL before any phase runs (§8.1)
  // -------------------------------------------------------------------------

  /** Resolve every per-TYPE key, refuse what cannot be carried out, and decide which boundary
    * moves this port has DECLARED.
    *
    * Everything happens here rather than in `run` for the reason `PolicyBound` states: binding
    * before the pipeline resolves each key against the names its author wrote, so this phase's
    * position can no longer change what its keys mean — and "did this entry fire?" becomes a
    * property of the policy and the program rather than of the run.
    */
  def bindPolicy(binder: PolicyBinder): Unit =
    didBind = true
    accepted = renames
    acceptedTypes = Map.empty
    promote = Map.empty
    widenings = Nil
    records = Nil
    extraFindings = Nil
    if !perTypeDeclared then
      if allowPackageSplit.nonEmpty then
        extraFindings = allowPackageSplit.toList.sorted.map(k =>
          PolicyFinding(name, AllowSetting, k, PolicyIssue.NeverMatched,
            "no per-type rename names this type, so there is no boundary move to declare"))
      return

    val program = binder.program
    // Every key, with the map it came from. A key named by two maps is refused on BOTH, because
    // "which one wins" is a question policy must not leave to declaration order.
    val requested: List[Request] =
      typeRenames.toList.sorted.map((k, v) => Request(k, TypeSetting, Some(v))) ++
        subPackages.toList.sorted.map((k, v) => Request(k, SubSetting, Some(v))) ++
        flattenNestedTypes.toList.sorted.map(k => Request(k, FlatSetting, scala.None))
    val doubled = requested.groupBy(_.key).collect { case (k, rs) if rs.size > 1 => k }.toSet

    // stage 1 — the key names a type THIS PROGRAM DECLARES.
    val bound: List[(Request, SymId)] = requested.flatMap { r =>
      binder.bindType(name, r.setting, r.key).toOption.map(r -> _)
    }
    records = binder.recordsFor(name)

    // stage 2 — the request is one this phase can carry out at all.
    val shaped: List[Move] = bound.flatMap { (r, sid) =>
      val sym = program.symbolOf(sid)
      if doubled(r.key) then
        refuse(r, PolicyIssue.Malformed,
          "this type is named by more than one of `typeRenames`, `subPackages` and " +
            "`flattenNestedTypes`; one type, one destination")
      else
        target(r, sym) match
          case Left(why)  => refuse(r, PolicyIssue.Malformed, why)
          case Right(tgt) => Some(Move(r, sid, tgt, renamed(tgt, renames)))
    }

    // stage 3 — the DESTINATION is free. A bound target FQN is a COLLISION, not a hit: two types
    // at one emitted name compile as one file overwriting the other, and no count moves for it.
    val typeNames: Map[String, String] =
      allClasses(program).flatMap(cd => program.symbolOf(cd.symbol)).map(s => s.fullName -> renamed(s.fullName, renames)).toMap
    val free: List[Move] = shaped.flatMap { mv =>
      val taken = typeNames.collectFirst { case (up, em) if up != mv.key && em == mv.emitted => up }
      val twin  = shaped.collectFirst { case o if o.key != mv.key && o.emitted == mv.emitted => o.key }
      (taken orElse twin) match
        case Some(other) =>
          refuse(mv.request, PolicyIssue.Malformed,
            s"""the destination "${mv.emitted}" is already the emitted name of `$other` — a rename """ +
              "target must be FREE, and a bound one is a collision two files would silently resolve " +
              "by overwriting each other")
        case scala.None => Some(mv)
    }

    // stage 4 — the ACCESS BOUNDARY the move crosses (DESIGN.md §8.7's `package-split`).
    val kept: List[Move] = free.flatMap { mv =>
      val b = boundaryOf(program, mv)
      if b.isEmpty then Some(mv)
      else if allowPackageSplit(mv.key) then
        widenings ++= b
        Some(mv)
      else
        refuse(mv.request, PolicyIssue.Unverifiable,
          s"${b.head.cause} — ${b.size} declaration(s) lose an access boundary Java gave them " +
            s"(${b.map(_.subjectFqn).sorted.take(4).mkString(", ")}${if b.size > 4 then ", …" else ""}). " +
            s"""Either leave the type where it is, or DECLARE the move with `allowPackageSplit += "${mv.key}"`, """ +
            "which records one widening per affected declaration instead of hiding it")
    }

    val unusedAllow = allowPackageSplit -- widenings.map(_.key).toSet
    if unusedAllow.nonEmpty then
      extraFindings ++= unusedAllow.toList.sorted.map(k =>
        PolicyFinding(name, AllowSetting, k, PolicyIssue.NeverMatched,
          "declared as a deliberate boundary move, and no accepted rename of this type moves one"))

    acceptedTypes = kept.map(mv => mv.key -> mv.emitted).toMap
    accepted = renames ++ acceptedTypes
    promote = kept.collect { case mv if mv.request.setting == FlatSetting => mv.sid -> mv.key }.toMap

  private def refuse(r: Request, issue: PolicyIssue, why: String): Option[Move] =
    extraFindings :+= PolicyFinding(name, r.setting, r.key, issue, why)
    scala.None

  /** The UPSTREAM-namespace destination a request names, or why it is not one this phase can carry
    * out. Every answer is upstream: the package rename is applied to it afterwards, once.
    *
    * The STRING half is `PortManifest.TypeMove`'s, so a manifest answering "what will a dependent
    * see" and this phase answering "what do I rewrite to" cannot drift. What is added here is the
    * one judgement a manifest cannot make, because it needs a symbol: a Java INNER class carries an
    * implicit reference to its enclosing instance, so only a STATIC nested type can be promoted. */
  private def target(r: Request, sym: Option[Symbol]): Either[String, String] =
    import balticporter.core.PortManifest.TypeMove
    r.setting match
      case TypeSetting => TypeMove.renameTo(r.key, r.value.getOrElse(""))
      case SubSetting  => TypeMove.subPackage(r.key, r.value.getOrElse(""))
      case _ =>
        TypeMove.flatten(r.key).flatMap { t =>
          if sym.exists(_.flags.isStatic) then Right(t)
          else Left("only a STATIC nested type can be promoted: a Java inner class carries an " +
            "implicit reference to its enclosing instance, and a top-level type has nowhere to keep it")
        }

  // -------------------------------------------------------------------------
  // THE BOUNDARY RULE — DESIGN.md §8.7, and why M6 could not ship without it
  // -------------------------------------------------------------------------

  /** Which declarations a move puts on the wrong side of an access boundary Java gave them.
    *
    * §8.7's dependent-safety argument rested on the premise that *an upstream package cannot
    * split*: `renames` maps whole packages, cut at separators, so two types that shared an upstream
    * package share an emitted one. A per-TYPE rename moves ONE type at a time and falsifies it —
    * two types that shared a package can land in different ones, and a `protected` member declared
    * by one and read by the other then crosses a boundary Java never had. Flattening does the same
    * one level down: Java's `private` reaches throughout the TOP-LEVEL enclosure (JLS 6.6.1), which
    * §8.7 renders `private[TopLevel]`, and a promoted type is no longer inside it.
    *
    * So the answer is not "does this look risky" but "which DECLARATIONS does it break", one row
    * each — which is what a refusal has to say to be actionable, and what a declared move has to
    * RECORD so §8.7's qualifier derivation can read the widening rather than re-derive it.
    *
    * '''The measurable limit, stated rather than implied.''' Java's package-private is not
    * REPRESENTED in this TIR at all — a declaration with no modifier produces flags byte-identical
    * to a `public` one (§8.7) — so this rule sees the `protected` half of the package boundary and
    * not the default-access half. It is written as one predicate ([[restricted]]) so that the flag
    * §8.7 adds is one line here and not a second search of this file.
    *
    * Each entry is judged ALONE, against the package renames plus itself. That is the question
    * being asked ("does THIS entry move a boundary"), and it makes the answer independent of which
    * other entries were refused — an order dependence that would otherwise be invisible.
    */
  private def boundaryOf(program: Program, mv: Move): List[Widening] =
    val solo   = renames + (mv.key -> mv.emitted)
    val wasKey = renamed(mv.key, renames)
    // Read each declaration's OWN `fullName`, never its top-level enclosure's: a promoted nested
    // type's members move with IT, and a rule that asked the enclosure would answer that nothing
    // moved at all — which is exactly the boundary flattening breaks.
    def name(id: SymId): String    = program.symbolOf(id).map(_.fullName).getOrElse("")
    def now(id: SymId): String     = renamed(name(id), solo)
    def before(id: SymId): String  = renamed(name(id), renames)

    val movedPkg = packageOf(mv.emitted) != packageOf(wasKey)
    val movedTop = typeHeadOf(mv.emitted) != typeHeadOf(wasKey)
    if !movedPkg && !movedTop then Nil
    else
      val cause  = if movedPkg then Cause.PackageSplit else Cause.EnclosureSplit
      val inside = under(program, mv.sid)
      // BOTH directions of one crossing, which is why the scan is over every restricted declaration
      // and not over the moved type's own: a `protected` member the moved type DECLARES and
      // something left behind reads, and a `protected` member left behind that the moved type
      // reads. Only one of the two is visible from the moved type.
      //
      // A pair is a CROSSING only if it shared a boundary BEFORE and does not after. That is what
      // makes an ordinary top-level rename silent: renaming `p.Map` to `p.MapFilter` moves every
      // member's head, so two members of it move together and two members either side of it never
      // shared one.
      val rows = program.symbols.all.toList.flatMap { m =>
        if !program.owns(m.id) || !restricted(m.flags, cause) then Nil
        else
          val declaredInside = inside(m.id)
          program.usages(m.id).map(_.enclosing).filter(_ != SymId.None).distinct.flatMap { user =>
            if declaredInside == inside(user) || name(user).isEmpty then Nil
            else
              val (was, is) =
                if cause == Cause.PackageSplit then
                  (reaches(before(m.id), before(user)), reaches(now(m.id), now(user)))
                else
                  (typeHeadOf(before(m.id)) == typeHeadOf(before(user)),
                   typeHeadOf(now(m.id)) == typeHeadOf(now(user)))
              if !(was && !is) then Nil
              else List(Widening(mv.key, cause, m.id, name(m.id),
                                 Decision.fqnOf(program, user, ""), Decision.originOf(program, m.id)))
          }
      }
      rows.distinctBy(w => (w.subject, w.readerFqn)).sortBy(w => (w.subjectFqn, w.readerFqn))

  /** Can a declaration emitted at `reader` still see a package-scoped member emitted at `decl`?
    *
    * NOT string equality, and the difference is the whole of the `subPackages` knob. Java's package
    * boundary is exact — `p.internal` is simply a different package from `p` — while SCALA's
    * `private[p]` covers `p` AND ITS SUBPACKAGES (§8.7: "subpackage nesting only WIDENS, never
    * blocks"). So nesting a type under `p.internal` keeps every restricted member of `p` reachable
    * FROM it, and takes away only the other direction: `p` can no longer see what the nested type
    * restricts. Compared by equality, the rule would refuse every sub-package move for a crossing
    * that does not exist — and the port would have no way to declare a move that is not a split.
    *
    * Cut at a separator, like every other prefix question here: `p.internal` is under `p`, and
    * `p.internals` is not. */
  private def reaches(decl: String, reader: String): Boolean =
    val (d, r) = (packageOf(decl), packageOf(reader))
    d == r || covers(r, d)

  /** Which visibilities a given boundary move can strip. `private` is exact within a top-level
    * enclosure and irrelevant to a package move; `protected` carries Java's package half, which a
    * package move is exactly what removes. Package-private is not representable — see
    * [[boundaryOf]]. */
  private def restricted(f: Flags, cause: Cause): Boolean = cause match
    case Cause.PackageSplit   => f.isProtected
    case Cause.EnclosureSplit => f.isPrivate || f.isProtected

  // -------------------------------------------------------------------------
  // the rewrite
  // -------------------------------------------------------------------------

  override def run(program: Program): Program =
    require(didBind || !perTypeDeclared,
      "package-rename carries per-TYPE policy and was run WITHOUT being bound: `Pipeline.runTraced` " +
        "binds every `PolicyBound` phase before the first one runs, and a phase run unbound matches " +
        "nothing, silently (CLAUDE.md §1(b)).")
    // The no-op is the general path taken to its limit, not a branch around it: with no prefixes
    // nothing matches. The early return only spares the walk.
    if accepted.isEmpty then program
    else
      // FLATTENING FIRST, because it is the one change that is not a name: the `ClassDef` leaves
      // its enclosing body and becomes a unit, and everything after this reads `program.units`.
      val hoisted = if promote.isEmpty then program else hoist(program)
      val owned   = PackageRenameTransform.ownedSymbols(hoisted)
      // Prefixes the port DEMONSTRABLY declares types under — the ones that also cover at least one
      // OWNED symbol. Only these reach external symbols.
      //
      // That is what makes the relaxation safe without naming the JDK. A prefix under which this
      // program declares nothing is not this port's namespace, whatever the policy says, so
      // `Map("java" -> "jvm")` still cannot touch `java.lang.String`. A prefix under which it
      // declares 605 units plainly is, and the types it merely SUBSTITUTES there — parsed by
      // nobody, interned as external, replacement injected — move with it.
      //
      // A per-TYPE key is here by construction rather than by measurement: it was BOUND as
      // `Ownership.Owned`, which is that same question asked once, before the pipeline.
      val portOwnedPrefixes: Set[String] =
        val ownedNames = hoisted.symbols.all.iterator.filter(s => owned(s.id)).map(_.fullName).toList
        renames.keySet.filter(p => ownedNames.exists(n => PackageRenameTransform.longestMatch(n, Set(p)).isDefined)) ++
          acceptedTypes.keySet
      val table = hoisted.symbols.all.foldLeft(hoisted.symbols) { (t, s) =>
        // OWNED, or merely UNDER one of the renamed prefixes.
        //
        // The second half is not a weakening of the ownership rule, it is the rest of it. A type
        // this run never parsed can still be the PORT's: a `Substitutions.dropTypes` entry whose
        // replacement is injected Scala is interned as an external symbol, because nothing
        // translated it — yet it lives in the library's own namespace and its replacement file
        // moves with the rename. Renaming only owned symbols left every REFERENCE to such a type
        // behind: libGDX's `SharedLibraryLoader`, `Os` and the injected `AssetTypeRegistry` kept
        // `com.badlogic.gdx.*` while the files they name became `sge.*` — 8 errors, and the shape
        // that produced them is "a port that both substitutes and renames", i.e. every real port.
        //
        // It stays safe for the reason ownership was checking in the first place: a prefix here is
        // a namespace the PORT DECLARED as its own, and no JDK or stdlib type is under it. The
        // hostile case the ownership rule exists for — `Map("java" -> "jvm")` rewriting
        // `java.lang.String` — is now a policy that declares it owns `java`, which is a different
        // and self-inflicted error from a prefix accidentally colliding with one.
        if !(owned(s.id) || PackageRenameTransform.longestMatch(s.fullName, portOwnedPrefixes).isDefined) then t
        else
          PackageRenameTransform.longestMatch(s.fullName, accepted.keySet) match
            case scala.None => t
            case Some(from) =>
              val to      = accepted(from)
              val newFull = to + s.fullName.substring(from.length)
              val newName = if s.fullName == from then PackageRenameTransform.simpleNameOf(to) else s.name
              t.updated(s.copy(name = newName, fullName = newFull))
      }
      recordMoves(hoisted, table)
      // Trees and the xref are keyed by `SymId` and stay valid verbatim — that is the whole point
      // of renaming the symbol rather than the text. (The Pipeline rebuilds the xref anyway.)
      hoisted.rebuilt(symbols = table)

  /** Promote every accepted `flattenNestedTypes` entry to a top-level unit.
    *
    * Three things move together and the emitted file is wrong if any one of them does not: the
    * `ClassDef` leaves its enclosing body (or it renders nested), the symbol's OWNER becomes
    * `SymId.None` (or `Program.owned`'s climb still reaches the old enclosure and the emitter still
    * derives a nested path), and the FILE HEADER is carried across — a promoted type becomes its
    * own Scala file, each such file is a derived work in its own right, and CLAUDE.md §4.58 says
    * each carries the notice. */
  private def hoist(program: Program): Program =
    val ids = promote.keySet
    var out = List.empty[Tree.ClassDef]
    def strip(cd: Tree.ClassDef, header: List[Trivia]): Tree.ClassDef =
      cd.copy(body = cd.body.flatMap {
        case c: Tree.ClassDef if ids(c.symbol) =>
          out :+= strip(c, header).copy(unitLeading = header)
          Nil
        case c: Tree.ClassDef => List(strip(c, header))
        case s                => List(s)
      })
    val units   = program.units.map(u => strip(u, u.unitLeading))
    val symbols = ids.foldLeft(program.symbols)((t, id) =>
      program.symbolOf(id).fold(t)(s => t.updated(s.copy(owner = SymId.None))))
    program.rebuilt(units = units ++ out, symbols = symbols)

  /** DECISION PROVENANCE, one row per TYPE that moved — not per symbol. A member's namespace moved
    * because its type's did, so a per-symbol log would restate one decision thousands of times and
    * bury the ones that are not renames. The origin is the type's Java file, which is what an agent
    * holding an emitted FQN needs in order to find the upstream type at all: after this phase the
    * FQN no longer says where the file is (§4.57).
    *
    * `RenamedPackage` and `RenamedType` are separated by WHICH MAP matched, never by comparing the
    * two strings: a type rename whose target happens to keep the simple name is still a type
    * rename, and the reader's question is which policy entry to edit. */
  private def recordMoves(program: Program, table: SymbolTable): Unit =
    val units = program.units.map(_.symbol).toSet
    allClasses(program).foreach { cd =>
      for
        was <- program.symbolOf(cd.symbol)
        now <- table.get(cd.symbol) if now.fullName != was.fullName
        from <- PackageRenameTransform.longestMatch(was.fullName, accepted.keySet)
      do
        // ALTITUDE, and it is the same rule for both kinds (§4.575): one row per DECLARATION the
        // policy entry NAMES, never one per declaration the entry moved. A nested type moved
        // because its enclosure did, exactly as a member did, so recording it restates one decision
        // once per nested class and buries the decisions that are not renames.
        val isType = acceptedTypes.contains(from) && was.fullName == from
        if isType || units(cd.symbol) then
          record(Decision(
            kind       = if isType then Decision.Kind.RenamedType else Decision.Kind.RenamedPackage,
            subject    = cd.symbol,
            subjectFqn = was.fullName, // the UPSTREAM name: the one every policy key is written in
            detail     = Map("from" -> was.fullName, "to" -> now.fullName) ++
              (if isType then Map("why" -> whyOf(from)) else Map.empty),
            reason = Reason.Configured(name, s"$from -> ${accepted(from)}"),
            origin = cd.origin,
          ))
    }
    // …and the boundary the port DECLARED it was moving. One row per affected DECLARATION, which is
    // what §8.7's qualifier derivation reads: the recorded widening WINS, because the qualifier is
    // always the type's CURRENT emitted package and never a re-derivation from an upstream FQN.
    widenings.foreach { w =>
      record(Decision(
        kind       = Decision.Kind.WidenedVisibility,
        subject    = w.subject,
        subjectFqn = w.subjectFqn,
        detail = Map(
          "cause"  -> w.cause.slug,
          "type"   -> w.key,
          "reader" -> w.readerFqn,
          "why"    -> ("the port moves this type across an access boundary Java gave it, and " +
            "declared the move deliberate; this declaration therefore ships wider than Java wrote it"),
        ),
        reason = Reason.Configured(name, s"${w.key} -> ${accepted.getOrElse(w.key, w.key)}"),
        origin = w.origin,
      ))
    }

  private def whyOf(key: String): String =
    if flattenNestedTypes.contains(key) then
      "promoted out of its enclosing type: a consumer reads a nested type as a path-dependent one"
    else if subPackages.contains(key) then "nested into a sub-package the upstream did not have"
    else "renamed by policy: the upstream name is not one this port's consumers can use as it stands"

object PackageRenameTransform:

  private val TypeSetting  = "PackageRenameTransform(typeRenames)"
  private val SubSetting   = "PackageRenameTransform(subPackages)"
  private val FlatSetting  = "PackageRenameTransform(flattenNestedTypes)"
  private val AllowSetting = "PackageRenameTransform(allowPackageSplit)"

  /** WHAT a move takes away. Two, because they are two different Java rules and two different
    * §8.7 renderings — a package boundary is `protected[pkg]`/`private[pkg]`, a top-level enclosure
    * is `private[TopLevel]`. */
  enum Cause(val slug: String):
    case PackageSplit extends Cause("package-split")
    case EnclosureSplit extends Cause("enclosure-split")
    override def toString: String = slug

  /** One declaration a DECLARED move puts across a boundary Java gave it — the row §8.7's qualifier
    * derivation reads, carried as a value so a spec can assert it without a run directory. */
  final case class Widening(
      /** the `typeRenames`/`subPackages`/`flattenNestedTypes` key that moved the boundary. */
      key: String,
      cause: Cause,
      subject: SymId,
      /** the restricted declaration, by its UPSTREAM name — policy's namespace (§4.56). */
      subjectFqn: String,
      /** the declaration on the OTHER side, which is what makes this a crossing rather than a move. */
      readerFqn: String,
      origin: Origin,
  )

  private final case class Request(key: String, setting: String, value: Option[String])
  /** an accepted request, with BOTH namespaces of its destination: `upstreamTarget` is what the
    * policy author wrote (or what a simple name / a sub-package / a flatten derives), `emitted` is
    * that name after the PACKAGE renames — the composition §4.56 requires, performed once. */
  private final case class Move(request: Request, sid: SymId, upstreamTarget: String, emitted: String):
    def key: String = request.key

  /** `.` separates packages and the top-level type, `$` precedes a nested type, `#` a member. A
    * prefix may only be cut at one of the three. */
  private def isBoundary(c: Char): Boolean = c == '.' || c == '$' || c == '#'

  /** the last segment of a qualified name, at any of the three separators. */
  private def simpleNameOf(q: String): String =
    val i = q.lastIndexWhere(isBoundary)
    if i < 0 then q else q.substring(i + 1)

  /** the PACKAGE of a fully-qualified name — everything before the top-level type. Cut at a `.`
    * and only after the `$`/`#` tail is gone, so a nested type answers with its package and not
    * with its enclosure. */
  private[transform] def packageOf(fqn: String): String =
    val head = typeHeadOf(fqn)
    val i    = head.lastIndexOf('.')
    if i < 0 then "" else head.substring(0, i)

  /** the TOP-LEVEL type of a fully-qualified name — the part a Java `private` reaches throughout
    * (JLS 6.6.1), which is the boundary flattening moves. */
  private[transform] def typeHeadOf(fqn: String): String =
    val i = fqn.indexWhere(c => c == '$' || c == '#')
    if i < 0 then fqn else fqn.substring(0, i)

  /** does `fullName` sit under `prefix`, cut at a separator? (`com.foo` covers `com.foo.Bar` and
    * `com.foo` itself, never `com.foobar`.) */
  private def covers(fullName: String, prefix: String): Boolean =
    prefix.nonEmpty && fullName.startsWith(prefix) &&
      (fullName.length == prefix.length || isBoundary(fullName.charAt(prefix.length)))

  /** longest covering prefix — so a map holding both `com.foo` and `com.foo.bar` renames a symbol
    * under `com.foo.bar` by the more specific entry, as a namespace map is read everywhere else.
    * It is also what orders the two KINDS of entry against each other: a per-TYPE key is strictly
    * longer than any package prefix covering it, so a type entry always wins its own type. */
  private[transform] def longestMatch(fullName: String, prefixes: Set[String]): Option[String] =
    prefixes.filter(covers(fullName, _)).maxByOption(_.length)

  /** every class the program DECLARES, nested ones included — not only the top-level units. A
    * per-type rename names a nested type as readily as a top-level one, and a walk over `units`
    * alone would silently accept the key and record nothing. */
  private[transform] def allClasses(program: Program): List[Tree.ClassDef] =
    def all(cd: Tree.ClassDef): List[Tree.ClassDef] =
      cd :: cd.body.collect { case c: Tree.ClassDef => all(c) }.flatten
    program.units.flatMap(all)

  /** every symbol under `root` in the owner chain, `root` included. */
  private def under(program: Program, root: SymId): Set[SymId] =
    def rooted(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 && (s == root || program.symbolOf(s).exists(x => rooted(x.owner, fuel - 1)))
    program.symbols.all.collect { case s if rooted(s.id, 64) => s.id }.toSet

  /** The name `fqn` ends up with under `renames` — the same longest-prefix, cut-at-a-separator rule
    * the phase applies to a symbol, exposed for the code that has to say what an UPSTREAM name is
    * called in the EMITTED namespace without holding a `Program`.
    *
    * A run's POLICY is written upstream (§4.56: the rename runs last, so every other phase's keys
    * are upstream names), while everything observed about the running port — a stack frame, a
    * compiler path — is emitted. Anything joining the two has to translate one side, and doing it
    * by hand is where a bare `startsWith` gets written: `dropped-types.tsv` held upstream FQNs and
    * the correlator compared them against `sge.*` frames, so the derived expected-failure rule had
    * never once fired on a renaming port.
    *
    * Callers holding a RUN want `PackageRenameTransform.emittedName` on the instance the run
    * placed last: with per-TYPE policy the effective table is the ACCEPTED one, and an entry the
    * bind refused is not in it. This takes the table it is given and asks no questions. */
  def renamed(fqn: String, renames: Map[String, String]): String =
    longestMatch(fqn, renames.keySet) match
      case Some(from) => renames(from) + fqn.substring(from.length)
      case scala.None => fqn

  /** Symbols the program DECLARES, as opposed to externals the frontend interned on first
    * reference — `Program.owned`, which is where the one implementation now lives.
    *
    * It moved to the substrate when a SECOND kind of rule needed it: every phase that takes a
    * `RuleScope` must ask the same question before deciding a policy entry fired, and a §1(c) rule
    * in a consumer's repository compiles against `balticporter-api` alone. This name is kept because
    * it is what the phase's own doc and specs say. */
  def ownedSymbols(program: Program): Set[SymId] = program.owned

  /** What a rename map does to a program. Per CLAUDE.md §4.45 the answer names which of §1's three
    * kinds a discrepancy is: an unmatched prefix is always (b) — the phase is configured with a
    * namespace the program does not contain (a typo, a module that was not parsed, or this phase
    * running twice / out of order), never an engine bug.
    *
    * Run it BEFORE the phase to see what will move. Run it AFTER with the same map and every prefix
    * must come back unmatched with a zero count: any residue is an owned symbol the rename failed
    * to reach, which is the one defect that would otherwise surface as a package clause the
    * consumer cannot import.
    */
  final case class Report(matched: Map[String, Int], unmatched: List[String]):
    def render: String =
      val hits = matched.toList.sortBy(-_._2).map((p, n) => s"  $p -> $n owned symbol(s)")
      val miss =
        if unmatched.isEmpty then Nil
        else
          List("  unmatched prefixes (policy names a namespace this program does not declare — §1b, configure the phase):")
            ++ unmatched.sorted.map(p => s"    $p")
      (hits ++ miss).mkString("\n")

  def check(program: Program, renames: Map[String, String]): Report =
    val owned = ownedSymbols(program)
    val counts = program.symbols.all.toList
      .collect { case s if owned(s.id) => longestMatch(s.fullName, renames.keySet) }
      .flatten
      .groupBy(identity)
      .view
      .mapValues(_.size)
      .toMap
    Report(counts, (renames.keySet -- counts.keySet).toList)
