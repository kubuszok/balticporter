package balticporter.tir

import java.nio.file.{Files, Path}

/** DECISION PROVENANCE — why the emitted code looks the way it does.
  *
  * ## The problem this closes
  *
  * [[SrcMap]] answers "which Java produced this Scala"; it does not answer "why is this Scala not a
  * mechanical translation of that Java". Every non-obvious shape in the output — a type that is
  * simply absent, a package that is not the upstream one, a member that came from a hand-written
  * file rather than from the frontend — is the result of a DECISION some part of the engine made,
  * and until now the only record of it was the prose in a scaladoc plus whatever the operator
  * happened to read on stdout. An agent in another repository (CLAUDE.md §4.45) investigating
  * `sge.utils.Json` has no way to learn that the type it is looking at is injected because the
  * manifest dropped `com.badlogic.gdx.utils.Json`, short of reading the porting program.
  *
  * A `Decision` is that record, made durable and machine-joinable (`decisions.tsv`) beside the
  * source map, the findings and the port map.
  *
  * ## Every decision carries its §1 CLASSIFICATION, and that is not optional
  *
  * [[Reason]] is a constructor parameter, not a free-text field, because the first question an
  * investigating agent has is CLAUDE.md §1's: is this the engine's doing (a), a policy entry it can
  * change (b), or a rule written for one library (c)? A note that says what happened without saying
  * which of the three it is costs a full investigation to classify — which is the same reason
  * `PortReport.Kind` and every `ENGINE-LIMITS.md` entry carry one. Free text is still welcome; it
  * goes in `detail("why")`, where it cannot be mistaken for the classification.
  *
  * ## Why the FQN is captured at decision time
  *
  * `subject` is a [[SymId]], which is interning order and dies with the run — it is carried because
  * a phase that wants to join two decisions in-process has nothing better, and it is deliberately
  * NOT written to the artifact (the same rule `CheckReport` states for findings). `subjectFqn` is
  * the name the subject had WHEN THE DECISION WAS MADE, which is the only form that survives: a
  * package rename runs last (§4.56), so a decision recorded before it names an upstream symbol and
  * a decision recorded by it names both sides. Re-deriving the name at write time would silently
  * relabel every earlier decision into the emitted namespace.
  */
final case class Decision(
    kind: Decision.Kind,
    /** the symbol the decision is ABOUT, when the decider holds one. `SymId.None` for a decision
      * about a policy key that matched nothing, or about a file that never entered the TIR. */
    subject: SymId,
    /** the subject's fully-qualified name AT DECISION TIME — see the class doc. */
    subjectFqn: String,
    /** kind-specific pairs: `from`/`to` for a rename, `key` for the policy entry that fired,
      * `file` for an artefact copied in, `why` for free text. Sorted at write time. */
    detail: Map[String, String],
    reason: Reason,
    origin: Origin,
):
  def tsv: String =
    val d = detail.toList.sorted.map((k, v) => s"$k=${Decision.clean(v)}").mkString("; ")
    s"${kind.toString}\t${Decision.clean(subjectFqn)}\t${reason.className}\t${Decision.clean(reason.detail)}" +
      s"\t${CheckReport.relativise(origin.javaPath)}\t${origin.line}\t${Decision.clean(d)}"

  def render: String =
    val d = detail.toList.sorted.map((k, v) => s"$k=$v").mkString(", ")
    s"$kind $subjectFqn [${reason.render}]${if d.isEmpty then "" else s" ($d)"}"

object Decision:

  /** What was decided. CLOSED on purpose: an open string would make `decisions.tsv` ungroupable and
    * let two deciders describe the same act two ways. Add a case when a decider needs one — the
    * cost of the enum is the one edit that forces the name to be agreed.
    *
    *   - [[RenamedType]]      — a type is emitted under a different NAME (not merely a different
    *                            package): a shadowing fix, a collision with a Scala keyword.
    *   - [[RenamedPackage]]   — a symbol's namespace moved ([[balticporter.transform.PackageRenameTransform]]).
    *   - [[RenamedMember]]    — a field or method was renamed, e.g. because Java allows a name Scala
    *                            does not (§4.55).
    *   - [[DroppedType]]      — a type is deliberately NOT emitted; something else supplies its FQN.
    *   - [[DroppedMember]]    — one member is deliberately not emitted, the rest of its type is.
    *   - [[SubstitutedBody]]  — a method KEPT its signature and had its body replaced.
    *   - [[InjectedMember]]   — a definition in the output came from a hand-written file, not from
    *                            the frontend.
    *   - [[RedirectedCall]]   — a call site now names a different target (class table, static
    *                            forwarder, port map redirect).
    *   - [[RetypedSignature]] — a declaration's type changed (collections shims, opaque types,
    *                            raw-generic erasure).
    *   - [[FunnelledCtor]]    — Java's constructor set was funnelled into one primary plus
    *                            secondaries, promoting parameters and locals to members.
    */
  enum Kind:
    case RenamedType, RenamedPackage, RenamedMember
    case DroppedType, DroppedMember
    case SubstitutedBody, InjectedMember
    case RedirectedCall, RetypedSignature, FunnelledCtor

  val Header = "#kind\tsubjectFqn\treasonClass\treasonDetail\torigin\tline\tdetail"

  /** The DECLARATIONS a per-SITE rewrite reached, each with the earliest origin inside it.
    *
    * Every redirect phase rewrites EXPRESSIONS, and every one of them nevertheless records once per
    * DECLARATION. The reader is an agent diffing an emitted file against its upstream Java, and a
    * site-level rewrite is already visible in that diff — `ClassReflection.forName(s)` reads as
    * `AssetTypeRegistry.classFor(s)` right there. What the diff cannot say is WHICH POLICY ENTRY
    * did it, and that is one fact per (declaration, key), not one per occurrence. Recorded per site
    * it would be the same sentence 240 times, burying every decision that is not a redirect —
    * which is the failure `PortMapTransform.callSites` already documents for a per-site FINDING.
    *
    * The enclosing declaration is read from the xref, which records it on every usage
    * ([[Usage.enclosing]]); a phase that tracked "the definition I am currently inside" with its
    * own walk would be the hand-rolled traversal CLAUDE.md §3 forbids. A usage recorded outside any
    * definition keeps `SymId.None` and is reported under the callee's own name rather than dropped.
    *
    * The origin is the EARLIEST site in the declaration, by (file, line), so two runs of the same
    * program agree on it whatever order the xref hands the usages back in.
    */
  def declarationsUsing(program: Program, sym: SymId): List[(SymId, Origin)] =
    program
      .usages(sym)
      .groupBy(_.enclosing)
      .toList
      .flatMap { (encl, us) =>
        val os = us.map(_.site.origin)
        os.filter(_.javaPath.nonEmpty).minByOption(o => (o.javaPath, o.line))
          .orElse(os.headOption)
          .map(encl -> _)
      }
      .sortBy((encl, o) => (o.javaPath, o.line, encl.raw))

  /** the symbol's name, or `fallback` when the run no longer holds it (`SymId.None` for a usage
    * outside any definition). Never an empty subject: a row whose subject cannot be named is a row
    * nobody can join. */
  def fqnOf(program: Program, s: SymId, fallback: String): String =
    program.symbolOf(s).map(_.fullName).filter(_.nonEmpty).getOrElse(fallback)

  /** Is `s` a DECLARATION in the sense this channel records — a class, a field or a method — as
    * opposed to a parameter, a type parameter or a method-local?
    *
    * A retyping phase rewrites every symbol's `info`, parameters and locals included, and each of
    * those is ALREADY covered by the declaration that encloses it: a method's `info` is a
    * `MethodType` carrying its parameter types, so a parameter whose type moved moved the method's
    * signature and is one decision, not two. Recording both restates one fact per parameter, which
    * on libGDX is several thousand rows saying what the method's row already said.
    *
    * Decided STRUCTURALLY, from the owner chain — a parameter's and a local's owner is the METHOD
    * (`SpoonTir` interns them that way), a member's owner is a TYPE. Not from the `isParam` flag
    * alone, which locals do not carry.
    */
  def isDeclaration(program: Program, s: Symbol): Boolean =
    !s.flags.isParam && !program.symbolOf(s.owner).exists(o => isMethodLike(o.info))

  private def isMethodLike(t: TypeRepr): Boolean = t match
    case _: TypeRepr.MethodType => true
    case _: TypeRepr.PolyType   => true
    case _                      => false

  private[tir] def clean(s: String): String =
    s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim

  /** Write one run's decisions. Sorted on every column that appears in the file — the artifact is
    * meant to be diffed, so accumulation order (which is phase order, i.e. an implementation
    * detail) must not reach it. `SymId` is not written, for the reason [[CheckReport]] gives:
    * interning order changes when an unrelated file is added. */
  def write(out: Path, log: DecisionLog): Path =
    val all = log.all.sortBy(d =>
      (d.kind.toString, d.subjectFqn, d.reason.className, d.reason.detail, d.origin.javaPath, d.origin.line, d.tsv))
    Files.createDirectories(out)
    val p = out.resolve("decisions.tsv")
    Files.writeString(p, (Header :: all.map(_.tsv)).mkString("", "\n", "\n"))
    p

  def parse(line: String): Option[Decision] =
    if line.startsWith("#") || line.isBlank then scala.None
    else
      // `-1`, so an EMPTY trailing column is a column. A decision with no `detail` pairs (or no
      // origin path) is ordinary, and the default split drops trailing empties — which turned every
      // such row into an unparseable line, silently, in a file whose whole purpose is to be read.
      line.split("\t", -1) match
        case Array(k, fqn, rc, rd, path, ln, det) =>
          Kind.values.find(_.toString == k).map { kind =>
            Decision(kind, SymId.None, fqn, parseDetail(det), Reason.parse(rc, rd),
              Origin(path, ln.toIntOption.getOrElse(0), 0))
          }
        case _ => scala.None

  def parseAll(p: Path): List[Decision] =
    if !Files.isRegularFile(p) then Nil
    else Files.readAllLines(p).toArray(Array.empty[String]).toList.flatMap(parse)

  private def parseDetail(s: String): Map[String, String] =
    if s.isBlank then Map.empty
    else s.split("; ").toList.flatMap { kv =>
      val i = kv.indexOf('=')
      if i < 0 then scala.None else Some(kv.substring(0, i) -> kv.substring(i + 1))
    }.toMap

/** WHY a decision was made, in CLAUDE.md §1's three kinds. Mandatory on every [[Decision]] — see
  * `Decision`'s class doc for why this is a type and not a sentence.
  *
  *   - [[Reason.Universal]]   — §1(a): a fact about Java and Scala, true of every codebase. The
  *                              `rule` names it (e.g. "java-static-inherited-constant").
  *   - [[Reason.Configured]]  — §1(b): a parameterised mechanism fired on a POLICY ENTRY. `phase`
  *                              is the mechanism, `key` the entry — which together are exactly what
  *                              an agent must edit in the library's manifest to change the outcome.
  *   - [[Reason.LibraryRule]] — §1(c): a rule that could only ever apply to one library, plugged in
  *                              by the porting program. `rule` names it.
  */
enum Reason:
  case Universal(rule: String)
  case Configured(phase: String, key: String)
  case LibraryRule(rule: String)

  /** the §1 kind, as the one token `decisions.tsv` groups by. */
  def className: String = this match
    case Universal(_)     => "universal"
    case Configured(_, _) => "configured"
    case LibraryRule(_)   => "library-rule"

  /** the rest of the reason, in one column. For [[Reason.Configured]] the two halves are joined by
    * `:` and split again at the FIRST one — a phase name never contains a colon, and a policy key
    * that did would still round-trip. */
  def detail: String = this match
    case Universal(r)     => r
    case Configured(p, k) => s"$p:$k"
    case LibraryRule(r)   => r

  /** which of §1's three kinds a reader must act in — the whole point of the classification. */
  def section: String = this match
    case Universal(_)     => "§1(a) ENGINE"
    case Configured(_, _) => "§1(b) PER-LIBRARY POLICY"
    case LibraryRule(_)   => "§1(c) LIBRARY RULE"

  def render: String = s"$section: $detail"

object Reason:
  def parse(className: String, detail: String): Reason = className match
    case "configured" =>
      val i = detail.indexOf(':')
      if i < 0 then Configured(detail, "") else Configured(detail.substring(0, i), detail.substring(i + 1))
    case "library-rule" => LibraryRule(detail)
    case _              => Universal(detail)

/** One RUN's decisions — a value the [[Pipeline]] owns and hands back, never a process-global
  * table.
  *
  * This is the same rule §5.1 states for the source map, and for the same measured reason: sbt runs
  * every suite in one JVM, so a global accumulates two runs' decisions into one artifact and a
  * spec can only survive by filtering the global by name. A log belongs to the run it describes.
  *
  * Thread-safe because a phase may one day walk units in parallel, and because the cost of a
  * concurrent queue is nothing beside the cost of finding out that it was needed.
  */
final class DecisionLog:
  private val q = new java.util.concurrent.ConcurrentLinkedQueue[Decision]()

  def record(d: Decision): Unit = q.add(d)
  def recordAll(ds: IterableOnce[Decision]): Unit = ds.iterator.foreach(record)

  /** in RECORDING order. The artifact sorts; a caller that wants to see what happened when does
    * not. */
  def all: List[Decision] =
    scala.jdk.CollectionConverters.IteratorHasAsScala(q.iterator()).asScala.toList

  def size: Int        = q.size
  def isEmpty: Boolean = q.isEmpty
  def nonEmpty: Boolean = !isEmpty
  def of(kind: Decision.Kind): List[Decision] = all.filter(_.kind == kind)

  /** everything recorded so far, with the log emptied — how the [[Pipeline]] moves a phase's
    * decisions into the run's log without the phase's buffer outliving the run. */
  def drain(): List[Decision] =
    val out = all
    q.clear()
    out

  def clear(): Unit = q.clear()

  def counts: Map[Decision.Kind, Int] = all.groupBy(_.kind).view.mapValues(_.size).toMap

  /** one line per kind, for stdout. */
  def summary: String =
    if isEmpty then "no decisions recorded"
    else counts.toList.sortBy(_._1.toString).map((k, n) => s"$k=$n").mkString(", ")
