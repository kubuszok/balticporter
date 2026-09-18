package balticporter.tir

import java.nio.file.{ Files, Path }

/** Decision provenance — why the emitted code looks the way it does. [[SrcMap]] answers "which Java produced this Scala"; a `Decision` answers "why is this not a mechanical translation" — durable and
  * machine-joinable (`decisions.tsv`), for an agent in another repository. [[Reason]] classifies it into the three kinds; `subjectFqn` captures the name at decision time, since a package rename runs
  * last and re-deriving it later would relabel old decisions.
  */
final case class Decision(
  kind: Decision.Kind,
  /** the symbol the decision is ABOUT, when the decider holds one. `SymId.None` for a decision about a policy key that matched nothing, or about a file that never entered the TIR.
    */
  subject: SymId,
  /** the subject's fully-qualified name AT DECISION TIME — see the class doc. */
  subjectFqn: String,
  /** kind-specific pairs: `from`/`to` for a rename, `file` for an artefact copied in, `why` for free text. Sorted at write time. NEVER restate what [[Reason]] already carries — a
    * `Reason.Configured(phase, key)` IS the policy entry and both consumers print it beside this map, so a `key` here duplicates it in output; use a NARROWER or DIFFERENT string instead.
    */
  detail: Map[String, String],
  reason: Reason,
  origin: Origin
):
  def tsv: String =
    val d = detail.toList.sorted.map((k, v) => s"$k=${Decision.clean(v)}").mkString("; ")
    s"${kind.toString}\t${Decision.clean(subjectFqn)}\t${reason.className}\t${Decision.clean(reason.detail)}" +
      s"\t${CheckReport.relativise(origin.javaPath)}\t${origin.line}\t${Decision.clean(d)}"

  def render: String =
    val d = detail.toList.sorted.map((k, v) => s"$k=$v").mkString(", ")
    s"$kind $subjectFqn [${reason.render}]${if d.isEmpty then "" else s" ($d)"}"

object Decision:

  /** What was decided. CLOSED on purpose: an open string would make `decisions.tsv` ungroupable. Renamed{Type,Package,Member}, Dropped{Type,Member}, Substituted{Body,Call}, InjectedMember,
    * RedirectedCall, RetypedSignature, ScopedOut, FunnelledCtor, DroppedSuperCall, WidenedVisibility, DeferredInit, Unrenderable — see each case's own doc where one exists.
    */
  enum Kind:
    case RenamedType, RenamedPackage, RenamedMember
    case DroppedType, DroppedMember
    case SubstitutedBody, SubstitutedCall, InjectedMember
    case RedirectedCall, RetypedSignature, ScopedOut, FunnelledCtor
    case DroppedSuperCall, WidenedVisibility, Unrenderable, DeferredInit

    /** a PARENT this program's mapping could not move, because the target cannot BE a parent. */
    case RetainedParent

    /** a generic type argument kept in the upstream namespace because a third party reifies it out of the class file's generic signature — jackson's `TypeReference`, Gson's `TypeToken`,
      * `java.lang.Class`. Not [[ScopedOut]]: that holds back a whole declaration, this holds back one position inside a declaration whose every other type moved.
      */
    case ReifiedTypeArg

    /** a value handed to an external reflective sink at an opaque slot, presented in java's own representation at run time. The other end of [[ReifiedTypeArg]]'s call: that reads the class file's
      * type arguments, this reads the object — fixed differently (a carrier not retyping a position vs. a bridge at the use).
      */
    case BridgedEgress

    /** java-bean accessors added beside a field java declared `public`, because scala emits no public JVM field and a framework auto-detecting one sees nothing. An invented member — no java declared
      * it — which is `FunnelledCtor`'s case for carrying a note: the reader is looking at a `def getA()` with no upstream line behind it, and the source map cannot answer that.
      */
    case BeanAccessor

    /** a statement invented to touch this type's companion `object`, because java initialises the class at that moment and scala initialises the object at a different one (JLS 12.4.1). Invisible from
      * its own text (`val _ = com.foo.T` reads as dead code); the detail says which of java's triggers this statement stands for.
      */
    case ForcedClassInit

    /** a java `sealed` hierarchy shipped as an ordinary OPEN type, since scala's `sealed` is file-scoped with no cross-file `permits` (JLS 8.1.1.2, catalog `JS-C44`). Apart from [[WidenedVisibility]]
      * (access level, program-wide) — this is about who may EXTEND, decided at the declaration. DETAIL: how many subtypes declared, how many leave this file.
      */
    case WidenedSeal

    /** the `equals`/`hashCode`/`toString` javac derives from a record's components (JLS 8.10.3), written out, plus the `unapply` scala needs and java lacks (catalog `JS-C43`) — no java line declares
      * any of these, so the source map has nothing to point at. DETAIL: which of the four were written vs. record-declared, and that `Class.isRecord` is false on the emitted class.
      */
    case RecordMembers

    /** an anonymous SAM-implementing class was emitted as a lambda instead — correct but not the mechanical translation, so the reader is owed why. Also: java's anon class has a stable name
      * (`Outer$1`) a `getClass()`/log line can print; a lambda's is a hidden-class name. No structural guard can catch that — `was=` is the name it had.
      */
    case SamLambda

    /** a java bean pair over a trivial backing field was emitted as a scala `var`/`val`, accessors deleted — already correct as `def x`/`def x_=`, so the reader is owed why it went further: the JVM
      * method names moved (`getName()`/`setName()` → `name()`/`name_$eq()`), invisible to compiler/count/test, visible to reflection. `was=`.
      */
    case CollapsedProperty

    /** the port selected one of the remedies a phase or check offered at this declaration ([[Remedy]], [[AppliedResolution]]) — a kind of its own because the fact explaining the emitted text is one
      * word in the manifest, not code. Detail: the remedy's id, the lane it drained, and which of the three kinds carried it out.
      */
    case SelectedRemedy

    /** a declaration's type was not retyped because it overrides a signature living in a compiled class file — unowned signatures are facts, no phase may move them. Not [[ScopedOut]] (a policy key,
      * editable) — universal refusal with no key anywhere. Not [[RetainedParent]] either (keeps a parent, not a member's formals).
      */
    case RetainedSignature

    /** the `override` modifier java's hierarchy justified was removed, because the parent that justified it is not the parent the port emits — the modifier catching up with a re-parented class, not a
      * member repair. Not [[RetypedSignature]]: nothing about the member's type moved. Detail carries the parent the java no longer names.
      */
    case StrippedOverride

    /** a parent the mapping minted was dropped because another minted parent already carries the relation java wrote it for — mirror of [[RetainedParent]] (which keeps java's parent). Two minted
      * parents can declare one member at two arities, which cannot compile; detail carries the parent that subsumes it.
      */
    case SubsumedParent

    /** a member the minted parent declares, synthesised over the java member it renamed out of the way — [[StrippedOverride]]'s other half, where java's member is the wrong shape for the trait it
      * owes (e.g. `put(K,V): V` vs `Option[V]`). Detail carries the renamed java member it delegates to, and the guard when the bridge is refused.
      */
    case BridgedMember

    /** a converted test class rebuilds its own instance state before every test, because JUnit 4 constructs a fresh instance per `@Test` and MUnit runs one suite instance — the initialiser moves into
      * a member no java file declares. Detail: how many fields hoisted, whether the ctor body replayed, which ancestor it chains to.
      */
    case RebuiltPerTest

    /** a nullary java method whose `()` was dropped, making it a scala PARAMETERLESS `def` — sge's empirical getter convention. Already compiles and behaves identically either way, so the reader is
      * owed why. DETAIL: `from` (original `name()`), `to` (parameterless `name`).
      */
    case ParenlessConversion

    /** `@scala.annotation.nowarn("msg=deprecated")` added to a member whose body calls a method the target library deliberately deprecates as lint (e.g. sge's `orNull`). About a USAGE inside the
      * member, not its type/body, so not folded into [[RetypedSignature]]. DETAIL: which method triggered the suppression.
      */
    case SuppressedWarning

    /** a `(using GivenType[T])` clause added to a class's constructors because a retarget construction inside its body needs the given in scope — the class's callers supply it by inline given
      * resolution.
      */
    case RequiredGiven

    /** a local/private member was deleted, its binding discarded (side-effecting init kept as a bare expression), or suppressed with `@nowarn("msg=unused")`, because `-Wunused:locals,privates` under
      * `-Werror` reports what java compiles silently. Detail names which of the three sub-actions and why.
      */
    case UnusedSymbolHandled

    /** a member ADDED to a class by `AddMembersTransform` — verbatim Scala spliced at the end of the owner's body, for a member the hand port wrote that upstream java does not declare. Unlike
      * [[InjectedMember]] (a whole file), this is one member in a translated class. DETAIL: what was added (`member`, `arity`) and the reference port's source.
      */
    case AddedMember

    /** a field write was dropped because the target's field is immutable (a constructor parameter or absent) and the write is semantically a no-op. Detail: the field name and the reason.
      */
    case DroppedFieldWrite

    /** a java `static final` scratch field made THREAD-CONFINED by `ThreadConfinedStaticsTransform`: the companion `val` became a `def` over a `ThreadLocal` holder initialised per thread from the
      * java initialiser, so the reference `Owner.f` keeps compiling and each thread reads its own instance. DETAIL: the holder's name and the shape the field had.
      */
    case ThreadConfinedStatic

  val Header = "#kind\tsubjectFqn\treasonClass\treasonDetail\torigin\tline\tdetail"

  /** The DECLARATIONS a per-SITE rewrite reached, each with the earliest origin inside it. Recorded once per declaration (not per occurrence) since a site-level rewrite is already visible in the diff
    * — what the diff can't say is WHICH POLICY ENTRY did it. Enclosing declaration read from the xref ([[Usage.enclosing]]); origin is the earliest (file, line), for determinism.
    */
  def declarationsUsing(program: Program, sym: SymId): List[(SymId, Origin)] =
    program
      .usages(sym)
      .groupBy(_.enclosing)
      .toList
      .flatMap { (encl, us) =>
        val os = us.map(_.site.origin)
        os.filter(_.javaPath.nonEmpty).minByOption(o => (o.javaPath, o.line)).orElse(os.headOption).map(encl -> _)
      }
      .sortBy((encl, o) => (o.javaPath, o.line, encl.raw))

  /** the symbol's name, or `fallback` when the run no longer holds it (`SymId.None` for a usage outside any definition). Never an empty subject: a row whose subject cannot be named is a row nobody
    * can join.
    */
  def fqnOf(program: Program, s: SymId, fallback: String): String =
    program.symbolOf(s).map(_.fullName).filter(_.nonEmpty).getOrElse(fallback)

  /** WHERE a declaration lives — read from the TREE, never from the symbol (`Symbol.origin` is unpopulated; every `Symbol` carries `Origin.synthetic`). A symbol with no definition of its own borrows
    * its OWNER's, keeping the row navigable in the same file. Fuel-bounded, so a corrupt owner chain cannot hang a recording pass.
    */
  def originOf(program: Program, s: SymId, fuel: Int = 16): Origin =
    program.definitionOf(s).map(_.origin).filter(o => o.javaPath.nonEmpty && o != Origin.synthetic) match
      case Some(o)        => o
      case _ if fuel <= 0 => Origin.synthetic
      case _              =>
        program.symbolOf(s).map(_.owner).filter(_ != SymId.None).map(originOf(program, _, fuel - 1)).getOrElse(Origin.synthetic)

  /** Is `s` a DECLARATION this channel records — a class, field or method — as opposed to a parameter/type-parameter/local, whose retyping is already covered by the enclosing declaration's own
    * `info`. Decided STRUCTURALLY from the owner chain: a parameter's/local's owner is the METHOD, a member's owner is a TYPE — never from the `isParam` flag alone.
    */
  def isDeclaration(program: Program, s: Symbol): Boolean =
    !s.flags.isParam && !program.symbolOf(s.owner).exists(o => isMethodLike(o.info))

  /** …and the stricter question a per-location policy key asks: can `owner#member` name this? [[isDeclaration]] is generous (an anon class's method counts); this refuses it — the owner's `fullName`
    * is minted from a per-class counter that renumbers on unrelated edits. Both the check and the remedy-applier must read the same function here, or they can attribute one call to two different
    * declarations.
    */
  def isKeyable(program: Program, s: SymId): Boolean =
    program.symbolOf(s).map(_.owner).flatMap(program.definitionOf).exists(_.isInstanceOf[Tree.ClassDef])

  private def isMethodLike(t: TypeRepr): Boolean = t match
    case _: TypeRepr.MethodType => true
    case _: TypeRepr.PolyType   => true
    case _ => false

  private[tir] def clean(s: String): String =
    s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim

  /** Write one run's decisions. Sorted on every column that appears in the file — the artifact is meant to be diffed, so accumulation order (which is phase order, i.e. an implementation detail) must
    * not reach it. `SymId` is not written, for the reason [[CheckReport]] gives: interning order changes when an unrelated file is added.
    */
  def write(out: Path, log: DecisionLog): Path =
    val all = log.all.sortBy(d => (d.kind.toString, d.subjectFqn, d.reason.className, d.reason.detail, d.origin.javaPath, d.origin.line, d.tsv))
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
            Decision(kind, SymId.None, fqn, parseDetail(det), Reason.parse(rc, rd), Origin(path, ln.toIntOption.getOrElse(0), 0))
          }
        case _ => scala.None

  def parseAll(p: Path): List[Decision] =
    if !Files.isRegularFile(p) then Nil
    else Files.readAllLines(p).toArray(Array.empty[String]).toList.flatMap(parse)

  private def parseDetail(s: String): Map[String, String] =
    if s.isBlank then Map.empty
    else
      s.split("; ")
        .toList
        .flatMap { kv =>
          val i = kv.indexOf('=')
          if i < 0 then scala.None else Some(kv.substring(0, i) -> kv.substring(i + 1))
        }
        .toMap

/** Why a decision was made, in the three kinds — mandatory on every [[Decision]]. [[Reason.Universal]] (`rule` names it), [[Reason.Configured]] (`phase`+`key` name the manifest entry to edit),
  * [[Reason.LibraryRule]] (`rule` names the plugged-in rule).
  */
enum Reason:
  case Universal(rule: String)
  case Configured(phase: String, key: String)
  case LibraryRule(rule: String)

  /** the classification, as the one token `decisions.tsv` groups by. */
  def className: String = this match
    case Universal(_)     => "universal"
    case Configured(_, _) => "configured"
    case LibraryRule(_)   => "library-rule"

  /** the rest of the reason, in one column. For [[Reason.Configured]] the two halves are joined by `:` and split again at the FIRST one — a phase name never contains a colon, and a policy key that
    * did would still round-trip.
    */
  def detail: String = this match
    case Universal(r)     => r
    case Configured(p, k) => s"$p:$k"
    case LibraryRule(r)   => r

  /** which of the three kinds a reader must act in — the whole point of the classification. */
  def section: String = this match
    case Universal(_)     => "engine (true of every Java program)"
    case Configured(_, _) => "port policy"
    case LibraryRule(_)   => "library-specific rule"

  def render: String = s"$section: $detail"

object Reason:
  def parse(className: String, detail: String): Reason = className match
    case "configured" =>
      val i = detail.indexOf(':')
      if i < 0 then Configured(detail, "") else Configured(detail.substring(0, i), detail.substring(i + 1))
    case "library-rule" => LibraryRule(detail)
    case _              => Universal(detail)

/** One run's decisions — a value the [[Pipeline]] owns and hands back, never a process-global table (sbt runs every suite in one JVM, so a global would accumulate two runs' decisions into one
  * artifact). Thread-safe for a future parallel walk.
  */
final class DecisionLog:
  private val q = new java.util.concurrent.ConcurrentLinkedQueue[Decision]()

  def record(d:     Decision):               Unit = q.add(d)
  def recordAll(ds: IterableOnce[Decision]): Unit = ds.iterator.foreach(record)

  /** in RECORDING order. The artifact sorts; a caller that wants to see what happened when does not.
    */
  def all: List[Decision] =
    scala.jdk.CollectionConverters.IteratorHasAsScala(q.iterator()).asScala.toList

  def size:                    Int            = q.size
  def isEmpty:                 Boolean        = q.isEmpty
  def nonEmpty:                Boolean        = !isEmpty
  def of(kind: Decision.Kind): List[Decision] = all.filter(_.kind == kind)

  /** everything recorded so far, with the log emptied — how the [[Pipeline]] moves a phase's decisions into the run's log without the phase's buffer outliving the run.
    */
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
