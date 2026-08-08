package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Turn a named JavaBean ACCESSOR PAIR into a Scala property — `def x` and `def x_=(v)` — and
  * rewrite every call site through it (DESIGN.md §8.5).
  *
  * ==Kind==
  * CLAUDE.md §1(b). The MECHANISM — detect the pair, rename the whole override component, drop the
  * getter's empty parameter clause, turn `o.getX()` into `o.x` and `o.setX(v)` into `o.x = v` — is a
  * fact about the two languages. WHICH members are properties is a fact about one library and
  * arrives as a constructor parameter. An empty map is a structural no-op: the phase returns its
  * input before building anything.
  *
  * ==Why the policy is an INCLUDE LIST and not a detected pattern==
  * Measured, not assumed. One upstream in this corpus emits 3,234 `get*`/`set*`/`is*` methods and
  * its hand port KEPT 1,375 of them (684 distinct names) against ~223 it converted — a ~14 %
  * conversion rate concentrated in three package families, with the SAME pair converted differently
  * in different types (one type's `opacity` is a computed `def`, another's is a `var`). A blanket
  * rule would rewrite some three thousand members a careful human deliberately left alone. So the
  * pairs map IS the policy, and it is deliberately the only knob: a second scope beside it would be
  * two homes for one decision (DESIGN.md §5.6, §8.5's "Rejected").
  *
  * ==Configuration==
  * {{{
  * { transform = "bean-properties"
  *   pairs { "com.foo.MapLayer#opacity" = "getOpacity/setOpacity"
  *           "com.foo.Map#properties"   = "getProperties"
  *           "com.foo.MapLayer#name"    = { accessors = "getName/setName", target = "var" } } }
  * }}}
  * The KEY is the emitted property, in the UPSTREAM namespace like every policy key (§4.56 — the
  * package rename runs last). The VALUE names the accessor(s) EXPLICITLY rather than deriving them
  * from the property name, for two reasons: a hand port's names are not always bean-derivable
  * (`getDragActor` → `currentDragActor`), and a `neverFired` report needs the accessors as DATA.
  *
  * ==The default target is `def x` / `def x_=(v: R): Unit`, bodies kept VERBATIM==
  * Collapsing a trivial pair into a public `var` is what a hand port usually writes and it deletes
  * the `$field` noise the emitter's `resolveMemberClashes` leaves behind — and it carries three
  * silent-correctness obligations (a `var` cannot be overridden; every call must provably route
  * through the pair; direct field access elsewhere must be equivalent), so it is OPT-IN PER ENTRY —
  * `target = "var"` / `"val"` — and refused whenever an obligation cannot be discharged
  * (DESIGN.md §8.5). Keeping the bodies is what makes a computed accessor and a side-effecting
  * setter correct by construction, and it is what an entry that says nothing still gets.
  *
  * ==Refusals — each counted, each with a spec==
  * A pair is applied whole or not at all, because a renamed getter with an unrenameable setter is
  * half a property:
  *
  *   - an accessor whose override component reaches an unparsed parent or a resolution root's
  *     declaration ([[OverrideGraph.Closure]]'s anchors);
  *   - a FLUENT setter, returning the declaring type — `o.x = v` is `Unit` and a chain has no
  *     assignment rendering;
  *   - a SET-ONLY entry: Scala's `o.x = v` sugar needs `x` in scope, and the assignment's left-hand
  *     side names the GETTER symbol, so with no getter there is nothing to put on an LHS;
  *   - a value-position accessor reference (`Tree.MethodRef`, or a `Select` that is not a call) —
  *     an eta-expanded `x_=` is not the SAM java saw;
  *   - a STATIC accessor (a companion property is out of scope);
  *   - a name collision the emitter's §4.55 passes will not resolve ([[MemberRenamer.OnCollision]]).
  *
  * '''NEVER INVENT A MEMBER.''' An entry naming an accessor that does not exist is a `NeverMatched`
  * finding from the binder, not a synthesis. The audit behind this design found the hand port
  * AUTHORING getters to complete pairs; that is authoring, permanently outside mechanism scope.
  *
  * ==Ordering==
  * BEFORE every retyping phase, so the descriptors it matches are java's own; the package rename is
  * last as always. `runsBefore` names the two engine phases whose names are static; the opaque-type
  * phase's name embeds its own FQN and cannot be named here, so a port that runs one places this
  * phase earlier in its `surface` list (`Pipeline.order` is stable in declaration order).
  *
  * ==This phase changes emitted SIGNATURES, so it is SHARED SURFACE==
  * It implements `SurfacePolicy` and its pairs live in the BASE manifest: a dependent resolves
  * against the base's Java and must see the same conversion, or the two ports each compile alone
  * and cannot compile together (§1.5).
  */
final class BeanPropertyTransform(pairs: Map[String, String] = Map.empty,
                                  targets: Map[String, BeanPropertyTransform.Target] = Map.empty,
                                  exposedFields: RuleScope = RuleScope.Only(Set.empty))
    extends Phase, PolicySource, SurfacePolicy, PolicyBound, IdiomPhase, Rewrite:

  def name: String = "bean-properties"

  override def runsBefore: Set[String] = Set("java-collections->scala", "package-rename")

  /** the lane that publishes what this phase CONSIDERED — one row per configured pair, `Converted`
    * or `Refused(guard)`, which IS the collapse's denominator ([[BeanCollapse]]). */
  def idiomKinds: Set[IdiomKind] = Set(IdiomKind.BeanCollapse)

  /** the lane that counts what a collapse MOVED and did not rewrite.
    *
    * Owed because the collapse retypes the surviving getter's `info`, which `Pipeline.runTraced`
    * SEES — and deliberately NOT `policy`: a declared key that never fired is a different residue
    * from a usage a rewrite left behind, and naming a lane that answers the first question about
    * the second is the shape `ENGINE-LIMITS.md` K2.5 measured. */
  def accountedBy: Set[String] = Set(IdiomCheck.Residue)

  /** THE K21-FACE-2 POLICY THIS PHASE MUST NOT CONTRADICT, threaded by the run.
    *
    * `PublicFieldAccessorTransform` PUTS java-bean names on a field so a reflective framework can
    * find them; the collapse TAKES them off. A port declaring both over one type is asking for two
    * opposite things, and the only place that can SEE both is the run that assembles the pipeline —
    * so it hands this phase the other phase's own `RuleScope` rather than the port declaring a
    * second copy of it (`PortRun.idiomPhases`). A copy, never a mutation: a phase is a value. */
  def withExposed(scope: RuleScope): BeanPropertyTransform =
    new BeanPropertyTransform(pairs, targets, scope)

  /** WHAT SHAPE this entry asked for — [[BeanPropertyTransform.Target.DefPair]] where it said
    * nothing, which is what every published config already says and what makes the second map a
    * compatible extension rather than a second policy home (DESIGN.md §8.5). */
  def targetOf(key: String): BeanPropertyTransform.Target =
    targets.getOrElse(key, BeanPropertyTransform.Target.DefPair)

  /** the pairs, sorted and rendered — two modules that agree must compare equal (§1.5).
    *
    * '''The TARGET is part of it, and it is one line that is easy to miss.''' Two configurations
    * that name the same accessors and ask for different SHAPES emit different signatures; rendered
    * without the target they compare EQUAL, `SurfaceMissing` cannot see the difference, and a
    * same-name pair can be neither compared nor composed — which is `ENGINE-LIMITS.md` CT9's
    * recorded failure exactly. Rendered UNCONDITIONALLY rather than only where a target was
    * declared: a fingerprint whose grammar depends on the value is one whose collisions depend on
    * it too. */
  def surfaceFingerprint: String =
    pairs.toList.sorted.map((k, v) => s"$k=$v>${targetOf(k).config}").mkString(",")

  /** the port's own pairs table, verbatim — the policy this phase was constructed WITH.
    *
    * Exposed for the one question the phase itself cannot ask: whether the shape this run derived
    * for a BASE's pair is the shape the base PUBLISHED. That comparison needs the base's port map
    * and the run's own idiom log, and neither is a thing a phase holds — it belongs to `PortRun`
    * (`PortRun.collapseDivergence`). What a phase must not do is answer it twice, so this hands over
    * the CONFIGURATION and nothing else: the verdict still comes from the idiom log the phase filled
    * (§4.6, and `ENGINE-LIMITS.md` K2.5's measured shape of two answers to one question). */
  def pairsTable: Map[String, String] = pairs


  // ---- policy, bound before the pipeline starts ---------------------------------------------

  private var parsed: List[BeanPropertyTransform.Entry] = Nil
  private var bound: Map[String, List[PolicyBinder.Hit]] = Map.empty
  private var records: List[PolicyBinder.Record]         = Nil
  private var ownFindings: List[PolicyFinding]           = Nil

  def bindPolicy(binder: PolicyBinder): Unit =
    val (entries, malformed) = BeanPropertyTransform.parse(pairs)
    parsed      = entries
    ownFindings = malformed
    bound = entries.flatMap { e =>
      // The accessor key is RENDERED from a `MemberKey`, never spliced from the owner and the name:
      // §8.1's convention, and the one the transform-package lint enforces. It is BARE on purpose —
      // a bare key names every overload, and only the one with the right shape converts (§8.5:
      // `getX(int)` stays while `getX()` moves).
      e.accessors.map { a =>
        val key = MemberKey(e.owner, a).render
        key -> binder.bindMembers(name, s"BeanPropertyTransform(pairs) `${e.key}`", key)
          .toOption.getOrElse(Nil)
      }
    }.toMap
    records = binder.recordsFor(name)

  /** the never-fired half (from the BINDING, so it is complete whether or not this phase ran) plus
    * this phase's own malformed entries and counted refusals. */
  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(ownFindings)

  // ---- the run ------------------------------------------------------------------------------

  /** EVERY declaration of the getter component -> the property, and the same for the setter.
    *
    * The whole COMPONENT and not the named accessor, which is the invariant applied to the SHAPE
    * change rather than only to the name: an interface whose `getX()` became `def x` and an
    * implementor that kept `def x()` do not override each other, which is an error at exactly the
    * declaration the policy was written to move. Measured on this phase's own end-to-end fixture —
    * the rename was complete and the arity edit reached one of three declarations. */
  private var getters: Map[SymId, BeanPropertyTransform.Property] = Map.empty
  private var setters: Map[SymId, BeanPropertyTransform.Property] = Map.empty

  /** a setter declaration -> the getter declaration whose NAME its assignment's LHS must carry —
    * the one in the SAME owner where there is one, so the emitted `this.x = v` names a member of
    * the class it is written in. */
  private var lhsOf: Map[SymId, SymId] = Map.empty

  /** the collapses this run applied — decided once, applied once, reported once. */
  private var collapsed: List[BeanPropertyTransform.Collapsed] = Nil

  override def run(program: Program): Program =
    getters = Map.empty; setters = Map.empty; lhsOf = Map.empty; collapsed = Nil
    if pairs.isEmpty then return program

    val refusals = collection.mutable.ListBuffer.empty[PolicyFinding]
    def refuse(e: BeanPropertyTransform.Entry, why: String): scala.None.type =
      refusals += PolicyFinding(name, s"BeanPropertyTransform(pairs) `${e.key}`", e.key,
        PolicyIssue.Unverifiable, why)
      record(Decision(
        kind       = Decision.Kind.ScopedOut,
        subject    = SymId.None,
        subjectFqn = e.key,
        detail     = Map(
          "refused"  -> "bean-property",
          "property" -> e.property,
          "why"      -> why,
        ),
        reason = Reason.Configured(name, e.key),
        origin = Origin.synthetic,
      ))
      scala.None

    val graph = OverrideGraph.build(program)

    // ---- 1. shape validation, per entry, on the PRE-rename program -------------------------
    val properties = parsed.flatMap(e => validate(program, graph, e, refuse))

    // ---- 2. the rename: both accessors of a pair in ONE group, so half a property is impossible
    val requests = properties.flatMap { p =>
      MemberRenamer.Request(p.getter, p.property, Reason.Configured(name, p.key), p.key, p.key) ::
        p.setter.map(s =>
          MemberRenamer.Request(s, p.property + "_=", Reason.Configured(name, p.key), p.key, p.key)).toList
    }
    val (renamed, renameRefusals) = MemberRenamer.rename(
      program, graph, requests, MemberRenamer.OnCollision.DeferToEmitter, decisions)

    val refusedKeys = renameRefusals.map(_.request.key).toSet
    renameRefusals.map(_.request.key).distinct.foreach { k =>
      val why = renameRefusals.find(_.request.key == k).map(_.why).getOrElse("refused")
      parsed.find(_.key == k).foreach(e => refuse(e, why))
    }
    ownFindings = ownFindings.filterNot(f => refusals.exists(_.key == f.key)) ++ refusals.toList

    val applied = properties.filterNot(p => refusedKeys.contains(p.key))

    // ---- 3. THE COLLAPSE DECISION, per entry, on the PRE-rename program ---------------------
    // A rename moves NAMES and every §8.5 obligation is about a SHAPE, so the two questions are
    // independent and the cheaper program answers this one. Filed for EVERY parsed entry, including
    // the ones the def-pair path refused, because `idiom(refused)` is a DENOMINATOR: a run that
    // reported only the entries it could have collapsed would answer "how many did you convert" and
    // nothing at all about the population that number is drawn from.
    lazy val written = BeanCollapse.writtenSymbols(program)
    val verdicts = parsed.map { e =>
      e -> (applied.find(_.key == e.key) match
        case scala.None =>
          BeanCollapse.Verdict.Refuse(BeanCollapse.Guard.PairRefused)
        case Some(prop) =>
          BeanCollapse.decide(program, graph, prop, targetOf(e.key), exposedFields, written))
    }
    verdicts.foreach { (e, v) =>
      val at   = applied.find(_.key == e.key)
        .map(p => Decision.originOf(program, p.getter)).getOrElse(Origin.synthetic)
      val what = s"property `${e.property}` via `${e.value}`"
      val verdict = v match
        case BeanCollapse.Verdict.Collapse(_) => IdiomVerdict.Converted
        case BeanCollapse.Verdict.Refuse(g)   => IdiomVerdict.Refused(g.toString, g.why)
      consider(IdiomCandidate(IdiomKind.BeanCollapse, verdict, e.key, what, at))
    }
    collapsed = verdicts.collect { case (e, BeanCollapse.Verdict.Collapse(f)) =>
      val p = applied.find(_.key == e.key).get
      BeanPropertyTransform.Collapsed(e.key, e.value, p.property, p.getter, p.setter, f,
        targetOf(e.key),
        triviaOf(program, p.getter) ++ p.setter.toList.flatMap(s => triviaOf(program, s)))
    }

    if applied.isEmpty then return renamed

    getters = applied.flatMap(p => p.getterMembers.map(_ -> p)).toMap
    setters = applied.flatMap(p => p.setterMembers.map(_ -> p)).toMap
    lhsOf = applied.flatMap { p =>
      val byOwner = p.getterMembers.iterator.map(g => renamed.symbolOf(g).map(_.owner) -> g).toMap
      p.setterMembers.map(s => s -> byOwner.getOrElse(renamed.symbolOf(s).map(_.owner), p.getter))
    }.toMap

    // ---- 4. the tree edits: the getter's empty parameter clause, and every call site ---------
    // A `paramss` of `List(Nil)` renders `()` and `Nil` renders nothing, so this is what turns a
    // java nilary method into a scala PARAMETERLESS one — and it is why the call sites must move in
    // the same pass: `o.x()` on a parameterless def is a type error.
    //
    // The symbol's `info` is deliberately LEFT as `MethodType(Nil, R)`: a scala parameterless `def`
    // IS a method, its descriptor is still the empty one, and every arity reader in the engine reads
    // `paramss`. Retyping it would make `PolicyBinder.isExecutable` and `OverrideGraph.signatureOf`
    // stop recognising the member they had just renamed.
    val paired =
      given Program = renamed
      renamed.rebuilt(units = renamed.units.map(u => StandardTraversal.mapClassDef(this, u)))

    // ---- 5. …and the COLLAPSE, applied last, over what the def-pair path has already moved ---
    if collapsed.isEmpty then paired else applyCollapse(paired)

  override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
    if getters.contains(t.symbol) then t.copy(paramss = Nil) else t

  /** `o.getX()` → `o.x`, `o.setX(v)` → `o.x = v`.
    *
    * BOTTOM-UP, which is what makes the compound form `o.setX(o.getX() + 1)` need no special case:
    * the inner read has already become a `Select` by the time the outer write is rewritten.
    *
    * The assignment's LEFT-HAND SIDE names the GETTER symbol, not the setter — the emitter renders
    * `Assign` as `lhs = rhs`, so `recv.x = v` is what reaches scalac, and scalac desugars it to
    * `x_=`. That is also the structural reason a set-only entry is refused: with no getter there is
    * nothing to put on an LHS.
    */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    if getters.contains(t.method) && t.args.isEmpty then
      receiverOf(t.fun) match
        case Some(scala.None)    => Tree.Ident(t.method, t.tpe, t.origin)
        case Some(Some(qual))    => Tree.Select(qual, t.method, t.tpe, t.origin)
        case scala.None          => t
    else if setters.contains(t.method) && t.args.sizeIs == 1 then
      val g   = lhsOf.getOrElse(t.method, setters(t.method).getter)
      val res = summon[Program].symbolOf(g).map(_.info).getOrElse(TypeRepr.NoType) match
        case TypeRepr.MethodType(_, r, _) => r
        case other                        => other
      receiverOf(t.fun) match
        case Some(scala.None) => Tree.Assign(Tree.Ident(g, res, t.origin), t.args.head, t.tpe, t.origin)
        case Some(Some(qual)) => Tree.Assign(Tree.Select(qual, g, res, t.origin), t.args.head, t.tpe, t.origin)
        case scala.None       => t
    else t

  /** the call's receiver: `Some(None)` for a bare call (no qualifier), `Some(Some(q))` for a
    * qualified one, `None` for a shape this phase does not rewrite — which `validate` has already
    * refused the pair for, so it cannot be reached for an APPLIED property. */
  private def receiverOf(fun: Term): Option[Option[Term]] = fun match
    case _: Tree.Ident            => Some(scala.None)
    case Tree.Select(q, _, _, _)  => Some(Some(q))
    case _                        => scala.None

  // ---- the collapse ---------------------------------------------------------------------------

  /** an accessor's own comments, harvested BEFORE its declaration is deleted.
    *
    * §4.58: a comment whose construct the emission CONSUMES needs a home, and the honest one here is
    * the node that SURVIVES — the getter's javadoc was describing the property. Taken in DECLARATION
    * ORDER (getter, then setter) and appended after the field's own, so the emitted file reads the
    * way the java did and nothing is lost. */
  private def triviaOf(p: Program, m: SymId): List[Trivia] =
    p.definitionOf(m).collect { case d: Tree.DefDef => d.leading }.getOrElse(Nil)

  /** APPLY the collapses: the getter's SYMBOL becomes the property's storage, the field's `ValDef`
    * becomes the property's declaration, and both accessors' declarations go.
    *
    * ==Why the GETTER survives and the field does not==
    * The two orders emit the same text and only one of them is visible to anything.
    * `Pipeline.runTraced` derives what a phase moved by comparing each owned symbol's `info` ACROSS
    * the phase, so retyping the getter is a `Patch.retyped` row while dropping the pair and MINTING a
    * `var` is not: the dropped symbol has no post-phase `info`, the minted one has no pre-phase one,
    * and the pair cancels to nothing. Minted, this phase would owe no `accountedBy` lane at all —
    * `rewrite-callsites` would report 0, `idiom(residue)` would report 0, and every usage the phase
    * failed to rewrite would be invisible to the one instrument built to find it. The surviving
    * symbol also carries the `usagesOf` edges the call-site rewrite has already moved, the `srcmap`
    * row that attributes an error to a java line, and the `port-map.tsv` member row a dependent's
    * `base-surface` compares against.
    *
    * ==…and why the FIELD's NODE is the one that stays in the body==
    * A scala class body IS its constructor, so the property's POSITION among the field initialisers
    * and instance-initialiser blocks is the whole of what the class computes (§4.55, JLS 12.5 step
    * 4). The field is where java put that value; the getter is somewhere else entirely. So the node
    * keeps its place, its initialiser and its declared type, and only its SYMBOL changes.
    *
    * ==The flags are a SPLIT, and it is the one §8.5 states==
    * VISIBILITY comes from the ACCESSORS — the field is the implementation and the pair is the
    * surface, so taking the field's `private` would silently narrow the port's API with a green
    * compile. Everything else comes from the FIELD, whose modifiers are facts about the slot java
    * allocated. `isMutable` is neither: it is the SHAPE the port asked for, which the guards are
    * what make sound (`Var` was refused without a setter, `Val` without storage nothing writes).
    * Read off the field's own `isMutable` instead, a get-only property over an ordinary non-`final`
    * java field would emit a public `var` and publish a writer java never had.
    */
  private def applyCollapse(p: Program): Program =
    val byGetter = collapsed.map(c => c.getter -> c).toMap
    val syms = p.symbols.all.map { s =>
      byGetter.get(s.id).flatMap(c => p.symbolOf(c.field).map(f => (c, f))) match
        case Some((c, f)) =>
          s.copy(
            info = f.info,
            // a field has no parameter spelling; leaving the getter's would tell every overload
            // reader that this member is still executable (§8.1).
            descriptor = scala.None,
            flags = f.flags.copy(
              isPrivate        = s.flags.isPrivate,
              isProtected      = s.flags.isProtected,
              isPackagePrivate = s.flags.isPackagePrivate,
              isOverride       = false,
              isMutable        = c.target == BeanPropertyTransform.Target.Var,
            ))
        case scala.None => s
    }
    val retyped = p.rebuilt(symbols = SymbolTable(syms))
    val out =
      given Program = retyped
      retyped.rebuilt(units = retyped.units.map(u =>
        StandardTraversal.mapClassDef(new BeanPropertyTransform.Collapser(collapsed), u)))
    val indexed = out.rebuilt(xref = Xref.build(out.units))
    recordCollapse(indexed)
    indexed

  /** the DECISION per collapsed property, and the RESIDUE the shape cannot rule out.
    *
    * One decision per surviving declaration (§5.1), carrying the one thing the emitted text cannot
    * say: the JVM METHOD NAMES moved. `getName()`/`setName()` became `name()`/`name_$eq()`, which is
    * invisible to a compiler, to every count and to every test, and visible to exactly the reflective
    * reader `ENGINE-LIMITS.md` K21 is about.
    */
  private def recordCollapse(p: Program): Unit =
    collapsed.foreach { c =>
      record(Decision(
        kind       = Decision.Kind.CollapsedProperty,
        subject    = c.getter,
        subjectFqn = Decision.fqnOf(p, c.getter, c.key),
        detail = Map(
          "form"  -> c.target.config,
          "from"  -> c.accessors,
          "to"    -> c.property,
          "field" -> Decision.fqnOf(p, c.field, "?"),
          "was"   -> c.accessors.split('/').map(_.trim + "()").mkString(" "),
          "why"   -> ("java's bean pair over a trivial field and a scala property are the same " +
            "value with two spellings, and the port publishes the scala one — but the JVM METHOD " +
            "NAMES move with it, so a framework that discovers `getX`/`setX` reflectively finds " +
            "neither" + finality(c)),
        ),
        reason = Reason.Configured(name, c.key),
        origin = Decision.originOf(p, c.getter),
      ))
      // …and the RESIDUE. Delta 2 of the enumeration is made impossible by the SHAPE — the def-pair
      // path refuses a pair with an unrewritable reference and has already moved every rewritable
      // one — so these three counts SHOULD be zero, and that is exactly why they are taken: a claim
      // a phase makes about itself and never checks is the shape `ENGINE-LIMITS.md` K2.5 measured.
      c.setter.foreach(s => residue(p, c, s, "a reference to the setter this collapse deleted"))
      residue(p, c, c.field, "a reference to the backing field the property replaced")
      residue(p, c, c.getter, "a CALL of a member that is now a `var`", _.kind == UsageKind.Call)
    }

  /** the SECOND reflective fact, and it belongs only to `target = "val"`.
    *
    * Guard 5 of the §8.5 enumeration records that the JVM METHOD NAMES move. A `val` moves one more
    * thing, in the other direction and at the FIELD: scalac emits a `val`'s backing field `final`,
    * and java's was not — `MutableStorage` requires a declaration initialiser and no assignment IN
    * THIS PROGRAM, which is a fact about the program and never about the `final` keyword (that is
    * deliberate, and it is what keeps the guard from declining most of the get-only population).
    *
    * So a reflective WRITER that worked against the java field — `setAccessible(true)` and
    * `Field.set`, which every deserialiser and every test fixture reaches for — does not work
    * against the emitted one. It is `ENGINE-LIMITS.md` K21's shape at the storage rather than at the
    * accessor: no guard can reach it (every reference to an object can reach its `Class`), the port
    * compiles, no count moves, and the only reader who can act on it is §4.45's, at this line.
    *
    * Recorded ONLY for `val`, because a `var`'s field is not final and the sentence would then be
    * false — a note that says something untrue about the code below it is worse than no note. */
  private def finality(c: BeanPropertyTransform.Collapsed): String =
    if c.target != BeanPropertyTransform.Target.Val then ""
    else "; and this one is a `val`, so its BACKING FIELD is `final` on the JVM where java's was " +
      "not — a reflective writer (`setAccessible` + `Field.set`) that worked against the java " +
      "field does not work against this one"

  private def residue(p: Program, c: BeanPropertyTransform.Collapsed, sym: SymId, what: String,
                      keep: Usage => Boolean = _ => true): Unit =
    p.usages(sym).filter(keep).map(_.enclosing).distinct.sortBy(_.raw).foreach { encl =>
      consider(IdiomCandidate(IdiomKind.BeanCollapse, IdiomVerdict.Residue(what),
        Decision.fqnOf(p, encl, c.key), s"property `${c.property}`", Decision.originOf(p, encl)))
    }

  // ---- validation ---------------------------------------------------------------------------

  private def validate(p: Program, graph: OverrideGraph, e: BeanPropertyTransform.Entry,
                       refuse: (BeanPropertyTransform.Entry, String) => scala.None.type)
      : Option[BeanPropertyTransform.Property] =

    def defOf(s: SymId): Option[Tree.DefDef] = p.definitionOf(s).collect { case d: Tree.DefDef => d }
    def hits(a: String): List[SymId] = bound.getOrElse(MemberKey(e.owner, a).render, Nil).flatMap(_.sym)

    /** the java NILARY overload, and only it: `getX(int)` stays where `getX()` moves (D1 — match by
      * descriptor, never by name-and-guess). */
    def nilary(cands: List[SymId]): List[SymId] =
      cands.filter(s => defOf(s).exists(_.paramss.forall(_.isEmpty)))
    def unary(cands: List[SymId]): List[SymId] =
      cands.filter(s => defOf(s).exists(d => d.paramss.map(_.size).sum == 1))

    def isStatic(s: SymId): Boolean = p.symbolOf(s).exists(_.flags.isStatic)

    /** every recorded CALL of every declaration in the COMPONENT is an ordinary `Apply` of the
      * right arity through a receiver this phase can move. A `Tree.MethodRef` is a method VALUE —
      * an eta-expanded `x_=` is not the SAM java saw — and anything else is a shape the rewrite has
      * no rendering for. Asked of the whole component and not of the named accessor, for the same
      * reason the arity edit is: a call through an IMPLEMENTOR's symbol is a different symbol from
      * a call through the interface's, and only one of them is what the entry named. Read from the
      * xref, so an occurrence inside an anonymous body counts like any other. */
    def callsAreRewritable(component: Set[SymId], arity: Int): Boolean =
      component.forall(s => p.usages(s).forall {
        case Usage(UsageKind.Call, a: Tree.Apply, _) =>
          a.args.sizeIs == arity && receiverOf(a.fun).isDefined
        case Usage(UsageKind.Call, _, _) => false // a `MethodRef` — a method VALUE
        case Usage(UsageKind.TermRef, _, _) => false // a bare `Select`/`Ident` of the method
        case _ => true
      })

    e.setterName match
      case scala.None if e.accessors.sizeIs != 1 =>
        refuse(e, s"`${e.value}` names ${e.accessors.size} accessors; the value is `getter` or " +
          "`getter/setter`")
      case _ =>
        nilary(hits(e.getterName)) match
          case Nil =>
            // the binder already reports a key that named nothing; this is the narrower failure —
            // it named something, and nothing it named has the shape of a getter.
            if hits(e.getterName).isEmpty then scala.None
            else refuse(e, s"`${e.getterName}` has no NILARY overload, so there is no getter to " +
              "convert (an overload with parameters is left exactly as it is)")
          case g :: rest if rest.nonEmpty =>
            refuse(e, s"`${e.getterName}` has ${rest.size + 1} nilary declarations in this program " +
              "and a property has one getter")
          case g :: Nil =>
            val gd    = defOf(g).get
            val gComp = graph.closureOf(g).members
            if isStatic(g) then
              refuse(e, s"`${e.getterName}` is STATIC; a companion property is out of scope for " +
                "this phase (v1), so the accessor is left as it is")
            else if isVoid(p, gd.returnTpt.tpe) then
              refuse(e, s"`${e.getterName}` returns void, so it is not a getter")
            else if !callsAreRewritable(gComp, 0) then
              refuse(e, s"`${e.getterName}` is referenced in VALUE position (a method reference or " +
                "a bare selection); an eta-expanded accessor is not the SAM java saw")
            else
              e.setterName match
                case scala.None =>
                  Some(BeanPropertyTransform.Property(e.key, e.property, g, scala.None, gComp, Set.empty))
                case Some(sn) =>
                  val cands = unary(hits(sn)).filter(s => paramHead(p, s) == headOf(gd.returnTpt.tpe))
                  cands match
                    case Nil =>
                      if hits(sn).isEmpty then scala.None
                      else refuse(e, s"`$sn` has no single-parameter overload taking " +
                        s"`${e.getterName}`'s return type, so the two are not a pair")
                    case s :: rest if rest.nonEmpty =>
                      refuse(e, s"`$sn` has ${rest.size + 1} declarations matching the getter's type")
                    case s :: Nil =>
                      val sd = defOf(s).get
                      if isStatic(s) then
                        refuse(e, s"`$sn` is STATIC; a companion property is out of scope (v1)")
                      else if headOf(sd.returnTpt.tpe).exists(h => ownerTypeOf(p, s).contains(h)) then
                        refuse(e, s"`$sn` is FLUENT — it returns its own declaring type for " +
                          "chaining, and `o.x = v` is Unit, so a chain has no assignment rendering")
                      else if !isVoid(p, sd.returnTpt.tpe) then
                        refuse(e, s"`$sn` returns a value, and an assignment discards it")
                      else if !callsAreRewritable(graph.closureOf(s).members, 1) then
                        refuse(e, s"`$sn` is referenced in VALUE position; an eta-expanded `x_=` is " +
                          "not the SAM java saw")
                      else Some(BeanPropertyTransform.Property(e.key, e.property, g, Some(s),
                        gComp, graph.closureOf(s).members))

  private def ownerTypeOf(p: Program, m: SymId): Option[SymId] = p.symbolOf(m).map(_.owner)

  private def paramHead(p: Program, m: SymId): Option[SymId] =
    p.definitionOf(m).collect { case d: Tree.DefDef => d }
      .flatMap(_.paramss.flatten.headOption).flatMap(v => headOf(v.tpt.tpe))

  private def headOf(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headOf(tc)
    case _                           => scala.None

  /** Java `void`, as the frontend writes it. `scala.Unit` is the ENGINE's own rendering of it — an
    * external this program never declares and has no symbol to bind — so the question "did the
    * frontend write void here" has no other instrument. See `PolicyKeyLintSpec`'s allow-list. */
  private def isVoid(p: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => p.symbolOf(s).exists(_.fullName == "scala.Unit")
    case _                      => false

object BeanPropertyTransform:

  /** WHAT SHAPE an entry asks for.
    *
    * Three cases and not a boolean, because "collapse this" is two different declarations with two
    * different obligations: a `var` needs a setter to be a faithful surface and a `val` needs the
    * storage to be written once. Naming them separately is what lets the phase REFUSE a mismatch
    * (a `val` target on a pair with a setter is a contradiction the port can be told about) rather
    * than pick one and be silently right or wrong.
    *
    * [[DefPair]] is the default, so every existing port and every existing config is byte-identical
    * by construction — §1(b)'s "an empty parameter is a no-op" met at the ENTRY level, which is the
    * right granularity for a per-key policy.
    */
  enum Target:
    /** `def x` / `def x_=(v: R): Unit`, bodies verbatim — the phase's original and only shape. */
    case DefPair
    /** a public `var x`: the accessors go, and the backing field becomes the property. */
    case Var
    /** a public `val x`: a get-only entry over storage java wrote once. */
    case Val

    /** the spelling a `.conf` writes. Derived nowhere else, so the config grammar and the enum
      * cannot drift. */
    def config: String = this match
      case DefPair => "def-pair"
      case Var     => "var"
      case Val     => "val"

  object Target:
    /** the CLOSED set a `target = …` key is read against — `ConfigView.enumerated`'s loud door,
      * which names every alternative in the error rather than falling back to the default. */
    val byConfigName: Map[String, Target] = values.map(t => t.config -> t).toMap

  /** ONE COLLAPSED PROPERTY: the policy key, the accessors as the entry spelled them, the emitted
    * name, the surviving symbol, the setter that goes, the field that becomes the storage, the shape
    * the port asked for, and the comments the deleted declarations were carrying. */
  final case class Collapsed(key: String, accessors: String, property: String,
                             getter: SymId, setter: Option[SymId], field: SymId,
                             target: Target, trivia: List[Trivia])

  /** THE TREE EDIT — one traversal, bottom-up, so every reference has been re-pointed by the time
    * the owning class's body is rebuilt.
    *
    * A `StandardTraversal` phase and not a private recursion, for §3's reason: the references this
    * has to move are wherever java wrote them, an anonymous body and a method-local class included.
    */
  private[transform] final class Collapser(cs: List[Collapsed]) extends Phase:
    def name: String = "bean-properties/collapse"

    private val byField = cs.map(c => c.field -> c).toMap
    private val gone    = cs.flatMap(c => c.getter :: c.setter.toList).toSet

    override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
      val touches = t.body.exists {
        case v: Tree.ValDef => byField.contains(v.symbol)
        case d: Tree.DefDef => gone.contains(d.symbol)
        case _              => false
      }
      if !touches then t
      else t.copy(body = t.body.flatMap {
        case v: Tree.ValDef if byField.contains(v.symbol) =>
          val c = byField(v.symbol)
          List(v.copy(symbol = c.getter, leading = v.leading ++ c.trivia))
        case d: Tree.DefDef if gone.contains(d.symbol) => Nil
        case other                                     => List(other)
      })

    /** every read and write of the backing field now names the PROPERTY.
      *
      * Exact, and it is obligation 3 of the enumeration cashed in: the trivial-body guard has
      * already established that the getter is `return this.f` and the setter `this.f = v`, so
      * `this.f` and `this.x` are the same storage — a plain scala `var` compiles to the same field
      * access the java field did. */
    override def transformIdent(t: Tree.Ident)(using Program): Term =
      byField.get(t.sym).map(c => t.copy(sym = c.getter)).getOrElse(t)

    override def transformSelect(t: Tree.Select)(using Program): Term =
      byField.get(t.sym).map(c => t.copy(sym = c.getter)).getOrElse(t)

  /** One applied property: the policy key it came from, the emitted name, the accessors the entry
    * NAMED, and every declaration of each accessor's override COMPONENT — which is what the arity
    * edit and the call-site rewrite range over, because a call through an implementor's symbol is a
    * different symbol from a call through the interface's. */
  final case class Property(key: String, property: String, getter: SymId, setter: Option[SymId],
                            getterMembers: Set[SymId], setterMembers: Set[SymId])

  /** one declared entry, parsed. `key` is kept verbatim — it is the string an agent edits. */
  final case class Entry(key: String, value: String, owner: String, property: String,
                         getterName: String, setterName: Option[String]):
    def accessors: List[String] = getterName :: setterName.toList

  /** Parse the declared map. Everything outside the grammar is a `Malformed` finding rather than a
    * best-effort reading: a lenient parse produces a key that binds to the wrong member and says
    * nothing (`MemberKey.parse`'s argument, applied to this phase's value half too). */
  def parse(pairs: Map[String, String]): (List[Entry], List[PolicyFinding]) =
    val findings = collection.mutable.ListBuffer.empty[PolicyFinding]
    def bad(k: String, what: String): scala.None.type =
      findings += PolicyFinding("bean-properties", s"BeanPropertyTransform(pairs) `$k`", k,
        PolicyIssue.Malformed, what)
      scala.None
    val entries = pairs.toList.sortBy(_._1).flatMap { (k, v) =>
      MemberKey.parse(k) match
        case Left(m) => bad(k, m.what)
        case Right(mk) if mk.descriptor.isDefined =>
          bad(k, "the KEY names the emitted PROPERTY, which has no parameter list — write " +
            "`owner#property`, and name the accessors in the value")
        case Right(mk) =>
          v.split("/", -1).toList.map(_.trim) match
            case List(g) if g.nonEmpty      => Some(Entry(k, v, mk.owner, mk.name, g, scala.None))
            case List(g, s) if g.nonEmpty && s.nonEmpty =>
              Some(Entry(k, v, mk.owner, mk.name, g, Some(s)))
            case List(g) if g.isEmpty       => bad(k, "the value is empty — it names the accessor(s)")
            case List(g, _) if g.isEmpty    =>
              bad(k, "a SET-ONLY entry (`/setX`) is refused: scala's `o.x = v` sugar needs `x` in " +
                "scope, and the assignment's left-hand side names the GETTER symbol — with no " +
                "getter there is nothing to put on an LHS. Name both accessors, or neither")
            case _ =>
              bad(k, s"`$v` is not `getter` or `getter/setter` — a value with more than one `/` " +
                "names more accessors than a property has")
    }
    (entries, findings.toList)
