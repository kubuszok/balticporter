package balticporter.core

/** Does this module's policy agree with the modules it is a DEPENDENT of?
  *
  * ==Why a check and not just composition==
  * [[PortManifest.extendedBy]] makes agreement the default, and if every consumer used it this
  * object would be unreachable. None of them will. A port that already exists was written longhand,
  * a port that is being migrated to manifests is longhand while it is being migrated, and a port
  * whose author had a reason to spell the policy out is longhand forever. The failure mode is the
  * same in all three: a dependent module that quietly re-derives the shared surface differently
  * from the module it compiles against, producing two ports that each compile alone and cannot
  * compile together. So the check reads the EFFECTIVE policy, however it got there.
  *
  * ==Two layers, because they see different things==
  *
  *  - '''Static''' — manifest against manifest, before anything is parsed. Catches a declaration
  *    that is missing, extra, or different. It is cheap, it is total over what was declared, and it
  *    is blind to whether any of it applied.
  *  - '''Dynamic''' — the base's policy against what this run actually MODELLED of the shared
  *    surface: for every type the run resolved against but did not convert, was it tagged
  *    [[Substituted]] exactly when the base drops it, and does it now carry the name the base gives
  *    it? This is the audit's own sentence — "a resolution-root type tagged `Substituted` in port A
  *    is identically tagged in port B" — and it is worth more than the static layer because a
  *    declaration that is present and never fires looks identical to one that is absent.
  *
  * The dynamic layer identifies a shared type by the ORIGIN of the unit, not by a package prefix.
  * That is deliberate: a library's own test suite normally declares its suites in the very packages
  * it tests, so no prefix separates "mine" from "the base's", while the file a unit was parsed from
  * always does.
  *
  * ==Two SOURCES for the base's half, and why the published one is better==
  * The dynamic layer needs to know what the base did with each shared type. There are two ways to
  * learn it, and this check prefers the second whenever it is available:
  *
  *  - '''RE-DERIVED''' — read the base's `PortManifest`. Cheap, always available, and an answer
  *    about INTENT: it says what the base was configured to do, and assumes the base's run agreed.
  *  - '''PUBLISHED''' — read the base's [[PortMap]], the artifact its run wrote. An answer about
  *    OUTPUT: the emitted FQN of every type, which types were dropped and which were replaced,
  *    which members exist and which of their bodies were hand-supplied.
  *
  * The published map is preferred because three of the five holes below are holes in
  * *re-derivation*, not in the check. It closes 1, 3 and 5, and it closes them by not asking the
  * question re-derivation asks: a phase's configuration cannot be mis-read off a map that records
  * what the phase PRODUCED, and neither can a nested type nor an emitted name.
  *
  * It is only usable while it is TRUE of the base, which is design risk R1. So a map is checked for
  * freshness ([[PortMap.freshness]]) before it is believed, a map proven stale is REFUSED and
  * reported ([[Kind.BaseMapStale]]) with the check falling back to re-derivation, and a base with no
  * map at all is reported too ([[Kind.BaseMapMissing]]) — because silently running the weaker check
  * is the failure this whole page exists to prevent.
  *
  * ==What this check CANNOT see==
  * Stated here because a composition check that silently misses a class of drift is worse than no
  * check at all. Each entry says whether a PUBLISHED map closes it:
  *
  *  1. '''A phase's configuration, unless the phase opts in.''' [[PortManifest.fingerprint]]
  *     compares by `Phase.name` plus, for a phase implementing [[SurfacePolicy]], its policy. Two
  *     differently-configured instances of a phase that does not implement it compare EQUAL.
  *     ''CLOSED by a published map for everything that reaches a NAME'' — a differently-configured
  *     phase that moved a type produces a different `emitted` column, and the map is compared
  *     against it directly.
  *  2. '''A phase whose OUTPUT differs for reasons outside its policy''' — a retyping keyed on
  *     something in the program rather than on a parameter. ''Same as 1.''
  *  3. '''Nested types.''' The dynamic layer walks top-level units, so a drop naming a nested type
  *     is verified only by the never-fired tally, not by tag parity. ''NARROWED by a published
  *     map'': a nested type the base dropped is an entry like any other, so a dependent that models
  *     it as ordinary is a `TagMissing`. What is still not covered is a nested type neither module
  *     mentions.
  *  4. '''A base module that is not DECLARED.''' Nothing can be compared against a manifest that
  *     was never named — which is why a run with foreign resolution roots and no declared base is
  *     itself a finding ([[Kind.NoBaseDeclared]]) rather than a silent pass. ''Not closed'': a map
  *     is found by the base's NAME, so an undeclared base is still nothing to look up.
  *  5. '''Divergence in the base's own emitted output.''' ''CLOSED'': the map IS the base's emitted
  *     output. A type the base neither emitted nor dropped is [[Kind.BaseSurfaceAbsent]], and an
  *     emitted name that differs from the base's own is a `SurfaceNameDivergence` derived from what
  *     the base wrote rather than from what its rename map says it would write.
  */
object ManifestAgreement:

  /** One type of the shared surface, as THIS run modelled it. Built by the orchestrator, which is
    * the layer that knows which units came from a resolution root; keeping paths out of here makes
    * the whole check a pure function that a test can drive with three strings. */
  final case class SharedType(
      /** the FQN the base module's Java declares, before any rename. */
      upstreamFqn: String,
      /** the FQN this run's symbol carries after every phase, renames included. */
      emittedFqn: String,
      /** did this run tag it [[Substituted]]? */
      substituted: Boolean,
  )

  /** A base module as this run FOUND it: its manifest, and its published map if there is a usable
    * one. Assembled by the orchestrator, which is the layer that touches the filesystem — keeping
    * the IO out of here leaves the whole check a pure function a test can drive with three strings
    * and a hand-built map.
    *
    * @param manifest the base's declared policy — always present, since a base is declared before
    *                 it is looked up.
    * @param map      its published [[PortMap]], if one was found AND is fresh. A map proven stale
    *                 is deliberately NOT carried here: the point of detecting staleness is to stop
    *                 the stale entry being used, so `stale` carries the reason and this stays
    *                 `scala.None`, which is the same shape as "never published" and takes the same
    *                 fallback path.
    * @param source   `run-latest` / `baseline` — which artifact was read.
    * @param stale    reasons the map was refused. Non-empty ⇒ `map` is `scala.None`.
    * @param unverified reasons freshness could not be established. The map IS used.
    */
  final case class BasePort(
      manifest: PortManifest,
      map: Option[PortMap.Map0] = scala.None,
      source: String = "",
      stale: List[String] = Nil,
      unverified: List[String] = Nil,
  ):
    def name: String = manifest.name
    /** does this base declare ANY shared-surface policy? An empty manifest is the documented way to
      * say "this resolution root is not a ported module" (CLAUDE.md §1.5), and holding one to the
      * obligation to publish a map would turn that statement into a finding. One derivation, on the
      * manifest, because the same line governs the `governs` obligation below. */
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
      *
      * Separate from [[RenameDivergence]] because the fix is a different key and the failure is a
      * different shape: a package divergence moves a whole namespace and is visible in every
      * `import`, while a per-TYPE one moves ONE class and is invisible until the dependent names
      * it. Both are fatal, and for the same reason — the two ports each compile alone and cannot
      * compile together. */
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
    /** two instances of one phase, configured differently, in one effective pipeline — and the
      * merge that would have composed them was declined or refused. */
    case SurfaceDivergence extends Kind(true,
      "§1(b) PER-LIBRARY: one phase appears twice in the effective pipeline with different " +
        "policy and the two could not be MERGED — either the phase declares no `MergeablePolicy` " +
        "(that is §1(a), engine: give it one) or its own merge refused the pair, which is the " +
        "drift CLAUDE.md §1 warns about (§1(b): reconcile the two values, or share one instance).")
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

  /** The whole check.
    *
    * @param manifest   this run's manifest, if it declared one.
    * @param shared     every type this run resolved against but did not convert. Empty for a base
    *                   port, which is what makes the check a no-op there rather than a special case.
    * @param foreignRoots whether the run resolved against roots outside its own source root — the
    *                   structural signature of a dependent port, and the trigger for
    *                   [[Kind.NoBaseDeclared]].
    * @param ports      the bases as the orchestrator found them on disk, in `baseChain` order
    *                   (furthest base first). Empty ⇒ every base is compared by re-derivation, which
    *                   is exactly the behaviour before published maps existed, so a caller that
    *                   cannot look one up loses nothing it had.
    */
  def check(
      manifest: Option[PortManifest],
      shared: List[SharedType],
      foreignRoots: Boolean,
      ports: List[BasePort] = Nil,
      /** declared drop keys the run BOUND — supplied by the caller, which is the only layer that
        * holds both the manifest and the `PolicyBinder`. Was read off a mutable tally on
        * `Substitutions`; see `PortManifest.inheritedKeysNeverFired`. */
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
        noBase ++ statik(m, fired) ++ mapHealth(ports) ++ dynamic(m, shared, ports)

  /** THE FINDINGS THAT MUST STOP A RUN BEFORE ANY PHASE RUNS — the same-name pairs the fold could
    * not compose, and nothing else.
    *
    * Every finding this object produces is reported after the translation, which is right for all
    * but one of them: a disagreement about a DROP or a RENAME describes emitted text an operator can
    * read beside the finding. A pair the fold refused is different in kind, because the refusal is
    * about the PIPELINE THAT IS ABOUT TO RUN. `Pipeline.order` used to key phases by name and drop
    * one of the two silently, so the run then emitted a whole module with one shared-surface policy
    * missing and reported a fatal finding about something else entirely (`ENGINE-LIMITS.md` CT9
    * Face B). Ordering INSTANCES fixes the drop; it does not make running two conflicting
    * configurations of one phase a sane thing to do. So the refusal is made LOAD-BEARING here: the
    * run stops before the pipeline, with both instances' policies named.
    *
    * One derivation with [[statik]] — the same [[surfacePairs]] body — so the gate and the report
    * can never disagree about what a refusal is. The gate takes no `fired` set and no `shared`
    * list: neither exists before the translation, and neither is an input to this question.
    */
  def surfaceGate(manifest: Option[PortManifest]): List[Finding] =
    manifest.toList.flatMap(surfacePairs).filter(_.kind.fatal)

  // -------------------------------------------------------------------------
  // the maps themselves — R1, reported before anything is read OFF one
  // -------------------------------------------------------------------------

  /** Whether each base's map could be believed, said out loud.
    *
    * This runs before the dynamic layer and independently of whether the dynamic layer had anything
    * to compare: a base port that has not been run is worth reporting on a dependent that happens
    * to share no types with it, because the NEXT change to that base is when it matters. */
  private def mapHealth(ports: List[BasePort]): List[Finding] =
    ports.sortBy(_.name).flatMap { p =>
      p.stale.map(r => Finding(Kind.BaseMapStale, p.name, s"${p.name} port map", r)) ++
        p.unverified.map(r => Finding(Kind.BaseMapUnverified, p.name, s"${p.name} port map", r)) ++
        (if p.map.isEmpty && p.stale.isEmpty && p.declaresPolicy then
           List(Finding(Kind.BaseMapMissing, p.name, s"${p.name} port map",
             "no port map published by this base; the shared surface below is re-derived from its manifest"))
         else Nil)
    }

  // -------------------------------------------------------------------------
  // static — declaration against declaration
  // -------------------------------------------------------------------------

  private def statik(m: PortManifest, fired: Set[String]): List[Finding] =
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

      // an EXTRA drop is only a disagreement about a name the base CLAIMS; the dynamic layer
      // catches the rest exactly, from unit origins, without needing a namespace claim at all.
      val extraTypes = (mine -- bDropTypes).filter(b.claims).toList.sorted.map(k =>
        Finding(Kind.ExtraDrop, b.name, k, "dropped here, inside the base's declared namespace, and not by the base"))
      val extraMethods = (myMethods -- bDropMethods).filter(k => b.claims(k.takeWhile(_ != '#'))).toList.sorted.map(k =>
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

      // ---- the PER-TYPE half of the same policy (M6) --------------------------------------
      // Compared as DECLARATIONS (`typeRenames=X` / `subPackages=Y` / `flattenNestedTypes`) rather
      // than as resolved destinations, because two manifests have to agree about what they SAY:
      // a base that sub-packages a type and a dependent that spells the same destination as a
      // `typeRenames` entry agree today and diverge the moment either package rename changes.
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
      // A DECLARED boundary move is half of the rename: a dependent that inherited the rename and
      // not the declaration refuses a move its base performed, and then the two modules disagree
      // about where the type IS. Only reported where the rename itself agrees, or the row above
      // already says everything.
      val splitDiff = (b.effectiveAllowPackageSplit -- m.effectiveAllowPackageSplit).toList.sorted
        .filter(fqn => myTypes.get(fqn).exists(bTypes.get(fqn).contains))
        .map(fqn => Finding(Kind.TypeRenameDivergence, b.name, fqn,
          "the base declares this type's move a DELIBERATE boundary split and this module does not, " +
            "so the same rename is performed there and refused here"))

      // …or ABSORBED by a merge, or SUBSUMED by one of this module's own instances. A base phase
      // this module's fold composed with its own is present in the pipeline — inside the merged
      // phase — and reading `mySurface` alone would report the very composition the merge contract
      // exists to allow (DESIGN.md §8.13). The promise that makes this sound is the implementor's:
      // a merge preserves both inputs' behaviour on their own keys, or refuses.
      //
      // `subsumes` is the same question asked of a module that INHERITS NOTHING (`mirroring`) and
      // therefore has no fold to read: does merging the base's instance into mine change mine? If
      // not, mine already holds everything the base's does. Asked through the phase's own
      // `mergedWith`, so there is no second notion of containment to keep in step with the first.
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

      // …and the claim itself. Reported from the DEPENDENT's side because that is the run that has
      // both manifests in hand, and because it is this module whose added policy goes unscreened —
      // but the fix is in the base's manifest, which the classification says.
      val unclaimed =
        if b.governs.isEmpty && b.declaresPolicy then
          List(Finding(Kind.BaseNamespaceUnclaimed, b.name, b.name,
            "declares shared-surface policy and claims no namespace, so nothing this module adds " +
              "inside it can be screened"))
        else Nil

      missingTypes ++ missingMethods ++ extraTypes ++ extraMethods ++ renameDiff ++ renameExtra ++
        typeDiff ++ typeExtra ++ splitDiff ++ surfaceGap ++ unclaimed
    }

    val neverFired = m.inheritedKeysNeverFired(fired).toList.sortBy(_._1).flatMap { (base, keys) =>
      keys.toList.sorted.map(k => Finding(Kind.InheritedKeyNeverFired, base, k, "inherited key matched nothing in this run"))
    }

    perBase ++ surfacePairs(m) ++ neverFired

  /** THE SURFACE HALF of the static layer: what the fold could not compose, and what it screened.
    *
    * Split out of [[statik]] because it is the half that must be asked BEFORE the pipeline runs
    * ([[surfaceGate]]) as well as reported with everything else afterwards, and two derivations of
    * "is this pair a refusal" would be free to drift.
    */
  private def surfacePairs(m: PortManifest): List[Finding] =
    // One phase NAME carrying two different policies in one pipeline is drift regardless of which
    // manifest each came from, so it is checked once over the effective surface. Two instances
    // survive the fold only where the merge was DECLINED or REFUSED, so the fold's own sentence for
    // why is attached — and an INTRUSION is reported as itself, because the reader's next action is
    // a different one (DESIGN.md §8.13). Read off the pipeline and not off the refusal list, so a
    // phase that never declared a merge is detected exactly as it was before merging existed.
    val whyRefused = m.surfaceFold.refusals.groupBy(_.phase)
    val divergent = m.effectiveSurface.groupBy(_.name).toList.sortBy(_._1).collect {
      case (n, ps) if ps.map(PortManifest.fingerprint).distinct.size > 1 =>
        val fps  = ps.map(PortManifest.fingerprint).distinct.sorted.mkString(" vs ")
        val here = whyRefused.getOrElse(n, Nil)
        here.find(_.cause == SurfaceFold.Cause.Intrusion) match
          case Some(r) => Finding(Kind.SurfaceIntrusion, m.name, n, s"$fps — ${r.why}")
          case scala.None =>
            Finding(Kind.SurfaceDivergence, m.name, n,
              here.headOption.map(r => s"$fps — ${r.why}").getOrElse(fps))
    }

    // …and an INTRUSION that produced no pair. The arm above can only see a phase NAME carrying two
    // fingerprints, which is a merge that was refused; a dependent declaring a phase NO base has is
    // one instance in the pipeline, so it never reaches that arm — and it is the shape with the most
    // freedom, since nothing in any base's table constrains what it may re-point. The fold screens
    // it (`SurfaceFold.of`'s no-counterpart arm) and this is where its refusal becomes the finding.
    val divergentPhases = divergent.map(_.subject).toSet
    val intrusions = m.surfaceFold.refusals
      .filter(r => r.cause == SurfaceFold.Cause.Intrusion && !divergentPhases.contains(r.phase))
      .map(r => Finding(Kind.SurfaceIntrusion, m.name, r.phase, r.why))

    divergent ++ intrusions

  // -------------------------------------------------------------------------
  // dynamic — the base's policy against what this run modelled of the shared surface
  // -------------------------------------------------------------------------

  private def dynamic(m: PortManifest, shared: List[SharedType], ports: List[BasePort]): List[Finding] =
    if shared.isEmpty then Nil
    else
      // the UNION of the bases: a shared type is the base layer's, whichever module in it declares
      // the policy. Two bases that disagree with each other show up in `statik` above, so the union
      // is well defined by the time it is read here.
      val baseDrops = m.baseChain.flatMap(_.dropTypes).toSet
      val baseName  = m.baseChain.map(_.name).mkString("+")

      // the name the BASE gives a shared type — not the name this module's own rename map gives it,
      // which would only ever check the run against itself and always agree.
      val baseRenames = m.baseChain.foldLeft(Map.empty[String, String])((acc, b) => acc ++ b.effectivePackageRenames)
      // …and the base's PER-TYPE moves, applied first, exactly as the phase composes them: every
      // target is written upstream and the package renames apply to the result (§4.56). Omitting
      // this half would report a type the base deliberately moved as a divergence on every run.
      val baseTypeMoves = m.baseChain.foldLeft(Map.empty[String, String])((acc, b) => acc ++ b.effectiveTypeMoves)
      def asTheBaseNamesIt(fqn: String): String =
        val once = PortManifest.longestPrefix(fqn, baseTypeMoves.keySet) match
          case Some(from) => baseTypeMoves(from) + fqn.substring(from.length)
          case scala.None => fqn
        PortManifest.longestPrefix(once, baseRenames.keySet) match
          case Some(from) => baseRenames(from) + once.substring(from.length)
          case scala.None => once

      // ---- the PUBLISHED half ----------------------------------------------------------------
      // One lookup over every usable base map, nearest base LAST so its entry wins — the same
      // precedence `PortManifest.effectivePackageRenames` uses, for the same reason.
      val usable = ports.filter(_.map.isDefined)
      val emittedByBase: Map[String, (String, PortMap.Entry)] =
        usable.foldLeft(Map.empty[String, (String, PortMap.Entry)]) { (acc, p) =>
          acc ++ p.map.get.byUpstream("type").iterator.map((k, e) => k -> (p.name, e))
        }
      // A type this run models is only ABSENT from a base's output if some base with a usable map
      // CLAIMS the namespace. Without a claim there is no module obliged to have emitted it, and
      // reporting one would fire on every JDK-adjacent or third-party root a port resolves against.
      def claimedBy(fqn: String): Option[String] =
        usable.collectFirst { case p if p.manifest.claims(fqn) => p.name }

      val sorted = shared.sortBy(_.upstreamFqn)

      val tags = sorted.flatMap { t =>
        emittedByBase.get(t.upstreamFqn) match
          // PUBLISHED: what the base actually produced. `Dropped` (nothing stands at the name) and
          // `Substituted` (injected Scala stands at it) are one answer here — neither is a
          // mechanical translation, so this run must have tagged the type either way.
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
              // RE-DERIVED: no usable map claims this type, so fall back to comparing declarations.
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

      // The name the base ACTUALLY emitted where a map says so, and the name its rename map implies
      // otherwise. The difference is the whole of hole 5: a base whose rename failed to reach an
      // owned symbol, or whose output moved for an engine reason, satisfies its own rename map and
      // not its own output — and only the second is what a dependent compiles against.
      def expectedName(t: SharedType): Option[String] = emittedByBase.get(t.upstreamFqn) match
        case Some((_, e)) if e.emitted.nonEmpty => Some(e.emitted)
        case Some(_)                            => scala.None // dropped: nothing was emitted to disagree with
        case scala.None                         => Some(asTheBaseNamesIt(t.upstreamFqn))

      // A namespace divergence is a property of the PREFIX, not of the types under it: one wrong
      // rename entry would otherwise produce one finding per shared type — 605 of them on libGDX —
      // and bury every other finding in the report. Grouped by the rename rule that explains it.
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
