package balticporter.tir

/** ONE place a POLICY KEY becomes a SYMBOL — and the only place a phase is allowed to learn what a
  * key names.
  *
  * ==What it replaces==
  * Eighteen sites in the engine built or matched a member key, thirteen of them overload-blind, each
  * with its own string test. Two independent phases had already routed AROUND member identity rather
  * than trust one of those tests — `TestFrameworkTransform` refuses to read a callee's parameter list
  * ("only as good as the frontend's key encoding for an EXTERNAL symbol") and `CollectionsTransform`
  * reads a call's RESULT type to decide which `remove` overload Java resolved. Two routes around one
  * wall is the signal that the wall is a design defect and not a collection of local ones.
  *
  * ==Two stages, and the reason the first one is not optional==
  * A `dropMethods` key names a member the frontend removed BEFORE minting its symbol — no `SymId`,
  * no `Symbol`, no row in the symbol table. Resolving it against a `Program` reports every drop that
  * WORKED as a typo. So stage one is the [[MemberIndex]] the frontend publishes (what it SAW,
  * including what it dropped) and stage two is the symbol table (what the program HAS, including
  * externals). Policy that REMOVES something can only be bound where the thing still exists.
  *
  * ==Every refusal is a different instruction to its reader==
  * That is the whole point of the enum, and it is CLAUDE.md §4.45's rule applied to policy: a
  * finding an agent cannot classify costs it a full investigation. `NeverMatched` says *your key is
  * a typo*; `ExternalOnly` says *it named a JDK symbol, which is a documented silent no-op*;
  * `SyntheticTarget` says *it names a member the ENGINE created, which policy has no standing to
  * address*; `Ambiguous` says *it names three members and you asked for one, here they are*;
  * `Malformed` says *this could never have matched anything, and here is what to write*. Collapsed
  * into one, the first and the third read identically and mean opposite things.
  *
  * ==What it deliberately does not do==
  * It does not make string matching impossible. `Symbol.fullName` is public and `String`-typed, so
  * nothing in the type system stops a new phase writing `s.fullName.startsWith("java.")` — the exact
  * §4.56 defect, twice measured. This is a CONVENTION WITH A LINT (`PolicyKeyLintSpec`), not a
  * type-level guarantee, and saying so plainly is better than implying otherwise.
  */
final class PolicyBinder(val program: Program, index: MemberIndex):

  private val log = collection.mutable.ListBuffer.empty[PolicyBinder.Record]

  /** every binding this run asked for, with WHO asked — the never-fired report, unified. */
  def bindings: List[PolicyBinder.Record] = log.toList

  /** the ones that did not bind. */
  def unbound: List[PolicyBinder.Record] = log.toList.filter(_.binding.isUnbound)

  /** the records ONE phase's binds produced — what its own never-fired report is derived from.
    *
    * A phase reads this instead of keeping a private `var report`, and the difference is not
    * bookkeeping: a report built here is complete the moment the keys are bound, so a phase that
    * never ran reports the same thing a phase that ran and matched nothing does. Both are true. */
  def recordsFor(phase: String): List[PolicyBinder.Record] = log.toList.filter(_.phase == phase)

  // -------------------------------------------------------------------------
  // types
  // -------------------------------------------------------------------------

  /** bind a TYPE FQN. No descriptor is involved and none is wanted — a type key is a type key. */
  def bindType(phase: String, setting: String, entry: String,
               need: Ownership = Ownership.Owned): Binding[SymId] =
    record(phase, setting, entry, {
      if entry.isEmpty then Binding.Unbound(entry, NotBound.Malformed("an empty type name names nothing"))
      else if entry.contains('#') then
        Binding.Unbound(entry, NotBound.Malformed("a `#` makes this a MEMBER key; use the member seam"))
      else
        val hits = program.symbols.all.iterator.filter(_.fullName == entry).toList
        hits.filter(s => need.admits(program, s.id)) match
          case Nil if hits.isEmpty => Binding.Unbound(entry, NotBound.NeverMatched)
          case Nil                 => Binding.Unbound(entry, NotBound.ExternalOnly(entry))
          case s :: _              => Binding.Bound(entry, s.id, hits.size)
    })

  // -------------------------------------------------------------------------
  // members
  // -------------------------------------------------------------------------

  /** bind a member key to everything it NAMES — one overload for a precise key, every overload for
    * a bare one.
    *
    * The result is a list of [[PolicyBinder.Hit]] and not a `Set[SymId]`, which looks like a detail
    * and is the drop case. A DROPPED member's key FIRED and there is no symbol to hand back; a
    * `Set[SymId]` would represent that as "matched nothing", which is the exact reading the
    * `MemberIndex` exists to prevent. A caller that wants symbols asks for `hit.sym`, and gets an
    * empty answer with a `Bound` binding — the two facts kept apart. */
  def bindMembers(phase: String, setting: String, entry: String,
                  need: Ownership = Ownership.Owned): Binding[List[PolicyBinder.Hit]] =
    record(phase, setting, entry, resolve(entry, need).map(_._2))

  /** bind a member key to EXACTLY ONE overload. A key naming two is `Ambiguous`, and the finding
    * LISTS the candidates rendered with their descriptors — because the message is the string an
    * agent edits (§4.575). */
  def bindMember(phase: String, setting: String, entry: String,
                 need: Ownership = Ownership.Owned): Binding[PolicyBinder.Hit] =
    record(phase, setting, entry, resolve(entry, need).flatMap { (_, hits) =>
      hits match
        case List(one) => Binding.Bound(entry, one, 1)
        case many      => Binding.Unbound(entry, NotBound.Ambiguous(many.map(_.key.render).sorted))
    })

  /** bind a key to the symbol a CALL SITE names — exactly one overload, like [[bindMember]], and
    * differing from it in one documented place.
    *
    * ==Why a call site is not the same question as a declaration==
    * The two stages exist because a DROPPED member has no symbol: the frontend filters the
    * executable out before minting one, so only the index can answer for it. That is the whole
    * truth for a declaration — there is nothing left to rewrite. It is NOT the truth for a call:
    * `SpoonTir.methodSym` interns the callee from the REFERENCE, with the declaration's own
    * descriptor, so every caller of a dropped member still names a real `SymId` that this program
    * holds. Bound through [[bindMember]] the key returns `Bound` with an empty symbol list, and a
    * phase that rewrites calls concludes there is nothing to do — for the one case
    * `ENGINE-LIMITS.md` D7 is about (a base drops a member, a dependent still calls it), which is
    * precisely the case a call-site rewrite exists to repair.
    *
    * So when the index answers with DROPPED members only, this falls through to the symbol table
    * and takes the reference-side symbol. Two consequences, both deliberate:
    *
    *   - the `SyntheticTarget` refusal is SUPPRESSED on that path, and only on it. Its structural
    *     test is "the frontend walked this owner and did not record this executable, so the ENGINE
    *     minted it" — true of an engine-minted member, and equally true of a member the frontend
    *     recorded as DROPPED and then interned again from a call site. Left in place it reports the
    *     port's own `dropMethods` entry as an engine artefact.
    *   - the drop itself is still visible: the returned [[PolicyBinder.Hit]] carries `dropped =
    *     true`, so a phase can say "this callee has no declaration to return to" without asking
    *     the index a second question.
    *
    * Everything else — the grammar, the exactness requirement, the `Ambiguous` report — is
    * [[bindMember]]'s, by calling the same resolution. */
  def bindCallee(phase: String, setting: String, entry: String,
                 need: Ownership = Ownership.Either): Binding[PolicyBinder.Hit] =
    record(phase, setting, entry, resolve(entry, need, callSite = true).flatMap { (_, hits) =>
      hits match
        case List(one) => Binding.Bound(entry, one, 1)
        case many      => Binding.Unbound(entry, NotBound.Ambiguous(many.map(_.key.render).sorted))
    })

  /** bind a SCOPE entry — a package, a type or a member prefix. It names a REGION, so the answer is
    * only "did anything in this program fall inside it", and the failure that matters is
    * [[NotBound.ExternalOnly]]: an entry naming a JDK type matches the interned external perfectly,
    * the phase then does exactly nothing, and the entry counts as having fired. */
  def bindScope(phase: String, setting: String, entry: String): Binding[Unit] =
    record(phase, setting, entry, {
      if entry.isEmpty then
        Binding.Unbound(entry, NotBound.Malformed("an empty scope entry names nothing — a stray comma"))
      else
        val hits = program.symbols.all.iterator.filter(s => RuleScope.covers(s.fullName, entry)).toList
        hits.partition(s => program.owns(s.id)) match
          case (Nil, Nil)  => Binding.Unbound(entry, NotBound.NeverMatched)
          case (Nil, _)    => Binding.Unbound(entry, NotBound.ExternalOnly(entry))
          case (owned, _)  => Binding.Bound(entry, (), owned.size)
    })

  // -------------------------------------------------------------------------
  // internals
  // -------------------------------------------------------------------------

  /** the shared two-stage lookup: parse, ask the INDEX, then ask the PROGRAM.
    *
    * `callSite` is [[bindCallee]]'s one difference and is documented there: it asks for the symbol
    * a REFERENCE names, which exists for a dropped member where the declaration's does not. */
  private def resolve(entry: String, need: Ownership,
                      callSite: Boolean = false): Binding[(MemberKey, List[PolicyBinder.Hit])] =
    MemberKey.parse(entry) match
      case Left(m) => Binding.Unbound(entry, NotBound.Malformed(m.what))
      case Right(key) =>
        // STAGE 1 — what the frontend saw, dropped members included.
        val seen = index.matching(key).map((k, f) => PolicyBinder.Hit(k, f.sym, f.dropped))
        // …the index SAW this member and every match was dropped, so it has no symbol to hand back.
        // For a declaration that is the complete answer; for a CALL SITE it is the case that most
        // needs one.
        val droppedOnly = seen.nonEmpty && seen.forall(_.sym.isEmpty)
        if seen.nonEmpty && !(callSite && droppedOnly) then Binding.Bound(entry, (key, seen), seen.size)
        else
          // STAGE 2 — what the program HAS. Externals live only here, and so does anything the
          // ENGINE minted after the frontend ran.
          val syms = program.symbols.all.iterator.filter { s =>
            s.name == key.name && program.symbolOf(s.owner).exists(_.fullName == key.owner) &&
              key.descriptor.forall(d => s.descriptor.contains(d))
          }.toList
          val (owned, external) = syms.partition(s => program.owns(s.id))
          // `dropped` is carried from STAGE 1's answer: on the call-site fall-through the index
          // already said this member was removed, and losing that here would make a caller of a
          // dropped member indistinguishable from a caller of a live one.
          def hits(ss: List[Symbol]) = ss.map(s => PolicyBinder.Hit(key, Some(s.id), dropped = droppedOnly))
          // The engine-minted test is STRUCTURAL and it is about EXECUTABLES only. The index holds
          // what the frontend walked in a type BODY; a FIELD was never a candidate for it, so
          // "walked the owner, did not record the member" says nothing about one — and read without
          // this partition it says the wrong thing about every field in every parsed type.
          val (ownedExecs, ownedOther) = owned.partition(s => PolicyBinder.isExecutable(s.info))
          if syms.isEmpty then Binding.Unbound(entry, NotBound.NeverMatched)
          else if ownedOther.nonEmpty || (owned.nonEmpty && !index.types.contains(key.owner)) then
            Binding.Bound(entry, (key, hits(owned)), owned.size)
          // …unless STAGE 1 already recorded this very member as DROPPED, in which case the
          // structural test is answering about the frontend's own reference-side interning rather
          // than about anything the engine minted. See [[bindCallee]].
          else if ownedExecs.nonEmpty && droppedOnly then
            Binding.Bound(entry, (key, hits(ownedExecs)), ownedExecs.size)
          else if ownedExecs.nonEmpty then
            // The frontend WALKED this owner and did not record this executable, yet the program has
            // it and owns it: the ENGINE minted it. A key naming one is not a typo and must not read
            // as one — policy has no standing to address a member the engine created.
            Binding.Unbound(entry, NotBound.SyntheticTarget(ownedExecs.map(_.fullName).sorted.head))
          else if need.admitsExternal then
            Binding.Bound(entry, (key, hits(external)), external.size)
          else Binding.Unbound(entry, NotBound.ExternalOnly(key.owner))

  private def record[A](phase: String, setting: String, entry: String, b: Binding[A]): Binding[A] =
    log += PolicyBinder.Record(phase, setting, entry, b.forget)
    b

object PolicyBinder:
  /** one hit: WHICH overload, its symbol (absent for a dropped member), and whether it was dropped. */
  final case class Hit(key: MemberKey, sym: Option[SymId], dropped: Boolean)

  /** is this symbol's `info` a method type? The one structural question the two stages differ on. */
  def isExecutable(info: TypeRepr): Boolean = info match
    case _: TypeRepr.MethodType => true
    case TypeRepr.PolyType(_, r) => isExecutable(r)
    case _                       => false

  /** one binding, with the `(phase, setting)` that asked for it — the never-fired report's row. */
  final case class Record(phase: String, setting: String, entry: String, binding: Binding[Unit])

/** May a policy key name a symbol this program does not DECLARE?
  *
  * `Owned` is the default and the answer for every rewriting seam: a scope entry naming
  * `java.util.List` matches the interned external perfectly and the phase then does nothing, which
  * is a silent no-op wearing the costume of a working knob. `External` is for the one kind of rule
  * whose entire subject is external members — a portability rule about `Class#forName` has nothing
  * else to name.
  */
enum Ownership:
  case Owned, External, Either

  def admits(program: Program, id: SymId): Boolean = this match
    case Owned    => program.owns(id)
    case External => !program.owns(id)
    case Either   => true

  def admitsExternal: Boolean = this != Owned

/** Why a declared key did not bind. Every case is a DIFFERENT instruction to its reader — see
  * [[PolicyBinder]] for why collapsing any two of them costs an investigation. */
enum NotBound:
  /** nothing anywhere in the program or the index has this name — a typo, or policy left behind by
    * an upstream rename. */
  case NeverMatched
  /** the key names several overloads and the seam needs exactly one. Carries the candidates,
    * rendered with their descriptors, because that is the string its author will paste. */
  case Ambiguous(candidates: List[String])
  /** it matched — but only an INTERNED EXTERNAL, which for a rule that rewrites declarations is a
    * documented no-op that counts as having fired. */
  case ExternalOnly(fqn: String)
  /** the key is not in the grammar and could never have matched anything. */
  case Malformed(what: String)
  /** it names a member the ENGINE minted, which is in neither of the two places a policy key may
    * legitimately name. Not a typo — the opposite of one. */
  case SyntheticTarget(fqn: String)

  def label: String = this match
    case NeverMatched       => "never matched"
    case Ambiguous(_)       => "ambiguous"
    case ExternalOnly(_)    => "matched only an external symbol"
    case Malformed(_)       => "malformed"
    case SyntheticTarget(_) => "names an engine-minted member"

  def detail: String = this match
    case NeverMatched =>
      "no symbol of that name occurs in this program and the frontend recorded no such member, so " +
        "the rule silently did not run"
    case Ambiguous(cs) =>
      s"it names ${cs.size} overloads and this seam needs exactly one — write one of: ${cs.mkString(", ")}"
    case ExternalOnly(fqn) =>
      s"`$fqn` is a symbol this program REFERENCES and does not DECLARE, so there is no declaration " +
        "to rewrite and no body to leave alone: the entry matched and the rule did nothing"
    case Malformed(what) => what
    case SyntheticTarget(fqn) =>
      s"`$fqn` is a member the ENGINE minted, not one the frontend read out of Java — policy has no " +
        "standing to address it, and the fix (if it should be addressable at all) is in the engine"

/** The result of binding one declared key. */
enum Binding[+A]:
  case Bound(entry: String, value: A, matched: Int)
  /** `reason` and not `why`, so the accessor below — which is what a caller holding a
    * `Binding[A]` can actually reach — keeps the readable name. */
  case Unbound(entry: String, reason: NotBound)

  def isBound: Boolean   = this.isInstanceOf[Binding.Bound[?]]
  def isUnbound: Boolean = !isBound

  def toOption: Option[A] = this match
    case Bound(_, v, _) => Some(v)
    case Unbound(_, _)  => scala.None

  def why: Option[NotBound] = this match
    case Unbound(_, w) => Some(w)
    case _             => scala.None

  def map[B](f: A => B): Binding[B] = this match
    case Bound(e, v, n) => Binding.Bound(e, f(v), n)
    case Unbound(e, w)  => Binding.Unbound(e, w)

  def flatMap[B](f: (A) => Binding[B]): Binding[B] = this match
    case Bound(_, v, _) => f(v)
    case Unbound(e, w)  => Binding.Unbound(e, w)

  /** drop the payload — what a REPORT keeps, since a report renders names and never symbols. */
  def forget: Binding[Unit] = map(_ => ())
