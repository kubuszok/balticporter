package balticporter.transform

import balticporter.tir.*

/** What `RegistryTransform` could NOT turn into a registry lookup, one lane per kind — the §3
  * refusal enumeration for reflective instantiation (`ENGINE-LIMITS.md` P10). Each kind is a
  * different instruction to its reader, so each is its own lane (CLAUDE.md §4.45); an empty spec
  * records nothing at all. */
object RegistryCheck:

  /** the check family; the lanes are `registry(<kind>)`. */
  val Name = "registry"

  def lane(kind: String): String = s"$Name($kind)"

  /** Why a call was not, or could only partly be, turned into a registry lookup. */
  enum Issue:
    /** the callee's argument is not a `Class` value, so there is no key to look up. REFUSED: the
      * call is left as java wrote it. */
    case NonClassArg
    /** the class is named by a STRING at run time (`forName(...)`), so no registration can exist
      * for it. REFUSED. */
    case ByName
    /** `newInstance(this.getClass())` — a reflective SELF-CLONE, whose key is the runtime class of
      * an arbitrary subtype. REFUSED unless the entry's `miss` reproduces reflection. */
    case SelfClone
    /** a call of a member the port declares a THROWING FACADE member: it compiles and throws at run
      * time, and no registry replaces it. COUNTED, never rewritten. */
    case Facade
    /** the rewritten call sits under a `try` this entry's `handles` does not describe, so java's
      * handler is left in place over a callee that can no longer throw what it catches. */
    case GuardedCall
    /** a call of the entry's callee OUTSIDE its `scope` — the port declared where the registry
      * applies and this site is not there. REFUSED. */
    case OutOfScope
    /** `Miss.JvmReflect` at a module ported for a backend that has no runtime reflection: the miss
      * arm compiles there and answers "not registered" for every unseeded type. */
    case JvmOnlyMiss

  object Issue:
    /** the lane suffix — the string a reader greps for in `findings.tsv`. */
    def slug(i: Issue): String = i match
      case NonClassArg => "non-class-arg"
      case ByName      => "by-name"
      case SelfClone   => "self-clone"
      case Facade      => "facade"
      case GuardedCall => "guarded-call"
      case OutOfScope  => "out-of-scope"
      case JvmOnlyMiss => "jvm-only-miss"

    /** which of §1's three kinds the fix is (CLAUDE.md §4.45). */
    def classification(i: Issue): String = i match
      case NonClassArg =>
        "§1(b) PER-LIBRARY: a `Class`-keyed registry has nothing to key on here. Either the call " +
          "reaches the callee through a value the port can make a `Class` at the call site, or " +
          "this callee is not a registry site at all and the entry's `scope` should exclude it."
      case ByName =>
        "§1(b) PER-LIBRARY: the class is named by a string, so the answer is a NAME table " +
          "(`ClassTableTransform`), not an instantiation registry — the two mechanisms are keyed " +
          "differently on purpose."
      case SelfClone =>
        "§1(c) LIBRARY RULE: java clones an arbitrary subtype through its own runtime class. A " +
          "registry answers only for keys somebody registered, so the faithful port is the " +
          "library's own clone contract (a `cloneTask`-style abstract member), not this mechanism."
      case Facade =>
        "§1(b) PER-LIBRARY: this member belongs to a facade the port injected to THROW on every " +
          "reflective path. It compiles and fails at run time; the count is what keeps that " +
          "visible until the library's own serialisation story is ported."
      case GuardedCall =>
        "§1(a) ENGINE / §1(b): the retired callee's exception handler is still here. Declare the " +
          "exception on the entry's `handles` when java's `catch` exists only for this callee's " +
          "failure; where the handler does more than the entry's `miss`, the port owes a body."
      case OutOfScope =>
        "§1(b) PER-LIBRARY: the entry's `scope` does not name this call site, so the reflective " +
          "callee survives here. Widen the scope, or declare a second entry with its own placement."
      case JvmOnlyMiss =>
        "§1(b) PER-LIBRARY: `Miss.JvmReflect` is the JVM's answer and this module is ported for a " +
          "backend without runtime reflection. Off the JVM every unseeded type resolves to the " +
          "miss value — `seeds`, or a registration in the consumer's bootstrap, is what closes it."

  /** one refused or counted site. `unit` is the top-level symbol for D2 ownership filtering. */
  final case class Finding(issue: Issue, subject: String, detail: String, origin: Origin,
                           unit: SymId = SymId.None):
    def render: String = s"$issue $subject — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(lane(Issue.slug(issue)), Issue.slug(issue), subject,
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** every lane this check owns — required together, so a kind that recorded nothing still says 0. */
  val AllLanes: Set[String] = Issue.values.map(i => lane(Issue.slug(i))).toSet

  /** Record ONE lane per kind, so a kind that found nothing still says 0 rather than "never ran"
    * (`CheckReport.record`'s own reason). Lives here, beside the lane names it writes. */
  def record(findings: List[Finding]): Unit =
    Issue.values.foreach(i =>
      CheckReport.record(lane(Issue.slug(i)), findings.filter(_.issue == i).map(_.report)))

  /** grouped one-line summary, worst family first, each with its §1 classification. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
