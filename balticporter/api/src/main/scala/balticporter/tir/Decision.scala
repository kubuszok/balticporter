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
    /** kind-specific pairs: `from`/`to` for a rename, `file` for an artefact copied in, `why` for
      * free text. Sorted at write time.
      *
      * '''Never restate what [[Reason]] already carries.''' A `Reason.Configured(phase, key)` IS
      * the policy entry, and both consumers print the two side by side — `PorterNote.pairs` emits
      * the classification and then this map, so a `key` here renders `key=… key=…` in the comment
      * beside the code, and `tsv` writes a `reason` column holding `phase:key` immediately before
      * a `detail` column repeating it. Three phases did exactly that and it reached emitted output;
      * the sites that remain are `PortRun`'s three drop loops (PROGRESS §12.4). Put a NARROWER or
      * DIFFERENT string here if a decider has one; never the same one. */
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
    *   - [[SubstitutedCall]]  — a CALL was replaced by a ready-made expression naming its receiver
    *                            and arguments. Distinct from [[RedirectedCall]], and the two are
    *                            not a candidate for merging: a redirect swaps the CALLEE and leaves
    *                            the call's shape alone, so the emitted line still reads as the
    *                            Java's call with a different name on it; a substitution replaces
    *                            the whole expression, and the result may be a field read, a `using`
    *                            application or nothing that looks like a call at all. Merged, "how
    *                            many call sites still resemble their Java" stops being answerable —
    *                            and it is the question that says whether a port's bodies can still
    *                            be diffed against upstream.
    *   - [[InjectedMember]]   — a definition in the output came from a hand-written file, not from
    *                            the frontend.
    *   - [[RedirectedCall]]   — a call site now names a different target (class table, static
    *                            forwarder, port map redirect).
    *   - [[RetypedSignature]] — a declaration's type changed (collections shims, opaque types,
    *                            raw-generic erasure).
    *   - [[ScopedOut]]        — the COMPLEMENT of the above, and it needs a kind of its own for the
    *                            reason `decisions.tsv` exists at all: a retyping rule's
    *                            [[RuleScope]] deliberately held this declaration back, so it keeps
    *                            its upstream type while the code around it moved. The row that
    *                            would have explained it is the one that is NOT there, and "why is
    *                            this field a `java.util.List` when every other file says `Buffer`"
    *                            then has no answer anywhere in the run. Always `Reason.Configured`
    *                            — an exclusion is a policy entry by construction.
    *   - [[FunnelledCtor]]    — Java's constructor set was funnelled into one primary plus
    *                            secondaries, promoting parameters and locals to members.
    *   - [[DroppedSuperCall]] — a secondary constructor's `super(args)` could not be expressed and
    *                            its arguments are gone (`ENGINE-LIMITS.md` C3). Distinct from
    *                            [[FunnelledCtor]], which is the nomination itself: one class may
    *                            funnel successfully and still drop one root's super arguments, and
    *                            merging them would make "how many paths lost their arguments"
    *                            unanswerable.
    *   - [[WidenedVisibility]]— a declaration ships WIDER than java wrote it. One kind for members
    *                            and types alike (DESIGN.md §8.7), with the `cause` pair saying
    *                            which residual it is: a REPLAYED parent constructor's statements
    *                            that must still reach a private member one level down; a `protected
    *                            static`, which a scala companion cannot express; a §4.55 field
    *                            RENAME, whose new name scala's own access rules do not make
    *                            reachable from everywhere java read the old one; a package the
    *                            port's own renames merged or split. The faithful part of the
    *                            visibility mapping records NOTHING — the diff is the change — so
    *                            every row here is a place the port could not be faithful.
    *   - [[DeferredInit]]     — a static's initialisation moved out of the class initialiser and
    *                            onto the first READ of it, because the initialiser needed a context
    *                            it had no signature to receive (DESIGN.md §8.4). It is the one
    *                            EAGER→LAZY change the engine makes, it is always asked for per site,
    *                            and it pays for a kind of its own because no existing one states
    *                            what changed: the declaration's TYPE is untouched, so
    *                            [[RetypedSignature]] would be a lie, and its body is not replaced,
    *                            so [[SubstitutedBody]] would be another. Java runs a class
    *                            initialiser at first ACTIVE USE of the class and the rewritten form
    *                            runs at first read of the field; a reader has to be told that, at
    *                            the declaration, which is why this kind carries a porter note.
    *   - [[Unrenderable]]     — the engine has no faithful Scala for this construct and said so in
    *                            the output (preview mode, [[balticporter.tir.Decision]] E9). Under
    *                            the shipping default the construct is refused and COUNTED instead
    *                            (`ENGINE-LIMITS.md` M6) and no row of this kind is written.
    */
  enum Kind:
    case RenamedType, RenamedPackage, RenamedMember
    case DroppedType, DroppedMember
    case SubstitutedBody, SubstitutedCall, InjectedMember
    case RedirectedCall, RetypedSignature, ScopedOut, FunnelledCtor
    case DroppedSuperCall, WidenedVisibility, Unrenderable, DeferredInit
    /** a PARENT this program's mapping could not move, because the target cannot BE a parent. */
    case RetainedParent
    /** a generic type ARGUMENT kept in the upstream namespace because a third party reifies it out
      * of the class file's generic signature (`ENGINE-LIMITS.md` K20) — jackson's `TypeReference`,
      * Gson's `TypeToken`, `java.lang.Class`. [[ScopedOut]]'s reasoning applies verbatim and is why
      * this is not folded into it: a declaration that kept its upstream type looks like a
      * translation nobody performed, and the diff shows nothing because nothing changed. It is not
      * [[ScopedOut]] itself because the two answer different questions — a scope holds a whole
      * DECLARATION back, and this holds back ONE POSITION inside a declaration whose every other
      * type moved, which is the fact a reader of that line needs. */
    case ReifiedTypeArg
    /** a value handed to an external REFLECTIVE SINK at an opaque slot, presented in java's own
      * representation at run time (`ENGINE-LIMITS.md` K21 face 1). [[ReifiedTypeArg]]'s third party
      * one end of the call over: that one reads the class file's TYPE ARGUMENTS, this one reads the
      * OBJECT. It is a separate kind because the two are fixed in different places — the carrier by
      * NOT retyping a position, this one by a bridge at the USE — and a reader who is told only
      * "the third party reifies things" cannot tell which of the two a line is. */
    case BridgedEgress
    /** java-bean accessors added beside a field java declared `public`, because scala emits no
      * public JVM field and a framework auto-detecting one sees nothing (`ENGINE-LIMITS.md` K21
      * face 2). An INVENTED member — no java declared it — which is `FunnelledCtor`'s case for
      * carrying a note: the reader is looking at a `def getA()` with no upstream line behind it,
      * and the source map cannot answer that. */
    case BeanAccessor

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

  /** WHERE a declaration lives — read from the TREE, never from the symbol.
    *
    * `Symbol.origin` exists but the frontend does not populate it: positions live on the tree
    * nodes, and every `Symbol` a run holds carries `Origin.synthetic`. A decision anchored on it
    * therefore writes `<synthetic>` in the one column that makes the row navigable — the same
    * unnavigable shape `PortRun`'s nested-drop rule already exists to prevent, arriving from the
    * other side. (Caught by a spec, not by a compile: the field is there and the type checks.)
    *
    * A symbol with no definition of its own borrows its OWNER's, which is right for the only thing
    * that matters here — the enclosing type is in the SAME FILE by construction, so the row stays
    * navigable and the line is the nearest one the run can honestly point at. Fuel-bounded, so a
    * corrupt owner chain cannot hang a recording pass.
    */
  def originOf(program: Program, s: SymId, fuel: Int = 16): Origin =
    program.definitionOf(s).map(_.origin).filter(o => o.javaPath.nonEmpty && o != Origin.synthetic) match
      case Some(o) => o
      case _ if fuel <= 0 => Origin.synthetic
      case _ =>
        program.symbolOf(s).map(_.owner).filter(_ != SymId.None)
          .map(originOf(program, _, fuel - 1)).getOrElse(Origin.synthetic)

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
