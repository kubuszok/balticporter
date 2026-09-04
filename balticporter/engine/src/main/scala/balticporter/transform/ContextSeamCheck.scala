package balticporter.transform

import balticporter.catalog.FixKind
import balticporter.tir.*

/** Counts every place [[GlobalsToImplicitsTransform]]'s `using`-threading closure stopped, and
  * why — a declaration with no signature to thread through (a class initialiser, a static field
  * initialiser, an unparsed override component). Eight [[Kind]]s, each a different reader
  * instruction; see [[balticporter.tir.NotBound]] for why they aren't collapsed. Gated by baseline,
  * not a hard-coded fatality — one library's census must not become an engine constant. No-op
  * unless a port declared a threaded holder. DESIGN.md §8.4, CLAUDE.md §1
  */
object ContextSeamCheck extends RemedySource:

  /** The check's name in `findings.tsv`. */
  val Name = "context-seam"

  /** The menu; see [[balticporter.tir.Remedy]] and `DESIGN.md` §8.16. Only `unconstructed-thread`
    * and `residual-global-read` get accept entries — the two kinds where "fine" is a real per-site
    * answer the engine cannot derive itself. Every other act already has a spelling elsewhere
    * (`selfSupplied`, `sites`, `promoteToClass`, `boundary`) and is a pointer, not an entry; and
    * `unsuppliable-use` gets none at all — the emitted file does not compile, so there is nothing
    * to accept. `lost-clause` is an engine bug (`DESIGN.md` §8.2, `ENGINE-LIMITS.md` CT5), not a
    * port's to silence.
    */
  def remedies: List[Remedy] = List(
    Remedy(
      id = "accept-unconstructed-thread", lane = Name, kind = Kind.UnconstructedThread.label,
      emissionAffecting = false, fix = FixKind.Parameterised,
      subject = Remedy.Subject.OwnedType,
      what = "the port states that this class is constructed by its USERS and not by a framework, " +
        "so the `using` clause is part of the ported API and there is nothing to supply — the " +
        "answer the warning says the engine cannot derive"),
    Remedy(
      id = "accept-residual-global", lane = Name, kind = Kind.ResidualGlobalRead.label,
      emissionAffecting = false, fix = FixKind.Parameterised,
      what = "the port states that this read stays global on purpose — no signature reaches it and " +
        "no `sites` entry should move it, which is a per-site statement `boundary` cannot make"),
  )

  /** DRAIN what this port selected — see [[remedies]] and `CLAUDE.md` §5. */
  def resolved(plan: ResolutionPlan, findings: List[Finding]): List[Finding] =
    plan.drain(remedies, findings)(f =>
      ResolutionPlan.Residue(f.kind.label, f.enclosing, f.subject, f.origin, f.detail))

  /** what kind of seam this is, which is what decides who fixes it (CLAUDE.md §1). */
  enum Kind(val label: String):
    case ResidualGlobalRead extends Kind("residual-global-read")
    /** the mirror of ResidualGlobalRead: a signature-less declaration that USES something
      * threaded — a `No given` at every site, unlike the coherent-but-global read. PROGRESS.md §10.8.9 */
    case UnsuppliableUse    extends Kind("unsuppliable-use")
    case DeferredInit       extends Kind("deferred-init")
    case CapturedContext    extends Kind("captured-context")
    case FrozenComponent    extends Kind("frozen-component")
    /** the port's own answer to a class no caller of this program constructs: constructors keep
      * java's signature, a `given` member supplies the context. ENGINE-LIMITS CT7 */
    case SelfSupplied       extends Kind("self-supplied")
    /** the CT7 shape observed rather than declared — a warning, not a refusal. */
    case UnconstructedThread extends Kind("unconstructed-thread")
    /** a static field whose initialiser constructs a threaded class — the field becomes a holder
      * with a throwing accessor, initialised at the head of threaded static methods. ENGINE-LIMITS
      * CT11 */
    case StaticFieldHolder  extends Kind("static-field-holder")
    /** a clause the phase attached that the emitted header does not carry — an engine bug, found
      * only from the emitter's own recording after emission. ENGINE-LIMITS CT5 */
    case LostClause         extends Kind("lost-clause")

  object Kind:
    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say. */
    def classification(k: Kind): String = k match
      case ResidualGlobalRead =>
        "§1(b) PER-LIBRARY: this read still reaches a global. It is at a site with no signature to " +
          "thread a context through — a class initialiser, a static field's initialiser, or a " +
          "declaration inside a refused override component. Move it behind a method the closure " +
          "can reach, give the site a `sites` policy (`lazy-init`), or accept it and set " +
          "`boundary = \"residual-global\"` so the read at least names the context rather than the " +
          "upstream holder. The engine needs no change."
      case UnsuppliableUse =>
        "§1(b) PER-LIBRARY, and IT DOES NOT COMPILE: this declaration constructs or calls something " +
          "the threading reached, and it has no signature to take a context through — a class " +
          "initialiser, a static field's initialiser, or a declaration inside a refused override " +
          "component. Unlike a residual READ, there is nothing here to re-spell: the emitted Scala " +
          "is `No given` at this line, so `boundary = \"residual-global\"` answers a question this " +
          "site never asked and no accept exists for it. What DOES reach it, and which one is right " +
          "is a fact about the library: give the site a `sites` policy (`lazy-init`) so the " +
          "initialisation moves to a first READ the closure can thread; move the use into a " +
          "declaration the closure can reach; give the USED type a `selfSupplied` entry, so it stops " +
          "taking a clause and there is nothing for this site to supply; or, where none of those " +
          "fits, `scope` the declaration out and keep the global it reads."
      case DeferredInit =>
        "§1(b) PER-LIBRARY and DELIBERATE: a `sites` entry asked for `lazy-init`, so this static is " +
          "now initialised at first READ instead of at class initialisation. Java runs a class " +
          "initialiser at first active use of the class; the two coincide only when nothing else " +
          "in the class is touched first. Read the decision row and confirm that holds here."
      case CapturedContext =>
        "§1(a) and CORRECT: the read is inside a lexically nested body whose own signature could " +
          "not change, so it captures the context from the enclosing declaration's clause. Nothing " +
          "to fix; the count exists so a port can size how much of its context outlives the call " +
          "that supplied it."
      case LostClause =>
        "§1(a) ENGINE, and SILENT until this line: the threading put a `using` clause on this " +
          "class's constructors and the emitted type does not carry one, so its body has no given " +
          "in scope — while its decision row and its porter note both say it does. A `class` here " +
          "is an engine bug in the constructor region (`DESIGN.md` §8.2), reachable from no " +
          "manifest key. The other three forms are the engine refusing rather than guessing, and " +
          "each has a port-level answer: an `object` is an all-static class with no constructor to " +
          "carry anything, a `trait` needs a `promoteToClass` entry (scala's trait parameters are " +
          "a different feature, and a subtrait may not pass arguments), and an `enum`'s primary IS " +
          "its java constructor, which every case object reaches with its own argument list — move " +
          "what needs the context off the enum, or scope the enum out."
      case SelfSupplied =>
        "§1(b) PER-LIBRARY and DELIBERATE: the port declared this type framework-instantiated, so " +
          "its constructors keep the signature java gave them and the context arrives from a " +
          "`given` member filled by the port's own expression. Nothing here is broken; the count " +
          "exists because the value this type threads is no longer its caller's, and because a " +
          "reader of the emitted file should be able to size how many of them there are. Read the " +
          "decision row for the expression, and confirm that a context built once per instance is " +
          "the one this type should have."
      case UnconstructedThread =>
        "§1(b) PER-LIBRARY, and it may be nothing: this class was threaded and NOTHING IN THIS " +
          "PROGRAM CONSTRUCTS IT, while its ancestry leaves the program — which is exactly the " +
          "shape of a class a FRAMEWORK instantiates (a test suite, a `ServiceLoader` " +
          "implementation, a bean). A reflective instantiation cannot supply a `using`, so the " +
          "emitted file compiles perfectly and the type cannot be built at run time — no error, no " +
          "other count, and the only evidence is the thing that stopped running. If a framework " +
          "constructs it, give it a `selfSupplied` entry naming the expression that yields the " +
          "context. If YOUR USERS construct it, this is correct as it stands and the clause is part " +
          "of the ported API: the engine cannot tell the two apart, which is why this warns rather " +
          "than refuses."
      case StaticFieldHolder =>
        "§1(a) ENGINE and DERIVED: this static field's initialiser constructs a type whose " +
          "constructor the threading reached, so the companion object cannot evaluate it at " +
          "initialisation time. The field becomes a holder with a throwing accessor, and the " +
          "initialiser runs at the head of every threaded static method on the same class. No " +
          "manifest key is needed — the engine derives the holder for every static field whose " +
          "initialiser constructs a threaded class, because the accessor keeps the field's name " +
          "and no new public name is minted."
      case FrozenComponent =>
        "§1(b)/§1(a): this override component reaches a declaration this program does not own — an " +
          "unparsed parent, or a resolution root's — so its signature is not this module's to " +
          "change, and threading half a component is a broken `override`. If the parent IS ported, " +
          "port it in the same run; if it is a trait of this program's own whose body needs the " +
          "context, add it to `promoteToClass`; otherwise the reads inside it stay global and are " +
          "counted above."

  /** one seam.
    *
    * @param subject   the DECLARATION the seam is at, fully qualified at the time it was found.
    * @param key       the policy entry a reader edits — the holder FQN, or the `sites` key.
    * @param enclosing the declaration's symbol, for the ownership filter and for attribution.
    */
  final case class Finding(kind: Kind, subject: String, key: String, detail: String,
                           origin: Origin, enclosing: SymId):
    def render: String = s"${kind.label} $subject — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, kind.label, subject, CheckReport.relativise(origin.javaPath),
                          origin.line, s"$detail [key=$key]")

  /** grouped one-line summary, worst family first, each with its §1 classification — the whole point
    * of the check is that a reader does not have to work out who fixes it. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.kind).toList.sortBy((_, v) => -v.size).map { (kind, vs) =>
        val head  = s"  ${vs.size} × ${kind.label}\n  ${Kind.classification(kind)}"
        val sites = vs.groupBy(f => (f.subject, f.detail)).toList.sortBy((_, v) => -v.size).take(10)
          .map { case ((subj, det), ss) => s"    ${ss.size} × $subj: $det" }
        (head :: sites).mkString("\n")
      }.mkString("\n")
