package balticporter.transform

import balticporter.tir.RuleScope

/** The POLICY half of [[GlobalsToImplicitsTransform]] — CLAUDE.md §1(b), as one value per holder.
  *
  * The mechanics of turning a static global into a threaded `using` parameter are the same for every
  * library; what differs is WHICH class is an ambient context, what its counterpart is called, which
  * of its fields map where, and what happens at the edges. All of that is here, so the engine names
  * no library and an empty list makes the phase a structural no-op.
  *
  * {{{
  * globals-to-implicits {
  *   holders = [{
  *     holder  = "com.foo.Gdx"                # UPSTREAM namespace (§4.56 — the rename runs last)
  *     context = { inject = "sge.Sge" }       # …or { mint = "com.foo.Sge" }
  *     members = { app = "application", gl = "graphics.gl20" }   # a PATH, not a member name
  *     attach   = "method" | "class"
  *     reader   = "summon" | "apply"
  *     boundary = "refuse" | "residual-global"
  *     sites    = { "com.foo.Utils#<clinit>" = "lazy-init" }
  *     selfSupplied = { "com.foo.FooTest" = "com.foo.TestFixture.ctx()" }
  *     promoteToClass = [ "com.foo.Viewport" ]
  *     scope    = { except = [ … ] }
  *   }]
  * }
  * }}}
  *
  * @param holder
  *   the class whose `static` members ARE the context, by fully-qualified UPSTREAM name.
  * @param context
  *   the type the context is threaded AS — injected (the port wrote it) or minted (the engine
  *   synthesises it).
  * @param members
  *   holder static field name → the ACCESS PATH on the context type. A dot-path, because the
  *   dominant rewrite in the reference hand port is two-hop: fields that were re-homed onto a
  *   service rather than onto the bundle (305 of 557 reads). A field ABSENT from the map is a
  *   residual global, counted.
  * @param attach
  *   WHERE the clause lands. `Method` puts a trailing `(using T)` on each threaded method — correct
  *   for statics and for the whole demand when class attachment is off. `Class` puts it on the
  *   class's constructors, so instance methods summon it with no signature change; that is the
  *   reference port's shape (82 % of its attachment sites are constructors).
  * @param reader
  *   how a read spells the context: `summon[T]`, or `T.apply()` for a context type that declares the
  *   `inline def apply()(using T): T` sugar.
  * @param boundary
  *   the DEFAULT for a site the closure cannot reach. `Refuse` leaves the read naming the upstream
  *   holder; `ResidualGlobal` rewrites it to the context companion's `global`. Both are counted; the
  *   difference is whether the emitted code still names the thing the port is retiring.
  * @param sites
  *   per-site overrides, keyed by MEMBER key (`com.foo.Utils#<clinit>`).
  * @param selfSupplied
  *   THE THIRD ANSWER (CLAUDE.md §1(b), `ENGINE-LIMITS.md` CT7), keyed by TYPE FQN: *this
  *   declaration takes the context WITHOUT taking a parameter*. Its constructors keep the signature
  *   java gave them and the engine emits `private given <context> = <this expression>` at the head
  *   of the type's body instead, so every `summon` inside it resolves to a value the PORT chose.
  *
  *   It exists because a class a FRAMEWORK instantiates has no caller to change: a test suite, a
  *   `ServiceLoader` implementation, a bean are constructed reflectively from OUTSIDE, the closure
  *   sees no instantiation at all, and a `using` clause on such a constructor emits code that
  *   compiles perfectly and cannot be constructed at run time. Measured: a whole suite disappeared
  *   at 0 scalac errors, 0 seams and 0 policy findings, and only `tests.tsv` saw it.
  *
  *   The VALUE is Scala, emitted verbatim exactly like `MethodBodyTransform`'s bodies, and it is
  *   written in the EMITTED namespace — it names a fixture the port hand-wrote, which the frontend
  *   never saw and the package rename therefore never rewrites (the same category as an INJECTED
  *   context type). CLAUDE.md §6 applies to what you write: fully-qualified, no imports.
  *
  *   WHICH declarations are framework-instantiated is not derivable — the closure only ever sees
  *   the program — so it is declared here. What IS derivable is the SHAPE, and the phase warns on
  *   it: a threaded class this program never constructs whose ancestry leaves it
  *   ([[ContextSeamCheck.Kind.UnconstructedThread]]).
  * @param promoteToClass
  *   traits this port allows to become `abstract class`es so they can carry a constructor clause.
  *   EXPLICIT rather than derived from "the trait's body needs the context": a predicate that
  *   silently changes a published type's KIND would flip under an unrelated upstream edit, and what
  *   a dependent may mix in is shared surface (§1.5). A promotion demand with no entry is a counted
  *   refusal naming the trait, whose fix is one manifest line.
  * @param scope
  *   the standard grammar. A read inside a scoped-out declaration is left exactly as it is and
  *   recorded as `ScopedOut`.
  */
final case class ContextHolder(
    holder: String,
    context: ContextType,
    members: Map[String, String] = Map.empty,
    attach: ContextAttach = ContextAttach.Method,
    reader: ContextReader = ContextReader.Summon,
    boundary: ContextBoundary = ContextBoundary.Refuse,
    sites: Map[String, ContextSite] = Map.empty,
    selfSupplied: Map[String, String] = Map.empty,
    promoteToClass: Set[String] = Set.empty,
    scope: RuleScope = RuleScope.everywhere,
):
  /** a stable, order-independent rendering — two modules that agree must compare equal (§1.5). */
  def fingerprint: String =
    val ms = members.toList.sorted.map((k, v) => s"$k->$v").mkString(",")
    val ss = sites.toList.map((k, v) => s"$k->${v.token}").sorted.mkString(",")
    // the SOURCE text is digested rather than spelled: it is Scala, it may hold every separator this
    // rendering uses, and two modules that supply different context sources for one declaration have
    // certainly made a mistake whether or not the strings are readable here.
    val fs = selfSupplied.toList.map((k, v) => s"$k=>${v.hashCode.toHexString}").sorted.mkString(",")
    s"$holder|${context.token}|$ms|${attach.token}|${reader.token}|${boundary.token}|$ss|$fs|" +
      s"${promoteToClass.toList.sorted.mkString(",")}|${scope.fingerprint}"

/** WHERE the context type comes from.
  *
  * Both are supported because the two answers are wanted for different reasons: a hand port writes
  * an immutable case class with `@implicitNotFound`, a private constructor and accessor sugar, none
  * of which a mechanism should try to guess — that is `Injected`. A port that only needs the statics
  * moved off a global takes `Minted` and gets one mutable member per mapped field.
  *
  * An INJECTED type is Scala the frontend never saw, so the engine cannot validate the member paths
  * against it: a path naming a member that is not there is a compile error at that one line, which
  * the source map attributes. A MINTED type is fully known, so its paths ARE validated — a two-hop
  * path is refused at bind time, since the engine has no intermediate type to hang the second hop
  * off.
  */
enum ContextType(val fqn: String):
  case Injected(override val fqn: String) extends ContextType(fqn)
  case Minted(override val fqn: String)   extends ContextType(fqn)

  def token: String = this match
    case Injected(f) => s"inject:$f"
    case Minted(f)   => s"mint:$f"

/** WHERE the `using` clause lands. See [[ContextHolder.attach]]. */
enum ContextAttach(val token: String):
  case Method extends ContextAttach("method")
  case Class  extends ContextAttach("class")

/** HOW a read spells the context. */
enum ContextReader(val token: String):
  case Summon extends ContextReader("summon")
  case Apply  extends ContextReader("apply")

/** the DEFAULT for a site the closure cannot reach. */
enum ContextBoundary(val token: String):
  case Refuse         extends ContextBoundary("refuse")
  case ResidualGlobal extends ContextBoundary("residual-global")

/** a PER-SITE override of [[ContextBoundary]].
  *
  * [[LazyInit]] is the one that changes semantics and it is therefore never a default: Java runs a
  * class initialiser at first ACTIVE USE of the class, and the rewritten form runs at first READ of
  * the field. The two coincide only when nothing else in the class is touched first, and the
  * mechanism cannot know that — so it is per-site opt-in, a `DeferredInit` decision, a porter note
  * and a counted seam.
  */
enum ContextSite(val token: String):
  case LazyInit       extends ContextSite("lazy-init")
  case ResidualGlobal extends ContextSite("residual-global")
  case Refuse         extends ContextSite("refuse")

object ContextSite:
  def fromToken(s: String): Option[ContextSite] = values.find(_.token == s)

object ContextAttach:
  def fromToken(s: String): Option[ContextAttach] = values.find(_.token == s)

object ContextReader:
  def fromToken(s: String): Option[ContextReader] = values.find(_.token == s)

object ContextBoundary:
  def fromToken(s: String): Option[ContextBoundary] = values.find(_.token == s)
