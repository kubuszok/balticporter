package balticporter.tir

/** WHERE a generic rewriting rule applies — the CLAUDE.md §1(b) half of every phase that RETYPES
  * declarations.
  *
  * ==Why every retyping rule needs one==
  * `CollectionsTransform` and `PrimitiveToOpaqueTransform` share a mechanism — move a set of
  * declarations onto a different type and carry every reference with them — and differ only in
  * WHICH declarations. Until a port can say "everything except these" or "only these", the answer
  * is always "everything", and a library with one hand-written bridge class that must keep the JDK
  * shape has no way to say so except by not running the phase at all. That is the §1(b) failure
  * mode exactly: a mechanism with its policy inlined.
  *
  * Two directions, because the two ports that need this need opposite things:
  *
  *   - [[RuleScope.Everywhere]] with an OPT-OUT list — the shape of a library that is mostly
  *     mechanical and has a handful of types it must not touch;
  *   - [[RuleScope.Only]] with an INCLUDE list — the shape of an opaque-type rule, where the port
  *     names a few seeds and the rest is discovered by
  *     [[balticporter.tir.RuleScope]]-constrained flow propagation.
  *
  * `Everywhere(Set.empty)` — the default everywhere this appears — makes the difference a NO-OP:
  * every symbol is in scope, so a phase configured with it takes the same code path it took before
  * scopes existed. That is §1(b)'s requirement that an empty parameter needs no code path, and it
  * is the property the measure lanes assert (0 members changed).
  *
  * ==Matching is by FULLY-QUALIFIED NAME, cut at a SEPARATOR — CLAUDE.md §4.56==
  * An entry names one of three things, and one rule covers all three because `Symbol.fullName` is
  * hierarchical:
  *
  * {{{
  * "com.foo"           a PACKAGE — covers its types and their members
  * "com.foo.Bar"       a TYPE    — covers its members and its nested types
  * "com.foo.Bar#baz"   a MEMBER  — covers that member (and its parameters)
  * }}}
  *
  * A prefix ALONE is not a structural fact about anything (§4.56, measured twice: a package rename
  * that turned `java.lang.String` into `j.lang.String`, and a cast dropped because its source type
  * had a `java.` prefix). So the match cuts only at one of the three separators `Symbol.fullName`
  * uses — `.` between packages and the top-level type, `$` before a nested type, `#` before a
  * member — and `com.foo` therefore does NOT cover `com.foobar`. [[RuleScope.covers]] is the one
  * implementation of that rule; `balticporter.core.PortManifest.covers` forwards to it rather than
  * keeping a second copy, because two copies of a rule this easy to get wrong is one copy too
  * many.
  *
  * ==And a SYMBOL is matched through its OWNERS, not through its own name alone==
  * That is not a convenience. Two of the four declaration kinds a retyping rule reaches carry a
  * `fullName` that no name-only test can place, and both were found by running this against the
  * frontend rather than by reading it:
  *
  *   - a method-LOCAL is named by its SIMPLE NAME (`SpoonTir.defineLocal`), so a local in
  *     `com.foo.Bar#m` is called `i`;
  *   - a PARAMETER is named `?#i`. The frontend qualifies it against its method before the method's
  *     own record is set, so the owner half of the name is the minter's placeholder.
  *
  * [[RuleScope.entryFor(program:balticporter\.tir\.Program,sym:balticporter\.tir\.Symbol)*]] climbs
  * the owner chain instead, which also gives the containment the three forms above promise: a
  * parameter of a scoped member is in scope with it, and a member of a scoped type is in scope with
  * the type, whatever its own `fullName` happens to be. A rule that read the name alone would have
  * scoped every field and method correctly and silently left every parameter and local behind — a
  * half-retyped signature, which is a compile error one call away and reads as a broken mapping.
  *
  * ==What this deliberately is NOT==
  * It is not a predicate. A `Symbol => Boolean` would be strictly more expressive and could not be
  * reported on: [[neverFired]] is what turns a typo'd entry from a silent no-op into a §1(b)
  * `PolicyFinding`, and that needs the DECLARED entries as data. A port that genuinely needs a
  * predicate has one — `PrimitiveToOpaqueTransform`'s `hints` — and it is a seed, not a scope.
  */
enum RuleScope:

  /** apply everywhere, EXCEPT the listed packages/types/members. `Set.empty` is the whole program
    * and is the default for every phase that takes a scope. */
  case Everywhere(except: Set[String] = Set.empty)

  /** apply ONLY to the listed packages/types/members (and, for a phase that propagates, to what
    * flows from them). An empty include set makes the phase a no-op — which is a statement a port
    * can make on purpose, and is the honest reading of "only these" for an empty list. */
  case Only(include: Set[String])

  /** the declared entries, whichever direction they point. What [[neverFired]] is the complement
    * of, and what a fingerprint renders. */
  def entries: Set[String] = this match
    case Everywhere(except) => except
    case Only(include)      => include

  /** is this the scope that covers the whole program with nothing declared? A phase may — and
    * `CollectionsTransform` does — branch on this to take its pre-scope code path unchanged, which
    * is the strongest available form of "an empty parameter is a no-op". */
  def isUnrestricted: Boolean = this match
    case Everywhere(except) => except.isEmpty
    case Only(_)            => false

  /** the DECLARED ENTRY that names `fullName`, longest first — or `None` if no entry does.
    *
    * Longest wins so that a port can write a package and then carve one type out of it in the other
    * direction; the entry returned is the one a `PolicyFinding` and a `Reason.Configured` key must
    * quote, because it is the string an agent edits (CLAUDE.md §4.575). */
  def entryFor(fullName: String): Option[String] = RuleScope.longestPrefix(fullName, entries)

  /** …for a SYMBOL, deciding from the owner chain when the symbol's own name does not — see the
    * class doc for why a name-only test cannot place a method-local.
    *
    * Fuel-bounded: a corrupt owner chain must not hang a scope decision, and the failure direction
    * is "no entry names it", which for [[Everywhere]] means IN scope (the pre-scope behaviour) and
    * for [[Only]] means OUT (nothing is rewritten without being asked for). Both are the
    * conservative answer for their direction. */
  def entryFor(program: Program, sym: Symbol, fuel: Int = 64): Option[String] =
    if entries.isEmpty then scala.None
    else
      entryFor(sym.fullName).orElse {
        if fuel <= 0 then scala.None
        else program.symbolOf(sym.owner).flatMap(o => entryFor(program, o, fuel - 1))
      }

  /** is `fullName` INSIDE this scope? */
  def includes(fullName: String): Boolean = this match
    case Everywhere(_) => entryFor(fullName).isEmpty
    case Only(_)       => entryFor(fullName).isDefined

  /** is this SYMBOL inside this scope, deciding through its owners? */
  def includes(program: Program, sym: Symbol): Boolean = this match
    case Everywhere(_) => entryFor(program, sym).isEmpty
    case Only(_)       => entryFor(program, sym).isDefined

  /** Declared entries that named nothing in this run — the §1(b) silent-no-op report.
    *
    * `fired` is what the phase OBSERVED matching, which is the only honest input: a key that names
    * a type this program does not contain is a typo or policy left behind by an upstream rename,
    * and it is invisible to every count (`PolicyReport`'s whole reason for existing). The phase
    * builds the `PolicyFinding`s, not this value — `balticporter.core` depends on
    * `balticporter.tir` and not the other way round, and a scope has no business knowing the shape
    * of a report. */
  def neverFired(fired: Set[String]): Set[String] = entries -- fired

  /** a stable, order-independent rendering, for [[balticporter.core.SurfacePolicy]]. Sorted, or two
    * ports that agree compare unequal on a `HashSet`'s iteration order. */
  def fingerprint: String = this match
    case Everywhere(except) if except.isEmpty => ""
    case Everywhere(except)                   => s"except:${except.toList.sorted.mkString(",")}"
    case Only(include)                        => s"only:${include.toList.sorted.mkString(",")}"

object RuleScope:

  /** the whole program, nothing excluded — the default for every phase that takes a scope. */
  val everywhere: RuleScope = Everywhere(Set.empty)

  /** the three separators `Symbol.fullName` uses: `.` between packages and the top-level type, `$`
    * before a nested type, `#` before a member (CLAUDE.md §4.56). */
  def isBoundary(c: Char): Boolean = c == '.' || c == '$' || c == '#'

  /** does `prefix` — a package, a type or a member FQN — NAME `fullName`?
    *
    * The whole §4.56 trap in four lines: a bare `startsWith` makes `com.foo` cover `com.foobar`,
    * and every honest entry that happens to share a prefix with an unrelated name then rewrites it,
    * silently, with a green compile. The cut must land on a separator or on the end of the string.
    *
    * An EMPTY prefix names nothing rather than everything: an empty entry in a policy set is a
    * mistake (a stray comma, a blank line in a generated list), and reading it as "the whole
    * program" would turn one typo into a scope that swallows the port. */
  def covers(fullName: String, prefix: String): Boolean =
    prefix.nonEmpty && fullName.startsWith(prefix) &&
      (fullName.length == prefix.length || isBoundary(fullName.charAt(prefix.length)))

  /** the LONGEST of `prefixes` that names `fullName` — the most specific entry, which is the one
    * whose author meant it. */
  def longestPrefix(fullName: String, prefixes: Set[String]): Option[String] =
    prefixes.filter(covers(fullName, _)).maxByOption(_.length)
