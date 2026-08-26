package balticporter.tir

/** THE IDIOM LAYER'S OWN RECORD — what an idiom phase CONSIDERED, and what it did about it.
  *
  * ==Why this layer needs a record of its own at all==
  * Every other layer in this engine derives its mandate from a DIFFERENCE: java does X, scala does
  * Y, and the port must not silently do Y where java did X. An idiom transformer has no such
  * mandate by construction — the faithful translation already exists, compiles and behaves
  * identically — so it moves code that already means the right thing. What licenses that is stated
  * in `DESIGN.md` §8.15 and is not restated here; what this file carries is the consequence:
  *
  * '''an idiom transform's safety argument is a REFUSAL ENUMERATION, not a suite result'''
  * (`CLAUDE.md` §5). A suite is the strongest oracle this project has and it is not sufficient —
  * the corpus's oracle strength spans two orders of magnitude, from a 637-test ported suite down to
  * one port with no suite at all. So each transformer ENUMERATES the behavioural differences
  * between the java shape and the scala shape, and every member of that enumeration is either made
  * impossible by a structural guard, made impossible by the SHAPE the transformer emits, or
  * COUNTED. This log is the counting half, and the count is worthless unless the DENOMINATOR is
  * beside it — which is why a phase files a candidate for every site it CONSIDERED and not only for
  * the ones it converted.
  *
  * ==Three lanes, for the trivia family's reason one artifact over==
  * `idiom(refused) = 0` is a bar a run could hold by converting NOTHING, and `idiom(converted) = N`
  * says nothing about what the phase declined. So the positive, the refusal population and the
  * unrewritten-usage residue are reported APART (`IdiomCheck`), from one log, keyed by
  * [[IdiomKind]] so one lane stays readable per transformer.
  *
  * ==A value ONE RUN owns==
  * Never a process-global table, for the reason `TirEmitter.srcMap`, [[DecisionLog]], [[RewriteLog]]
  * and `CatalogLog` are values one run owns (`CLAUDE.md` §5.1): `Determinism.Full` translates twice,
  * and two translations sharing a log would double every count in it. Each phase owns a buffer that
  * [[Pipeline.runTraced]] DRAINS the moment the phase returns.
  */
final case class IdiomCandidate(
    kind: IdiomKind,
    verdict: IdiomVerdict,
    /** the DECLARATION the site sits in, fully qualified — `owner#member`. Not the site's own
      * expression, because a reader joining this against `decisions.tsv` or `members.tsv` has a
      * declaration in hand and never an expression (`CLAUDE.md` §5.1). */
    subject: String,
    /** what the candidate WAS, in one phrase — the interface a SAM anon named, the property a pair
      * would collapse to. Free text; the machine-readable half is [[verdict]]. */
    what: String,
    origin: Origin,
):
  def render: String = s"$kind ${verdict.render} $subject: $what  (${origin.javaPath}:${origin.line})"

/** WHICH transformer a candidate belongs to. Closed on purpose, exactly as [[Decision.Kind]] is: an
  * open string would make the lanes ungroupable and let two phases describe one act two ways. */
enum IdiomKind:
  /** a java anonymous class implementing a single-abstract-method interface → a scala lambda,
    * ASCRIBED to that interface (`DESIGN.md` §8.15). */
  case SamLambda
  /** a configured bean pair whose backing field could become a `var`/`val` (`DESIGN.md` §8.5's
    * deferred half). */
  case BeanCollapse
  /** an AUTO-DETECTED bean pair whose getter/setter names follow the Java bean convention and whose
    * owner type falls inside the phase's `RuleScope`. Separate from [[BeanCollapse]] because the
    * two populations are different — one is configured, the other derived — and an agent reading the
    * lane needs to know WHICH produced the row. */
  case BeanDetect
  /** a method whose every `return` is `this`, whose declared return type could narrow to
    * `this.type`. */
  case NarrowedReturn

/** …and WHAT was done about it. */
enum IdiomVerdict:
  /** the transformer changed this site. */
  case Converted
  /** the transformer DECLINED this site, naming the guard that declined it.
    *
    * `guard` is the enumeration member from the transformer's own delta enumeration — the string a
    * reader classifies the row by (`CLAUDE.md` §4.45: a finding an agent cannot classify costs it a
    * full investigation). `why` is the sentence that says whether the refusal is permanent.
    */
  case Refused(guard: String, why: String)
  /** a usage of a declaration this transformer MOVED that the transformer did not rewrite — the
    * `Rewrite.accountedBy` target for the transformers that move a declaration's `info`. A
    * transformer that moves no declaration produces none of these, and that is a fact about the
    * transformer rather than a hole in the lane. */
  case Residue(what: String)

  def render: String = this match
    case Converted        => "converted"
    case Refused(g, _)    => s"refused[$g]"
    case Residue(w)       => s"residue[$w]"

  def lane: String = this match
    case Converted    => "converted"
    case Refused(_,_) => "refused"
    case Residue(_)   => "residue"

/** the candidates one translation produced — see [[IdiomCandidate]] for why this is not global. */
final class IdiomLog:
  private val entries = collection.mutable.ListBuffer.empty[IdiomCandidate]

  def record(c: IdiomCandidate): Unit = entries += c
  def recordAll(cs: Iterable[IdiomCandidate]): Unit = entries ++= cs
  def all: List[IdiomCandidate] = entries.toList
  def isEmpty: Boolean = entries.isEmpty
  def size: Int = entries.size
  /** every candidate, emptying the buffer — what [[Pipeline.runTraced]] does at each phase
    * boundary so a phase instance reused across two translations never reports the first run's
    * candidates as the second's. */
  def drain(): List[IdiomCandidate] = { val out = entries.toList; entries.clear(); out }
  def clear(): Unit = entries.clear()

object IdiomLog:
  /** for a caller that does not want the record — a testkit fixture, a §1(c) rule's own harness.
    * A shared instance would accumulate across callers, so this is a factory and not a value. */
  def discarding: IdiomLog = new IdiomLog

/** A PHASE THAT FILES IDIOM CANDIDATES.
  *
  * Deliberately a trait beside [[Phase]] rather than a field on it: the log is drained per phase
  * boundary and a phase that files nothing should not be asked. `Pipeline.runTraced` drains
  * whatever it finds, so a census phase and its transformer are indistinguishable to the drain —
  * which is the point, because wave 0's census and wave 1's transformer are the same question
  * asked at the same position with different consequences.
  */
trait IdiomPhase extends Phase:
  /** owned by the phase for the duration of one `run`, drained by [[Pipeline.runTraced]]. */
  final val candidates: IdiomLog = new IdiomLog

  final def consider(c: IdiomCandidate): Unit = candidates.record(c)

  /** WHICH kinds this phase files — declared, so a run can print a ZERO that means something.
    *
    * Without it the report cannot tell *this phase ran and found nothing* from *this phase is not
    * in the pipeline*, and the first is a number a reader needs: a census whose population went to
    * zero is exactly what a conversion regression looks like from here, and it is invisible to
    * every other instrument (`CLAUDE.md` §3 — a check reporting zero is only as good as its
    * coverage). Declared by the phase rather than derived from the log for the same reason
    * `Rewrite.accountedBy` is declared: the log cannot say what an empty log was about. */
  def idiomKinds: Set[IdiomKind]

/** JAVA'S SINGLE-ABSTRACT-METHOD QUESTION, answered where the CLASS FILE is — never where the
  * phase is.
  *
  * ==Why the answer is a frontend value and not a phase computation==
  * "Is this interface a SAM" is a fact about a CLASS FILE: java's rule (JLS 9.8) counts the
  * abstract methods a type declares AND inherits, excludes `static` and `default` ones, and
  * excludes the public members of `java.lang.Object` — which is exactly why `java.util.Comparator`,
  * with its `equals` redeclaration, is a functional interface. Nothing in the TIR can answer it:
  * the frontend interns an external symbol lazily and its MEMBERS are interned only where the
  * program references them, so an interface whose single method the program never calls has no
  * members in the symbol table at all. A phase that "derived" the answer from what is interned
  * would be reading what this run happened to PARSE rather than what the program DECLARES — which
  * is `CLAUDE.md` §4.56's rule, and its recorded failure mode is a wrongful seal.
  *
  * So the frontend, which holds the shadow-class model, answers ONCE at each anonymous-class site
  * and the answer travels on the node ([[Tree.AnonClass.sam]]). Two consequences worth stating:
  *
  *   - the answer is JAVA'S OWN by construction, not by phase ordering. `DESIGN.md` §8.15 still
  *     places the transformer before every retyping phase, and that constraint is about the
  *     ASCRIPTION the conversion emits (a `CollectionsTransform` retarget would otherwise ascribe
  *     to a type java never named), not about the question;
  *   - [[Answer.Unreadable]] is a FIRST-CLASS answer and never `false`. `getTypeDeclaration`
  *     returns null for a type that is not on the classpath — "normal, extremely common" — and
  *     `CLAUDE.md` §4.6's rule applies literally: the default must not be indistinguishable from a
  *     real result. Here the two happen to lead to the same ACTION (leave the anonymous class),
  *     which is precisely why the refusal has to be COUNTED, or a port whose classpath is
  *     incomplete reads as a port with no SAM sites.
  */
object Sam:

  /** what the class file says about an anonymous class's target type. */
  enum Answer:
    /** an interface with exactly one abstract method, by java's own counting rule.
      *
      * @param method       that method's simple name
      * @param arity        its parameter count — the arity the emitted lambda must have
      * @param serializable the target extends `java.io.Serializable`. Carried rather than folded
      *                     into [[No]] because it is not a statement about SAM-ness at all: the
      *                     type IS a functional interface, and the port refuses the CONVERSION
      *                     because a serializable lambda's serialized form is not the anonymous
      *                     class's and `$deserializeLambda$` is not a translation of a class
      *                     descriptor. Two different facts must not share one constructor.
      */
    case Yes(method: String, arity: Int, serializable: Boolean)
    /** the class file was read and the type is not a functional interface — it is a class, or it
      * declares zero or several abstract methods. `reason` is the one the refusal prints. */
    case No(reason: String)
    /** the class file could not be read. NOT `No`: see the object doc. */
    case Unreadable

  object Answer:
    /** the value a hand-built tree carries when nobody asked — the CONSERVATIVE arm, and the same
      * arm an unreadable class file takes, so a fixture that forgot to state the answer refuses
      * loudly in the lane rather than converting on a fact nobody established. */
    val unknown: Answer = Unreadable
