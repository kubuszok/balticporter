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
  * ==What this check CANNOT see==
  * Stated here because a composition check that silently misses a class of drift is worse than no
  * check at all:
  *
  *  1. '''A phase's configuration, unless the phase opts in.''' [[PortManifest.fingerprint]]
  *     compares by `Phase.name` plus, for a phase implementing [[SurfacePolicy]], its policy. Two
  *     differently-configured instances of a phase that does not implement it compare EQUAL.
  *  2. '''A phase whose OUTPUT differs for reasons outside its policy''' — a retyping keyed on
  *     something in the program rather than on a parameter.
  *  3. '''Nested types.''' The dynamic layer walks top-level units, so a drop naming a nested type
  *     is verified only by the never-fired tally, not by tag parity.
  *  4. '''A base module that is not DECLARED.''' Nothing can be compared against a manifest that
  *     was never named — which is why a run with foreign resolution roots and no declared base is
  *     itself a finding ([[Kind.NoBaseDeclared]]) rather than a silent pass.
  *  5. '''Divergence in the base's own emitted output.''' This compares POLICY and the dependent's
  *     model of the shared surface. It does not read the base's emitted Scala, so a base whose
  *     output changed for an engine reason — a new phase in the engine, a different engine version —
  *     is invisible here. That is what `EnginePin` is for.
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
    /** two instances of one phase, configured differently, in one effective pipeline. */
    case SurfaceDivergence extends Kind(true,
      "§1(b) PER-LIBRARY: one phase appears twice in the effective pipeline with different " +
        "policy — a second, differently-configured instantiation, which is exactly the drift " +
        "CLAUDE.md §1 warns about. Share the base's instance instead of building a new one.")
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
    */
  def check(manifest: Option[PortManifest], shared: List[SharedType], foreignRoots: Boolean): List[Finding] =
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
        noBase ++ statik(m) ++ dynamic(m, shared)

  // -------------------------------------------------------------------------
  // static — declaration against declaration
  // -------------------------------------------------------------------------

  private def statik(m: PortManifest): List[Finding] =
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

      val surfaceGap = b.effectiveSurface.map(PortManifest.fingerprint).distinct
        .filterNot(mySurface.contains).map(f =>
          Finding(Kind.SurfaceMissing, b.name, f, "signature-affecting phase present in the base's surface, absent from this module's"))

      missingTypes ++ missingMethods ++ extraTypes ++ extraMethods ++ renameDiff ++ renameExtra ++ surfaceGap
    }

    // one phase NAME carrying two different policies in one pipeline is drift regardless of which
    // manifest each came from, so it is checked once over the effective surface.
    val divergent = m.effectiveSurface.groupBy(_.name).toList.sortBy(_._1).collect {
      case (n, ps) if ps.map(PortManifest.fingerprint).distinct.size > 1 =>
        Finding(Kind.SurfaceDivergence, m.name, n,
          ps.map(PortManifest.fingerprint).distinct.sorted.mkString(" vs "))
    }

    val neverFired = m.inheritedKeysNeverFired.toList.sortBy(_._1).flatMap { (base, keys) =>
      keys.toList.sorted.map(k => Finding(Kind.InheritedKeyNeverFired, base, k, "inherited key matched nothing in this run"))
    }

    perBase ++ divergent ++ neverFired

  // -------------------------------------------------------------------------
  // dynamic — the base's policy against what this run modelled of the shared surface
  // -------------------------------------------------------------------------

  private def dynamic(m: PortManifest, shared: List[SharedType]): List[Finding] =
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
      def asTheBaseNamesIt(fqn: String): String =
        PortManifest.longestPrefix(fqn, baseRenames.keySet) match
          case Some(from) => baseRenames(from) + fqn.substring(from.length)
          case scala.None => fqn

      val sorted = shared.sortBy(_.upstreamFqn)

      val tags = sorted.flatMap { t =>
        val expectedDrop = baseDrops.contains(t.upstreamFqn)
        if expectedDrop == t.substituted then Nil
        else if expectedDrop then
          List(Finding(Kind.TagMissing, baseName, t.upstreamFqn,
            "the base declares it dropped; this run modelled it as an ordinary type"))
        else
          List(Finding(Kind.TagUnexpected, baseName, t.upstreamFqn,
            "this run tagged it `Substituted`; the base emits it mechanically"))
      }

      // A namespace divergence is a property of the PREFIX, not of the types under it: one wrong
      // rename entry would otherwise produce one finding per shared type — 605 of them on libGDX —
      // and bury every other finding in the report. Grouped by the rename rule that explains it.
      val prefixes = baseRenames.keySet ++ m.effectivePackageRenames.keySet
      val names = sorted
        .filter(t => asTheBaseNamesIt(t.upstreamFqn) != t.emittedFqn)
        .groupBy(t => PortManifest.longestPrefix(t.upstreamFqn, prefixes)
          .getOrElse(t.upstreamFqn.take(t.upstreamFqn.lastIndexOf('.').max(0))))
        .toList.sortBy(_._1)
        .map { (prefix, ts) =>
          val e = ts.head
          Finding(Kind.SurfaceNameDivergence, baseName, prefix,
            s"""${ts.size} shared type(s): the base names "${e.upstreamFqn}" """ +
              s""""${asTheBaseNamesIt(e.upstreamFqn)}"; this run emits "${e.emittedFqn}"""")
        }

      tags ++ names
