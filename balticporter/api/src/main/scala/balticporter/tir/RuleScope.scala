package balticporter.tir

/** WHERE a generic rewriting rule applies — the CLAUDE.md §1(b) half of every retyping phase.
  * `Everywhere(except)`/`Only(include)`, default `Everywhere(Set.empty)` (no-op). Matched by
  * FULLY-QUALIFIED NAME cut at a SEPARATOR (`.`/`$`/`#`, §4.56), through OWNERS not name alone
  * (a local/parameter's own `fullName` is unusable), and only against symbols the program OWNS
  * (an external match fires silently). NOT a predicate — [[neverFired]] needs DECLARED entries. */
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

  /** …for a SYMBOL, deciding from the owner chain when the symbol's own name does not (a
    * method-local's `fullName` cannot place it — see the class doc). Fuel-bounded: a corrupt owner
    * chain must not hang a decision. Failure direction is "no entry names it" — IN scope for
    * [[Everywhere]] (pre-scope behaviour), OUT for [[Only]] — both conservative for their side. */
  def entryFor(program: Program, sym: Symbol, fuel: Int = 64): Option[String] =
    if entries.isEmpty then scala.None
    else
      val byName = if RuleScope.placedByOwnName(program, sym) then entryFor(sym.fullName) else scala.None
      byName.orElse {
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

  /** Declared entries that named nothing in this run — the §1(b) silent-no-op report. `fired` is
    * what the phase OBSERVED matching — a key naming nothing is a typo or leftover upstream-rename
    * policy, invisible to every count otherwise. The phase builds `PolicyFinding`s, not this value:
    * `core` depends on `tir`, not vice versa. */
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

  /** Does this symbol's OWN `fullName` place it, or is the owner chain the only evidence there is?
    * STRUCTURAL (the owner is a method), never a shape test on the string (§4.56): a method-LOCAL
    * is named by its SIMPLE NAME (`items` matches every local called `items` in the program) and a
    * PARAMETER is `?#p`, both silently. Neither name identifies anything, so neither is consulted. */
  def placedByOwnName(program: Program, sym: Symbol): Boolean =
    !program.symbolOf(sym.owner).exists(_.info match
      case _: TypeRepr.MethodType | _: TypeRepr.PolyType => true
      case _                                             => false)

  /** does `prefix` — a package, a type or a member FQN — NAME `fullName`? The whole §4.56 trap in
    * one line: a bare `startsWith` makes `com.foo` cover `com.foobar`, silently, with a green
    * compile — the cut must land on a separator or end-of-string. An EMPTY prefix names nothing
    * rather than everything (a stray comma must not swallow the whole port). */
  def covers(fullName: String, prefix: String): Boolean =
    prefix.nonEmpty && fullName.startsWith(prefix) &&
      (fullName.length == prefix.length || isBoundary(fullName.charAt(prefix.length)))

  /** the LONGEST of `prefixes` that names `fullName` — the most specific entry, which is the one
    * whose author meant it. */
  def longestPrefix(fullName: String, prefixes: Set[String]): Option[String] =
    prefixes.filter(covers(fullName, _)).maxByOption(_.length)

  /** Can NO fully-qualified name be inside both scopes? Two `Everywhere`s always overlap, since
    * each covers the whole program bar a finite set; an `Only` avoids an `Everywhere` exactly when
    * every entry it names is excluded there. Cut at a separator, as everything here is (§4.56). */
  def disjoint(a: RuleScope, b: RuleScope): Boolean = (a, b) match
    case (Everywhere(_), Everywhere(_)) => false
    case (Everywhere(except), Only(include)) => include.forall(i => longestPrefix(i, except).isDefined)
    case (Only(include), Everywhere(except)) => include.forall(i => longestPrefix(i, except).isDefined)
    case (Only(x), Only(y)) => !x.exists(i => y.exists(j => covers(i, j) || covers(j, i)))
