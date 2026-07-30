package balticporter.transform

import balticporter.tir.*

/** Move the port OUT of the upstream namespace: rewrite the package prefix of every symbol the
  * program itself declares.
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
  * ==Why this renames SYMBOLS and not text==
  * The TIR is symbol-resolved: every reference in every tree and every `TypeRepr` names a `SymId`,
  * never a string. Renaming the symbol therefore reaches every reference by construction, including
  * ones no textual rewrite could find — a type argument nested inside a method type inside an
  * inherited signature. Trees and the xref are untouched on purpose: they are keyed by `SymId`, and
  * an id is exactly what a rename must NOT change.
  *
  * ==Only symbols the program OWNS==
  * A prefix match alone would silently rewrite the standard library — `PackageRenameTransform(Map(
  * "java" -> "j"))` must not turn `java.lang.String` into `j.lang.String`, and neither must an
  * honest map that happens to share a prefix with a JDK package. Ownership is decided structurally,
  * the same way the rest of the engine tells in-program from external: an external symbol is
  * lazily interned by the frontend with `owner = SymId.None` and no `Definition` in the xref, while
  * everything the program declares hangs — through the `owner` chain — off one of the top-level
  * units. So a symbol is owned iff climbing its owners reaches a `program.units` symbol. That is
  * strictly stronger than "has a definition", which anonymous-class symbols do not.
  *
  * ==Where the prefix is cut==
  * `fullName` is a single string with THREE separators: `.` between packages and the top-level
  * type, `$` before a nested type (`p.Outer$Inner`), and `#` before a member (`p.Outer#m`). A
  * prefix therefore matches only at one of those boundaries — `com.foo` must not match
  * `com.foobar` — and everything after the cut is carried across verbatim, so nested-type and
  * member structure survives untouched. The emitter derives the `package` clause, the nested-type
  * path and the output file path from this one string, so cutting anywhere else would show up only
  * at emission.
  *
  * `Symbol.name` (the simple name) changes ONLY when the symbol IS the renamed entity, i.e. its
  * whole `fullName` equals the `from` prefix. A package rename never hits that case (packages have
  * no symbol of their own), so simple names are untouched; renaming a TYPE to a differently-named
  * type does, and then the simple name must follow or the emitter renders the old one.
  *
  * ==Ordering: this phase runs LAST==
  * Every other phase's policy is written in the UPSTREAM namespace — `ClassTableTransform`'s
  * redirects, `StaticForwarderTransform`'s wrappers, `Substitutions`' dropped types, an
  * `IntToOpaque` hint list are all FQNs from the library as it ships. Renaming first invalidates
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
final class PackageRenameTransform(renames: Map[String, String] = Map.empty) extends Phase:
  def name: String = "package-rename"

  override def run(program: Program): Program =
    // The no-op is the general path taken to its limit, not a branch around it: with no prefixes
    // nothing matches. The early return only spares the walk.
    if renames.isEmpty then program
    else
      val owned = PackageRenameTransform.ownedSymbols(program)
      // Prefixes the port DEMONSTRABLY declares types under — the ones that also cover at least one
      // OWNED symbol. Only these reach external symbols.
      //
      // That is what makes the relaxation safe without naming the JDK. A prefix under which this
      // program declares nothing is not this port's namespace, whatever the policy says, so
      // `Map("java" -> "jvm")` still cannot touch `java.lang.String`. A prefix under which it
      // declares 605 units plainly is, and the types it merely SUBSTITUTES there — parsed by
      // nobody, interned as external, replacement injected — move with it.
      val portOwnedPrefixes: Set[String] =
        val ownedNames = program.symbols.all.iterator.filter(s => owned(s.id)).map(_.fullName).toList
        renames.keySet.filter(p => ownedNames.exists(n => PackageRenameTransform.longestMatch(n, Set(p)).isDefined))
      val table = program.symbols.all.foldLeft(program.symbols) { (t, s) =>
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
          PackageRenameTransform.longestMatch(s.fullName, renames.keySet) match
            case scala.None => t
            case Some(from) =>
              val to      = renames(from)
              val newFull = to + s.fullName.substring(from.length)
              val newName = if s.fullName == from then PackageRenameTransform.simpleNameOf(to) else s.name
              t.updated(s.copy(name = newName, fullName = newFull))
      }
      // Trees and the xref are keyed by `SymId` and stay valid verbatim — that is the whole point
      // of renaming the symbol rather than the text. (The Pipeline rebuilds the xref anyway.)
      new Program(program.units, table, program.xref)

object PackageRenameTransform:

  /** `.` separates packages and the top-level type, `$` precedes a nested type, `#` a member. A
    * prefix may only be cut at one of the three. */
  private def isBoundary(c: Char): Boolean = c == '.' || c == '$' || c == '#'

  /** the last segment of a qualified name, at any of the three separators. */
  private def simpleNameOf(q: String): String =
    val i = q.lastIndexWhere(isBoundary)
    if i < 0 then q else q.substring(i + 1)

  /** does `fullName` sit under `prefix`, cut at a separator? (`com.foo` covers `com.foo.Bar` and
    * `com.foo` itself, never `com.foobar`.) */
  private def covers(fullName: String, prefix: String): Boolean =
    prefix.nonEmpty && fullName.startsWith(prefix) &&
      (fullName.length == prefix.length || isBoundary(fullName.charAt(prefix.length)))

  /** longest covering prefix — so a map holding both `com.foo` and `com.foo.bar` renames a symbol
    * under `com.foo.bar` by the more specific entry, as a namespace map is read everywhere else. */
  private[transform] def longestMatch(fullName: String, prefixes: Set[String]): Option[String] =
    prefixes.filter(covers(fullName, _)).maxByOption(_.length)

  /** The name `fqn` ends up with under `renames` — the same longest-prefix, cut-at-a-separator rule
    * the phase applies to a symbol, exposed for the code that has to say what an UPSTREAM name is
    * called in the EMITTED namespace without holding a `Program`.
    *
    * A run's POLICY is written upstream (§4.56: the rename runs last, so every other phase's keys
    * are upstream names), while everything observed about the running port — a stack frame, a
    * compiler path — is emitted. Anything joining the two has to translate one side, and doing it
    * by hand is where a bare `startsWith` gets written: `dropped-types.tsv` held upstream FQNs and
    * the correlator compared them against `sge.*` frames, so the derived expected-failure rule had
    * never once fired on a renaming port. */
  def renamed(fqn: String, renames: Map[String, String]): String =
    longestMatch(fqn, renames.keySet) match
      case Some(from) => renames(from) + fqn.substring(from.length)
      case scala.None => fqn

  /** Symbols the program DECLARES, as opposed to externals the frontend interned on first
    * reference. Climbs the `owner` chain — an owned symbol reaches a top-level unit, an external
    * one is rooted at `SymId.None` without being a unit. Fuel-bounded, so a corrupt owner cycle
    * cannot hang the phase; a symbol that exhausts it counts as NOT owned (renaming on a guess is
    * the failure mode this whole predicate exists to prevent). */
  def ownedSymbols(program: Program): Set[SymId] =
    val roots = program.units.map(_.symbol).toSet
    def rooted(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 &&
        (roots(s) || program.symbolOf(s).exists(sym => rooted(sym.owner, fuel - 1)))
    program.symbols.all.collect { case s if rooted(s.id, 64) => s.id }.toSet

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
