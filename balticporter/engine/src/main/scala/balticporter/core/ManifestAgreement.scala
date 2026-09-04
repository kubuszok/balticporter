package balticporter.core

import balticporter.tir.MemberKey

/** Checks whether this module's policy agrees with its declared bases.
  *
  * Two layers: '''static''' (manifest vs manifest, before parsing) and '''dynamic''' (the base's
  * published [[PortMap]] or re-derived policy vs what this run modelled of the shared surface).
  * Prefers the published map; falls back to re-derivation when it is stale or absent. */
object ManifestAgreement:

  /** One type of the shared surface, as this run modelled it. Pure (no paths). */
  final case class SharedType(
      /** the FQN the base module's Java declares, before any rename. */
      upstreamFqn: String,
      /** the FQN this run's symbol carries after every phase, renames included. */
      emittedFqn: String,
      /** did this run tag it [[Substituted]]? */
      substituted: Boolean,
  )

  /** A base module's manifest and its published map (if usable).
    * @param map      published [[PortMap]], if fresh. Stale maps are refused (`stale` carries why).
    * @param jdk      `(published, running)` on JDK mismatch -- fatal, fallback cannot help. */
  final case class BasePort(
      manifest: PortManifest,
      map: Option[PortMap.Map0] = scala.None,
      source: String = "",
      stale: List[String] = Nil,
      unverified: List[String] = Nil,
      jdk: Option[(String, String)] = scala.None,
  ):
    def name: String = manifest.name
    /** Does this base declare any shared-surface policy? An empty manifest = "not a ported module". */
    def declaresPolicy: Boolean = manifest.declaresPolicy

  enum Kind(val fatal: Boolean, val classification: String):
    /** the base does not translate this type; this module does. */
    case MissingDrop extends Kind(true,
      "§1(b) PER-LIBRARY: the base module does not translate this type mechanically, so no class " +
        "exists at that name in its output. Add the key to this module's manifest, or inherit it " +
        "with `base.extendedBy(...)`.")
    /** this module drops a member of the shared surface the base keeps. */
    case ExtraDrop extends Kind(true,
      "§1(b) PER-LIBRARY: this module drops something the base module EMITS, so the two ports " +
        "disagree about what the shared surface contains. Remove the key here, or add it to the base.")
    /** a namespace both modules see is moved to two different places. */
    case RenameDivergence extends Kind(true,
      "§1(b) PER-LIBRARY: the shared namespace is renamed differently in the two modules, so this " +
        "module's references name a package the base never emits. Inherit the base's rename map.")
    /** a TYPE both modules see is moved — renamed, sub-packaged or flattened — differently.
      * Separate from [[RenameDivergence]]: a package divergence moves a whole namespace, visible
      * in every `import`; a per-TYPE one moves ONE class, invisible until the dependent names it.
      * Both are fatal — the two ports each compile alone and cannot compile together. */
    case TypeRenameDivergence extends Kind(true,
      "§1(b) PER-LIBRARY: a type of the shared surface is moved differently in the two modules — " +
        "renamed, sub-packaged or flattened here and not there, or to a different destination — so " +
        "this module names a class the base never emits. Inherit the base's per-type rename maps " +
        "with `base.extendedBy(...)` rather than restating them.")
    /** this module moves part of the base's claimed namespace the base leaves in place. */
    case RenameOverride extends Kind(true,
      "§1(b) PER-LIBRARY: this module renames a prefix inside the base's declared namespace that " +
        "the base does not rename. Either the base should rename it too, or this module's " +
        "`governs` claim is wrong.")
    /** a signature-affecting phase the base ran is absent here. */
    case SurfaceMissing extends Kind(true,
      "§1(b) PER-LIBRARY: a phase that shapes emitted signatures ran in the base module and not " +
        "here, so this module re-derives the shared surface's signatures differently from the " +
        "module it compiles against. Add the phase to this manifest's `surface`, or inherit it.")
    /** two instances of one phase in one effective pipeline that the fold could neither MERGE nor
      * prove equal — configured differently, or configured in a way the engine cannot read. */
    case SurfaceDivergence extends Kind(true,
      "§1(b) PER-LIBRARY: one phase appears twice in the effective pipeline and the two instances " +
        "could not be MERGED — either the phase declares no `MergeablePolicy` (that is §1(a), " +
        "engine: give it one) or its own merge refused the pair, which is the drift CLAUDE.md §1 " +
        "warns about (§1(b): reconcile the two values, or share one instance). A phase that " +
        "implements no `SurfacePolicy` either is reported HOWEVER it is configured — its " +
        "fingerprint is its NAME, so the engine cannot tell two policies from one, and both " +
        "instances would run over one program (§1(a), engine: implement `SurfacePolicy`).")
    /** one location, two remedy SELECTIONS, in one policy chain.
      *
      * A sibling of [[SurfaceDivergence]] and not an instance of it: that one is about two INSTANCES
      * of a phase whose policies could not be composed, and this is about one key with two values,
      * which composes perfectly (nearest wins) and composes WRONG. */
    case ResolutionDivergence extends Kind(true,
      "§1(b) PER-LIBRARY: two manifests in this chain select DIFFERENT remedies at the same " +
        "location. A remedy decides emitted text at a declaration both modules compile against, so " +
        "the union that makes the effective policy well defined (nearest wins) is exactly what " +
        "would let a dependent silently re-answer its base — and the two ports would then each " +
        "compile alone and could not compile together. Reconcile the two entries: move the " +
        "selection to the module that owns the declaration, and inherit it with " +
        "`base.extendedBy(...)` rather than restating it.")
    /** a base selects a remedy at a location this module answers NOTHING at. `MissingDrop` read
      * at a member key, reachable only on the `mirroring` path: an INHERITING module's
      * `effectiveResolutions` already contains its base's (vacuous there); a module stating its
      * policy IN FULL has no such guarantee, and [[ResolutionDivergence]] cannot see the omission
      * since its `policyChain` is `List(this)` here. */
    case MissingResolution extends Kind(true,
      "§1(b) PER-LIBRARY: the base module SELECTS a remedy at this location and this module selects " +
        "none, so the same declaration is emitted two ways and the two ports cannot compile " +
        "together. This is only reachable for a module that restates its policy in full " +
        "(`PortManifest.mirroring`): add the entry here, or inherit the base's policy with " +
        "`base.extendedBy(...)` and stop restating it.")
    /** a dependent selects a remedy at a declaration its base EMITS and does not select one at. */
    case ResolutionIntrusion extends Kind(true,
      "§1(b) PER-LIBRARY: this module selects a remedy at a declaration inside a base's declared " +
        "namespace that the base EMITS and says nothing about, so the remedy would re-shape the " +
        "SHARED surface from the dependent's side. `SurfaceIntrusion`'s rule, read at a member key: " +
        "move the entry to the base's manifest, or (if the declaration is genuinely not part of the " +
        "shared surface) say so there. A subject the base leaves EMPTY is the allowed case — " +
        "nothing stands at that name in the base's output.")
    /** a dependent's merged-in key edits a namespace a base emits. */
    case SurfaceIntrusion extends Kind(true,
      "§1(b) PER-LIBRARY: this module adds policy for a type INSIDE a base's declared namespace " +
        "that the base emits mechanically, so the merged pipeline would re-shape the SHARED " +
        "surface from the dependent's side and the two ports could not compile together. Move the " +
        "entry to the base's manifest, or (if the type is genuinely not part of the shared " +
        "surface) drop it there.")
    /** a base this module depends on claims NO namespace, which switches off the intrusion screen
      * that protects its surface from this module. Not fatal: nothing has gone wrong YET, and the
      * base's own author is the one who must act. Loud, because the alternative is a screen that
      * reports nothing whether it passed or was never asked. */
    case BaseNamespaceUnclaimed extends Kind(false,
      "§1(b) PER-LIBRARY, in the BASE's manifest: this base states shared-surface policy and " +
        "claims NO namespace (`governs` is empty), so `PortManifest.claims` is false for every FQN " +
        "and the `governs` INTRUSION SCREEN is disabled for it — every subject a dependent's phase " +
        "adds inside the base's packages is admitted unscreened, silently, because a screen with " +
        "nothing to screen against cannot be told from one that passed. Declare the base's " +
        "`governs`. Leave it empty only for a resolution root that is not a ported module, which " +
        "is stated by an EMPTY manifest and reports nothing here.")
    /** this module is ported for a backend its base is not. */
    case TargetWidening extends Kind(true,
      "§1(b) PER-LIBRARY: this module declares a `targets` platform its base does not, so it is " +
        "about to be built for a backend the emitted Scala it compiles against was never checked " +
        "for — and may not be portable to. The base's own findings are the ones that would have " +
        "said so, and D2's ownership filter is exactly what stops this module reporting them, so " +
        "the unbuildable half is the half nothing looks at. NARROWING is free (a dependent may " +
        "target fewer platforms than its base and simply asks fewer questions); widening is not. " +
        "Either drop the platform here, or — if the base genuinely IS portable and only never said " +
        "so — widen the BASE's `targets`, which is a statement rather than a loophole.")
    /** a resolution-root type the base drops that this run did not tag. */
    case TagMissing extends Kind(true,
      "§1(b) PER-LIBRARY: the base module substitutes this type, and this run translated it as an " +
        "ordinary reference. Every phase keyed on `Substituted` silently skipped it here.")
    /** a resolution-root type this run tagged that the base does not drop. */
    case TagUnexpected extends Kind(true,
      "§1(b) PER-LIBRARY: this run substitutes a type the base module emits mechanically. The " +
        "replacement this module expects at that name is not the class the base wrote.")
    /** a shared type's emitted name is not the one the base's renames give it. */
    case SurfaceNameDivergence extends Kind(true,
      "§1(a)/(b): a shared type's emitted name is not the one the effective rename map gives it — " +
        "either the map disagrees with the base (b, fix the manifest) or the rename failed to " +
        "reach an owned symbol (a, engine).")
    /** an inherited key that matched nothing in THIS run. Not fatal: a narrower dependent module
      * legitimately never touches part of the shared surface. */
    case InheritedKeyNeverFired extends Kind(false,
      "§1(b) PER-LIBRARY, in the BASE's manifest: an inherited key matched nothing here. Harmless " +
        "if this module simply does not reach that part of the shared surface; a typo in the base " +
        "otherwise — and then the base is not doing what it says either.")
    /** a shared type the base's PUBLISHED map neither emitted nor dropped. */
    case BaseSurfaceAbsent extends Kind(true,
      "§1(b) PER-LIBRARY: the base module's published port map has no entry for this type — it " +
        "neither emitted it nor recorded it as dropped — so this module is about to compile " +
        "against a class nobody writes. Either the base's file list omits it (fix the base's " +
        "`FrontendConfig.files`), or this run resolves against a source tree the base does not " +
        "port. Re-derivation cannot see this at all: a manifest is silent about what it did not " +
        "mention.")
    /** the base's map was found and is PROVEN out of date. Not fatal: the check falls back to
      * re-derivation, which is weaker but still valid. Loud, because the fallback is silent. */
    case BaseMapStale extends Kind(false,
      "§1(b) PER-LIBRARY, OPERATIONAL: the base's published port map does not describe the base as " +
        "it is now, so it was REFUSED and this run fell back to re-deriving the base's decisions " +
        "from its manifest — which cannot see the base's emitted output, its nested-type drops, or " +
        "the configuration of any phase that does not implement `SurfacePolicy`. Re-run the base " +
        "port to restore the stronger check.")
    /** the base's map was published by a JVM of a DIFFERENT JDK specification than this run's.
      * FATAL, the only member of this family that is: `Stale`/`Missing` fall back to re-deriving
      * the base's decisions from its manifest (honest, since re-derivation would agree with a
      * re-run base). A JDK mismatch breaks that — the base's EMITTED SCALA was produced from a
      * different set of class files, and no re-derivation on THIS JVM reproduces it. */
    case BaseMapJdk extends Kind(true,
      "§1(b) PER-LIBRARY, OPERATIONAL: the base's port map was published by a JVM implementing a " +
        "DIFFERENT JDK specification than this run's, so the Scala this module is about to compile " +
        "against was emitted from class files this run cannot read. Nothing else can see it — the " +
        "engine, source and policy fingerprints all match, because the engine, the java and the " +
        "policy really are unchanged. Re-run the base port ON THIS JDK, or run this port on the " +
        "base's (`ENGINE-LIMITS.md` M5.10; a lane's own `jdk_guard` is the same question asked of " +
        "the COMPILER instead of the base).")
    /** freshness could not be established either way. The map WAS used. */
    case BaseMapUnverified extends Kind(false,
      "§1(b) PER-LIBRARY, OPERATIONAL: the base's published map was used but its freshness could " +
        "not be checked, so it may describe an older run. Not an error — absence of proof is not " +
        "proof — but the agreement below is only as current as that map.")
    /** a declared base with shared-surface policy has published no readable map. */
    case BaseMapMissing extends Kind(false,
      "§1(b) PER-LIBRARY, OPERATIONAL: this base declares shared-surface policy but has published " +
        "no readable port map, so the agreement below is RE-DERIVED from its manifest — the weaker " +
        "of the two checks (see this object's scaladoc for the three holes that leaves). Run the " +
        "base port once; every run publishes its map.")
    /** foreign resolution roots, and no base manifest to compare against. */
    case NoBaseDeclared extends Kind(true,
      "§1(b) PER-LIBRARY: this run resolves against sources it does not convert — it is a " +
        "DEPENDENT port — but names no base manifest, so nothing verifies that it agrees with the " +
        "module that emits them. Declare the base with `base.extendedBy(...)`. If the resolution " +
        "root is NOT a ported module, say so with an empty `PortManifest(name = \"…\")` as the base.")

  final case class Finding(kind: Kind, base: String, subject: String, detail: String):
    /** one grep-able line ENDING in the §1 classification, because that is what the reader acts on. */
    def render: String = s"$kind: $subject — $detail (base: $base)  [${kind.classification}]"

  /** The whole check: static + map health + dynamic. */
  def check(
      manifest: Option[PortManifest],
      shared: List[SharedType],
      foreignRoots: Boolean,
      ports: List[BasePort] = Nil,
      /** Declared drop keys the run actually bound. */
      fired: Set[String] = Set.empty,
  ): List[Finding] =
    manifest match
      case None =>
        if foreignRoots then
          List(Finding(Kind.NoBaseDeclared, "-", "(this run)",
            "resolution roots outside the source root, and no `PortManifest` at all"))
        else Nil
      case Some(m) =>
        val noBase =
          if foreignRoots && m.baseChain.isEmpty then
            List(Finding(Kind.NoBaseDeclared, "-", m.name,
              "resolution roots outside the source root, and the manifest declares no `bases`"))
          else Nil
        noBase ++ statik(m, fired, ports) ++ mapHealth(ports) ++ dynamic(m, shared, ports)

  /** Fatal findings that must stop a run BEFORE any phase runs: surface pairs the fold could not
    * compose. Same derivation as [[statik]] via [[surfacePairs]]. // ENGINE-LIMITS CT9 */
  def surfaceGate(manifest: Option[PortManifest], ports: List[BasePort] = Nil): List[Finding] =
    manifest.toList.flatMap(m => surfacePairs(m, ports)).filter(_.kind.fatal)

  // -------------------------------------------------------------------------
  // the maps themselves — R1, reported before anything is read OFF one
  // -------------------------------------------------------------------------

  /** Report the health of each base's published map (stale, unverified, missing, JDK mismatch). */
  private def mapHealth(ports: List[BasePort]): List[Finding] =
    ports.sortBy(_.name).flatMap { p =>
      p.jdk.map((published, running) => Finding(Kind.BaseMapJdk, p.name, s"${p.name} port map",
        s"published by a JVM on JDK $published; this run is on JDK $running")).toList ++
        p.stale.map(r => Finding(Kind.BaseMapStale, p.name, s"${p.name} port map", r)) ++
        p.unverified.map(r => Finding(Kind.BaseMapUnverified, p.name, s"${p.name} port map", r)) ++
        // Not "missing" when refused for a stated reason (stale or JDK mismatch).
        (if p.map.isEmpty && p.stale.isEmpty && p.jdk.isEmpty && p.declaresPolicy then
           List(Finding(Kind.BaseMapMissing, p.name, s"${p.name} port map",
             "no port map published by this base; the shared surface below is re-derived from its manifest"))
         else Nil)
    }

  // -------------------------------------------------------------------------
  // static — declaration against declaration
  // -------------------------------------------------------------------------

  /** a target set, ordered so two runs render it the same way. */
  private def render(ts: Set[balticporter.catalog.Platform]): String =
    if ts.isEmpty then "nothing" else ts.toList.map(_.toString).sorted.mkString("/")

  private def statik(m: PortManifest, fired: Set[String], ports: List[BasePort]): List[Finding] =
    val mine        = m.effectiveDropTypes
    val myMethods   = m.effectiveDropMethods
    val myRenames   = m.effectivePackageRenames
    val mySurface   = m.effectiveSurface.map(PortManifest.fingerprint)

    val perBase = m.baseChain.flatMap { b =>
      val bDropTypes   = b.effectiveDropTypes
      val bDropMethods = b.effectiveDropMethods
      val bRenames     = b.effectivePackageRenames

      val missingTypes = (bDropTypes -- mine).toList.sorted.map(k =>
        Finding(Kind.MissingDrop, b.name, k, "declared `dropTypes` in the base, absent here"))
      val missingMethods = (bDropMethods -- myMethods).toList.sorted.map(k =>
        Finding(Kind.MissingDrop, b.name, k, "declared `dropMethods` in the base, absent here"))

      // Extra drops: use the base's published map to check whether something stands at that FQN.
      // Fall back to the `governs` claim only when no map is available. // ENGINE-LIMITS D10
      val bMap = ports.find(_.name == b.name).flatMap(_.map).map(_.types.map(_.upstream).toSet)
      def baseHas(fqn: String): Boolean = bMap.forall(_.contains(fqn))
      val extraTypes = (mine -- bDropTypes).filter(k => b.claims(k) && baseHas(k)).toList.sorted.map(k =>
        Finding(Kind.ExtraDrop, b.name, k, "dropped here, inside the base's declared namespace, and not by the base"))
      val extraMethods = (myMethods -- bDropMethods)
        .filter { k => val owner = k.takeWhile(_ != '#'); b.claims(owner) && baseHas(owner) }
        .toList.sorted.map(k =>
          Finding(Kind.ExtraDrop, b.name, k, "method dropped here, inside the base's declared namespace, and not by the base"))

      val renameDiff = bRenames.toList.sorted.flatMap { (from, to) =>
        myRenames.get(from) match
          case Some(`to`) => Nil
          case Some(other) => List(Finding(Kind.RenameDivergence, b.name, from, s"""base renames it to "$to", this module to "$other""""))
          case None        => List(Finding(Kind.RenameDivergence, b.name, from, s"""base renames it to "$to", this module leaves it in place"""))
      }
      val renameExtra = myRenames.toList.sorted.collect {
        case (from, to) if b.claims(from) && !bRenames.contains(from) =>
          Finding(Kind.RenameOverride, b.name, from, s"""renamed to "$to" here; the base claims this namespace and leaves it in place""")
      }

      // Per-type rename comparison: compared as declarations, not resolved destinations.
      val bTypes  = b.perTypeDestinations
      val myTypes = m.perTypeDestinations
      val typeDiff = bTypes.toList.sorted.flatMap { (fqn, dest) =>
        myTypes.get(fqn) match
          case Some(`dest`) => Nil
          case Some(other)  => List(Finding(Kind.TypeRenameDivergence, b.name, fqn, s"""base declares `$dest`, this module `$other`"""))
          case None         => List(Finding(Kind.TypeRenameDivergence, b.name, fqn, s"""base declares `$dest`, this module leaves it in place"""))
      }
      val typeExtra = myTypes.toList.sorted.collect {
        case (fqn, dest) if b.claims(fqn) && !bTypes.contains(fqn) =>
          Finding(Kind.TypeRenameDivergence, b.name, fqn,
            s"""declared `$dest` here; the base claims this namespace and leaves the type in place""")
      }
      // Declared boundary moves: only where the rename itself agrees.
      val splitDiff = (b.effectiveAllowPackageSplit -- m.effectiveAllowPackageSplit).toList.sorted
        .filter(fqn => myTypes.get(fqn).exists(bTypes.get(fqn).contains))
        .map(fqn => Finding(Kind.TypeRenameDivergence, b.name, fqn,
          "the base declares this type's move a DELIBERATE boundary split and this module does not, " +
            "so the same rename is performed there and refused here"))

      // Base phases absorbed by a merge or subsumed by this module's own instance.
      def subsumes(bp: balticporter.tir.Phase): Boolean = bp match
        case mergeable: MergeablePolicy =>
          m.effectiveSurface.filter(_.name == bp.name).exists(mine =>
            mergeable.mergedWith(mine).exists(r =>
              PortManifest.fingerprint(r.phase) == PortManifest.fingerprint(mine)))
        case _ => false
      val surfaceGap = b.effectiveSurface.distinct
        .filterNot(p => mySurface.contains(PortManifest.fingerprint(p)) ||
          m.surfaceFold.absorbed.contains(PortManifest.fingerprint(p)) || subsumes(p))
        .map(PortManifest.fingerprint).distinct.map(f =>
          Finding(Kind.SurfaceMissing, b.name, f, "signature-affecting phase present in the base's surface, absent from this module's"))

      // Base namespace claim check.
      val unclaimed =
        if b.governs.isEmpty && b.declaresPolicy then
          List(Finding(Kind.BaseNamespaceUnclaimed, b.name, b.name,
            "declares shared-surface policy and claims no namespace, so nothing this module adds " +
              "inside it can be screened"))
        else Nil

      // Targets: narrowing is harmless, widening is fatal. // ENGINE-LIMITS D2
      val widened = (m.targets -- b.targets).toList.map(_.toString).sorted
      val targetGap =
        if widened.isEmpty then Nil
        else List(Finding(Kind.TargetWidening, b.name, widened.mkString(", "),
          s"this module targets ${render(m.targets)} and its base targets ${render(b.targets)}"))

      missingTypes ++ missingMethods ++ extraTypes ++ extraMethods ++ renameDiff ++ renameExtra ++
        typeDiff ++ typeExtra ++ splitDiff ++ surfaceGap ++ unclaimed ++ targetGap
    }

    val neverFired = m.inheritedKeysNeverFired(fired).toList.sortBy(_._1).flatMap { (base, keys) =>
      keys.toList.sorted.map(k => Finding(Kind.InheritedKeyNeverFired, base, k, "inherited key matched nothing in this run"))
    }

    perBase ++ surfacePairs(m, ports) ++ neverFired

  /** Surface half of the static layer: what the fold could not compose + intrusion screen.
    * Shared with [[surfaceGate]] so the gate and the report use one derivation. */
  private def surfacePairs(m: PortManifest, ports: List[BasePort]): List[Finding] =
    // Two instances of one phase name in the effective surface: the fold declined or refused merge.
    val whyRefused = m.surfaceFold.refusals.groupBy(_.phase)
    val divergent = m.effectiveSurface.groupBy(_.name).toList.sortBy(_._1).collect {
      case (n, ps) if ps.size > 1 =>
        val distinct = ps.map(PortManifest.fingerprint).distinct.sorted
        // Equal-as-rendered is stated explicitly so the reader does not think a misfire.
        val fps = if distinct.size > 1 then distinct.mkString(" vs ")
                  else s"${distinct.head} vs ${distinct.head} — EQUAL AS RENDERED, which is not evidence of agreement"
        val here = whyRefused.getOrElse(n, Nil)
        Finding(Kind.SurfaceDivergence, m.name, n,
          here.headOption.map(r => s"$fps — ${r.why}").getOrElse(fps))
    }

    // Intrusion screen: does anything STAND at the subject in the base's output? // ENGINE-LIMITS CT9
    // One finding per phase; divergent phases already reported are skipped.
    val divergentPhases = divergent.map(_.subject).toSet
    val intrusions = m.surfaceFold.intrusions
      .filterNot(i => divergentPhases.contains(i.phase))
      .filter(i => standsAt(ports, i.base, i.subject))
      .groupBy(_.phase).toList.sortBy(_._1)
      .map { (phase, is) =>
        val first = is.head
        Finding(Kind.SurfaceIntrusion, m.name, phase,
          first.why + evidence(ports, first.base, first.subject) +
            (if is.size > 1 then s" (${is.size} such subjects; the first is named)" else ""))
      }

    // Per-location remedy selection conflicts.
    val chainConflicts = m.resolutionConflicts.map { (key, claims) =>
      Finding(Kind.ResolutionDivergence, claims.map(_._1).distinct.mkString("+"), key,
        claims.map((who, k, id) =>
          s"""`$who` selects "$id"""" + (if k == key then "" else s" at `$k`")).mkString(", "))
    }

    // Mirroring path: `!inherit` only (inheriting modules are covered by `chainConflicts`).
    // Uses `MemberKey.mayNameSame` to avoid false positives from alternate key spellings.
    val myRes = m.effectiveResolutions.toList.sorted
    val mirrored = if m.inherit then Nil else m.baseChain.flatMap { b =>
      b.effectiveResolutions.toList.sorted.flatMap { (key, id) =>
        myRes.find((k, _) => MemberKey.mayNameSame(k, key)) match
          case Some((_, `id`)) => Nil
          case Some((k, other)) => List(Finding(Kind.ResolutionDivergence, b.name, key,
            s"""base `${b.name}` selects "$id", this module "$other"""" +
              (if k == key then "" else s""" at `$k` — the same member under the other spelling""")))
          case None => List(Finding(Kind.MissingResolution, b.name, key,
            s"""base `${b.name}` selects "$id" here; this module selects nothing"""))
      }
    }

    // Resolution intrusion screen: this module's own keys only, asked of what the base EMITS.
    // A key the base also answers is not an intrusion (already a ResolutionDivergence). // ENGINE-LIMITS D10
    val resolutionIntrusions = for
      (key, id) <- m.resolutions.toList.sortBy(_._1)
      subject    = MergeablePolicy.subjectOf(key)
      b         <- m.baseChain
      // `mayNameSame`: alternate spellings of the same member are not intrusions.
      if b.claims(subject) && !b.effectiveResolutions.keys.exists(MemberKey.mayNameSame(_, key)) &&
        standsAt(ports, b.name, subject)
    yield Finding(Kind.ResolutionIntrusion, b.name, key,
      s"""selects "$id" at a declaration of `$subject`, which is inside `${b.name}`'s declared """ +
        s"namespace and which `${b.name}` emits" + evidence(ports, b.name, subject))

    divergent ++ intrusions ++ chainConflicts ++ mirrored ++ resolutionIntrusions

  /** Does anything stand at `subject` in `base`'s output? Uses the published map when usable;
    * falls back to `true` (strictly more refusing) when no map is available. */
  private def standsAt(ports: List[BasePort], base: String, subject: String): Boolean =
    ports.find(_.name == base).flatMap(_.map) match
      case Some(m0)   => m0.byUpstream("type").get(subject).exists(_.disposition != PortMap.Disposition.Dropped)
      case scala.None => true

  /** Provenance annotation for the evidence. */
  private def evidence(ports: List[BasePort], base: String, subject: String): String =
    ports.find(_.name == base).flatMap(_.map) match
      case Some(m0) =>
        m0.byUpstream("type").get(subject).map(e => s""" — its published map emits it as "${e.emitted}"""")
          .getOrElse("")
      case scala.None =>
        s" — re-derived from `$base`'s MANIFEST, because it publishes no usable port map; run the " +
          "base port and this screen reads what it actually emits"

  // -------------------------------------------------------------------------
  // dynamic — the base's policy against what this run modelled of the shared surface
  // -------------------------------------------------------------------------

  private def dynamic(m: PortManifest, shared: List[SharedType], ports: List[BasePort]): List[Finding] =
    if shared.isEmpty then Nil
    else
      // Union of all bases' drops.
      val baseDrops = m.baseChain.flatMap(_.dropTypes).toSet
      val baseName  = m.baseChain.map(_.name).mkString("+")

      // Name as the BASE gives it, not this module's own rename map.
      val baseRenames = m.baseChain.foldLeft(Map.empty[String, String])((acc, b) => acc ++ b.effectivePackageRenames)
      // Base's per-type moves applied before package renames.
      val baseTypeMoves = m.baseChain.foldLeft(Map.empty[String, String])((acc, b) => acc ++ b.effectiveTypeMoves)
      def asTheBaseNamesIt(fqn: String): String =
        val once = PortManifest.longestPrefix(fqn, baseTypeMoves.keySet) match
          case Some(from) => baseTypeMoves(from) + fqn.substring(from.length)
          case scala.None => fqn
        PortManifest.longestPrefix(once, baseRenames.keySet) match
          case Some(from) => baseRenames(from) + once.substring(from.length)
          case scala.None => once

      // Published half: nearest base last so its entry wins.
      val usable = ports.filter(_.map.isDefined)
      val emittedByBase: Map[String, (String, PortMap.Entry)] =
        usable.foldLeft(Map.empty[String, (String, PortMap.Entry)]) { (acc, p) =>
          acc ++ p.map.get.byUpstream("type").iterator.map((k, e) => k -> (p.name, e))
        }
      // Second index by EMITTED name, for types whose upstreamFqn carries a post-type-rename name. // ENGINE-LIMITS D16
      val emittedByBaseName: Map[String, (String, PortMap.Entry)] =
        usable.foldLeft(Map.empty[String, (String, PortMap.Entry)]) { (acc, p) =>
          acc ++ p.map.get.types.filter(_.emitted.nonEmpty)
            .iterator.map(e => e.emitted -> (p.name, e))
        }
      // A type is absent only when a base with a usable map claims the namespace.
      def claimedBy(fqn: String): Option[String] =
        usable.collectFirst { case p if p.manifest.claims(fqn) => p.name }

      val sorted = shared.sortBy(_.upstreamFqn)

      val tags = sorted.flatMap { t =>
        emittedByBase.get(t.upstreamFqn).orElse(emittedByBaseName.get(t.emittedFqn)) match
          // Published: Dropped and Substituted both require the type to be tagged.
          case Some((who, e)) =>
            val notTranslated = e.disposition == PortMap.Disposition.Dropped ||
              e.disposition == PortMap.Disposition.Substituted
            if notTranslated == t.substituted then Nil
            else if notTranslated then
              List(Finding(Kind.TagMissing, who, t.upstreamFqn,
                s"the base's published map records it as ${e.disposition}; this run modelled it as an ordinary type"))
            else
              List(Finding(Kind.TagUnexpected, who, t.upstreamFqn,
                s"""this run tagged it `Substituted`; the base emitted it as "${e.emitted}""""))
          case scala.None =>
            claimedBy(t.upstreamFqn) match
              case Some(who) =>
                List(Finding(Kind.BaseSurfaceAbsent, who, t.upstreamFqn,
                  "inside the base's declared namespace, and its published map has no entry for it — " +
                    "the base neither emitted it nor recorded it as dropped"))
              // Re-derived: no usable map, fall back to declarations.
              case scala.None =>
                val expectedDrop = baseDrops.contains(t.upstreamFqn)
                if expectedDrop == t.substituted then Nil
                else if expectedDrop then
                  List(Finding(Kind.TagMissing, baseName, t.upstreamFqn,
                    "the base declares it dropped; this run modelled it as an ordinary type"))
                else
                  List(Finding(Kind.TagUnexpected, baseName, t.upstreamFqn,
                    "this run tagged it `Substituted`; the base emits it mechanically"))
      }

      // The name the base actually emitted (from map) or rename-implies (fallback).
      def expectedName(t: SharedType): Option[String] =
        emittedByBase.get(t.upstreamFqn).orElse(emittedByBaseName.get(t.emittedFqn)) match
        case Some((_, e)) if e.emitted.nonEmpty => Some(e.emitted)
        case Some(_)                            => scala.None // dropped: nothing was emitted to disagree with
        case scala.None                         => Some(asTheBaseNamesIt(t.upstreamFqn))

      // Group by rename prefix so one wrong entry does not produce N findings.
      val prefixes = baseRenames.keySet ++ m.effectivePackageRenames.keySet ++
        baseTypeMoves.keySet ++ m.effectiveTypeMoves.keySet
      val names = sorted
        .filter(t => expectedName(t).exists(_ != t.emittedFqn))
        .groupBy(t => PortManifest.longestPrefix(t.upstreamFqn, prefixes)
          .getOrElse(t.upstreamFqn.take(t.upstreamFqn.lastIndexOf('.').max(0))))
        .toList.sortBy(_._1)
        .map { (prefix, ts) =>
          val e   = ts.head
          val who = emittedByBase.get(e.upstreamFqn).map(_._1).getOrElse(baseName)
          val src = if emittedByBase.contains(e.upstreamFqn) then "emits" else "would name"
          Finding(Kind.SurfaceNameDivergence, who, prefix,
            s"""${ts.size} shared type(s): the base $src "${e.upstreamFqn}" """ +
              s""""${expectedName(e).getOrElse("")}"; this run emits "${e.emittedFqn}"""")
        }

      tags ++ names
