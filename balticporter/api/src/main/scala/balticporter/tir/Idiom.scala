package balticporter.tir

/** The idiom layer's own record — what an idiom phase considered, and what it did about it. An idiom transform has no difference-based mandate (the faithful translation already exists and behaves
  * identically), so its safety argument is a refusal enumeration, not a suite result: every delta is guarded, shaped away, or counted with its denominator — a candidate for every site considered.
  * Three lanes, one log; never process-global.
  */
final case class IdiomCandidate(
  kind:    IdiomKind,
  verdict: IdiomVerdict,
  /** the declaration the site sits in, fully qualified — `owner#member`. Not the site's own expression, because a reader joining this against `decisions.tsv` or `members.tsv` has a declaration in
    * hand and never an expression.
    */
  subject: String,
  /** what the candidate WAS, in one phrase — the interface a SAM anon named, the property a pair would collapse to. Free text; the machine-readable half is [[verdict]].
    */
  what:   String,
  origin: Origin
):
  def render: String = s"$kind ${verdict.render} $subject: $what  (${origin.javaPath}:${origin.line})"

/** WHICH transformer a candidate belongs to. Closed on purpose, exactly as [[Decision.Kind]] is: an open string would make the lanes ungroupable and let two phases describe one act two ways.
  */
enum IdiomKind:
  /** a java anonymous class implementing a single-abstract-method interface → a scala lambda, ascribed to that interface.
    */
  case SamLambda

  /** a configured bean pair whose backing field could become a `var`/`val`.
    */
  case BeanCollapse

  /** an AUTO-DETECTED bean pair whose getter/setter names follow the Java bean convention and whose owner type falls inside the phase's `RuleScope`. Separate from [[BeanCollapse]] because the two
    * populations are different — one is configured, the other derived — and an agent reading the lane needs to know WHICH produced the row.
    */
  case BeanDetect

  /** a method whose every `return` is `this`, whose declared return type could narrow to `this.type`.
    */
  case NarrowedReturn

  /** a nullary java method that is getter-like (returns a value, body free of side effects) and whose owner type falls inside the phase's `RuleScope` — its `()` is dropped, making it a scala
    * parameterless `def`. The sge reference port's empirical convention, no written rule in `conversion-rules.md`.
    */
  case NullaryArity

/** …and WHAT was done about it. */
enum IdiomVerdict:
  /** the transformer changed this site. */
  case Converted

  /** the transformer declined this site, naming the guard that declined it. `guard` is the enumeration member from the transformer's own delta enumeration; `why` is the sentence saying whether the
    * refusal is permanent.
    */
  case Refused(guard: String, why: String)

  /** a usage of a declaration this transformer MOVED that the transformer did not rewrite — the `Rewrite.accountedBy` target for the transformers that move a declaration's `info`. A transformer that
    * moves no declaration produces none of these, and that is a fact about the transformer rather than a hole in the lane.
    */
  case Residue(what: String)

  def render: String = this match
    case Converted     => "converted"
    case Refused(g, _) => s"refused[$g]"
    case Residue(w)    => s"residue[$w]"

  def lane: String = this match
    case Converted     => "converted"
    case Refused(_, _) => "refused"
    case Residue(_)    => "residue"

/** the candidates one translation produced — see [[IdiomCandidate]] for why this is not global. */
final class IdiomLog:
  private val entries = collection.mutable.ListBuffer.empty[IdiomCandidate]

  def record(c:     IdiomCandidate):           Unit                 = entries += c
  def recordAll(cs: Iterable[IdiomCandidate]): Unit                 = entries ++= cs
  def all:                                     List[IdiomCandidate] = entries.toList
  def isEmpty:                                 Boolean              = entries.isEmpty
  def size:                                    Int                  = entries.size

  /** every candidate, emptying the buffer — what [[Pipeline.runTraced]] does at each phase boundary so a phase instance reused across two translations never reports the first run's candidates as the
    * second's.
    */
  def drain(): List[IdiomCandidate] = { val out = entries.toList; entries.clear(); out }
  def clear(): Unit                 = entries.clear()

object IdiomLog:
  /** for a caller that does not want the record — a testkit fixture, a library-specific rule's own harness. A shared instance would accumulate across callers, so this is a factory and not a value.
    */
  def discarding: IdiomLog = new IdiomLog

/** A PHASE THAT FILES IDIOM CANDIDATES. A trait beside [[Phase]], not a field on it — a phase that files nothing should not be asked. `Pipeline.runTraced` drains whatever it finds, so a census phase
  * and its eventual transformer are indistinguishable to the drain, deliberately.
  */
trait IdiomPhase extends Phase:
  /** owned by the phase for the duration of one `run`, drained by [[Pipeline.runTraced]]. */
  final val candidates: IdiomLog = new IdiomLog

  final def consider(c: IdiomCandidate): Unit = candidates.record(c)

  /** Which kinds this phase files — declared, so a run can print a zero that means something: without it the report cannot tell "ran and found nothing" from "not in the pipeline", and a census
    * population going to zero is exactly what a conversion regression looks like. Declared by the phase for `Rewrite.accountedBy`'s reason: an empty log says nothing.
    */
  def idiomKinds: Set[IdiomKind]

/** Java's single-abstract-method question, answered where the class file is — never where the phase is. A frontend value, not a phase computation: the TIR interns external members lazily, so deriving
  * the answer from what happened to be parsed would silently seal the wrong verdict. Travels on the node ([[Tree.AnonClass.sam]]). [[Answer.Unreadable]] is first-class, never `false` — an incomplete
  * classpath must be counted, not read as "no SAM sites".
  */
object Sam:

  /** what the class file says about an anonymous class's target type. */
  enum Answer:
    /** an interface with exactly one abstract method, by java's own counting rule.
      * @param method
      *   that method's simple name @param arity its parameter count — the emitted lambda's arity @param serializable the target extends `java.io.Serializable` — carried separately since it is not
      *   about SAM-ness: the port refuses the CONVERSION (not the fact) because a serializable lambda's `$deserializeLambda$` is not a class descriptor.
      */
    case Yes(method: String, arity: Int, serializable: Boolean)

    /** the class file was read and the type is not a functional interface — it is a class, or it declares zero or several abstract methods. `reason` is the one the refusal prints.
      */
    case No(reason: String)

    /** the class file could not be read. NOT `No`: see the object doc. */
    case Unreadable

  object Answer:
    /** the value a hand-built tree carries when nobody asked — the CONSERVATIVE arm, and the same arm an unreadable class file takes, so a fixture that forgot to state the answer refuses loudly in
      * the lane rather than converting on a fact nobody established.
      */
    val unknown: Answer = Unreadable
