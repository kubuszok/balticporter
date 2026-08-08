package balticporter.transform

import balticporter.tir.*

/** THE ANONYMOUS-SAM DECISION, STATED ONCE — read by the census that measures it and by the
  * transformer that acts on it.
  *
  * ==What this is about==
  * A java anonymous class implementing a single-abstract-method interface and a scala lambda at
  * that same interface are the same value with two spellings. The faithful translation — the
  * anonymous class — already compiles and already behaves correctly, so this is not a `JS-` row and
  * this layer has no difference to discharge (`DESIGN.md` §8.15 states what licenses it instead).
  *
  * ==Why one function and not two==
  * `CLAUDE.md` §4.6's one-mechanism-one-seam. The census publishes the DENOMINATOR before anything
  * converts, and the transformer converts; a second copy of the guards would be free to disagree
  * with the first, and the disagreement would look exactly like a real change in the population.
  * So the guards live here, both consumers call [[decide]], and the census is the transformer with
  * the rewrite removed rather than a re-derivation of its question.
  *
  * ==THE DELTA ENUMERATION — the whole safety argument==
  * An idiom transform's safety argument is a REFUSAL ENUMERATION, not a suite result. The
  * behavioural differences between `new I(){ … }` and a lambda at `I` are these, and each is
  * either made impossible by a guard, made impossible by the SHAPE the transformer emits, or
  * COUNTED:
  *
  *   1. '''the target may not be a functional interface at all''' — guard [[Guard.NotSam]];
  *   2. '''the class file may be unreadable''' — guard [[Guard.Unreadable]]. Counted rather than
  *      assumed either way (§4.6: a default indistinguishable from a real answer is a fabricated
  *      fact);
  *   3. '''the body may be more than the one method''' — a field, an instance initialiser, a helper,
  *      a nested type, or a member the frontend could not translate. Guard [[Guard.BodyNotSingle]];
  *   4. '''`this` inside a java anonymous class is the ANON INSTANCE; inside a scala lambda it is
  *      the ENCLOSING class.''' Guard [[Guard.SelfReference]] — a §4.4-class meaning change that
  *      compiles perfectly either way. Note the test is on the `cls` SYMBOL and not on the node
  *      kind: a QUALIFIED outer `this` is fine and must not be refused, and refusing it would
  *      decline most of the real population;
  *   5. '''INSTANCE IDENTITY.''' `new Runnable(){…}` allocates a fresh object at every evaluation.
  *      A scala lambda at a SAM slot goes through `LambdaMetafactory`, and '''JVMS 5.4.3.6 and the
  *      `LambdaMetafactory` contract leave instance identity UNSPECIFIED, in both directions''' —
  *      a JDK may cache a non-capturing lambda, may not, may cache at one call site and not
  *      another, and may change between releases. What licenses guard [[Guard.NonCapturing]] is
  *      therefore the SPECIFICATION'S SILENCE, never a measurement: a program whose behaviour
  *      depends on an unspecified identity is one the port must not create. Written the other way
  *      round ("this JDK caches, so refuse") the guard would read as removable the day somebody
  *      measures a JDK that does not, which is exactly backwards — the next JDK is not bound by
  *      that measurement. A CAPTURING lambda allocates per evaluation, so the guard closes the
  *      whole gap structurally and costs only the non-capturing sites;
  *   6. '''SERIALIZATION.''' A serializable lambda's serialized form is not the anonymous class's,
  *      and `$deserializeLambda$` is not a translation of a class descriptor. Guard
  *      [[Guard.Serializable]];
  *   7. '''OVERLOAD RESOLUTION at every use of the value''' — made impossible by the SHAPE, which
  *      is the cheapest of the three arms and the reason there is no seventh guard. A java
  *      anonymous class is a value whose type javac WROTE DOWN; a bare scala function literal is a
  *      poly expression whose type the CALLEE decides. So the transformer emits the lambda
  *      ASCRIBED to the interface the anonymous class named, and the argument's type at every
  *      callee is exactly the type it had before the phase ran — the candidate set and the
  *      most-specific answer cannot move, because nothing about the call moved. (`JS-C22`/`JS-C23`
  *      is the standing risk that javac and scalac resolve one call differently; the ascription is
  *      what keeps this transformer out of it entirely.)
  *
  * ==And ONE member of the enumeration has no structural test, so it is COUNTED on the decision==
  * A capturing lambda allocates per evaluation, which is what guard 5 buys — and its CLASS is
  * still not the anonymous class's. Java's is `Outer$1`, with a stable name that
  * `getClass().getName()`, `getSimpleName()`, a `toString()` or a log line can print; a lambda's is
  * a hidden class spelled `Outer$$Lambda$14/0x…` — synthetic, unstable across runs, and different
  * in shape as well as in text. No guard can reach it: every reference to a value can reach
  * `getClass()`, so a guard for it would refuse every conversion. It is a §4.4-shaped residue —
  * valid scala, green compile, no moved count — and the only honest answer is to record it where
  * §4.45's reader is, on the CONVERSION's own decision.
  */
object SamLambda:

  /** WHY a site was declined. A closed enum, because [[IdiomVerdict.Refused]]'s `guard` is the
    * string a reader classifies a row by and an open one would let two sites describe one refusal
    * two ways. Each case's [[why]] says whether the refusal is permanent. */
  enum Guard:
    case NotSam, Unreadable, BodyNotSingle, SelfReference, NonCapturing, Serializable,
         MethodMismatch, GenericMethod, ConstructorArgs

    def why: String = this match
      case NotSam =>
        "PERMANENT and correct: java's own SAM rule (JLS 9.8) does not admit this type, so there " +
          "is no lambda to write"
      case Unreadable =>
        "NOT a statement about the type: the class file is not on this port's classpath, so the " +
          "question could not be asked. Widening the frontend classpath would move this row; " +
          "assuming an answer here would be a fabricated fact (§4.6)"
      case BodyNotSingle =>
        "PERMANENT: a scala lambda is one expression and has no place for a field, an instance " +
          "initialiser, a helper method or a nested type. The anonymous class is the faithful form"
      case SelfReference =>
        "PERMANENT: `this`/`super` inside a java anonymous class names the ANON INSTANCE and " +
          "inside a scala lambda names the ENCLOSING class — the conversion would compile and mean " +
          "something else (§4.4)"
      case NonCapturing =>
        "PERMANENT, and licensed by the SPECIFICATION'S SILENCE rather than by any measurement: " +
          "JVMS 5.4.3.6 and the `LambdaMetafactory` contract leave a lambda's instance identity " +
          "UNSPECIFIED in both directions, so a non-capturing lambda MAY be the same object at two " +
          "evaluations while `new I(){…}` never is. A library that removes a listener by identity, " +
          "keys an `IdentityHashMap` or synchronises on the instance would then differ, with a " +
          "green compile and no moved count"
      case Serializable =>
        "PERMANENT: the target extends `java.io.Serializable`, and a serializable lambda's " +
          "serialized form is not the anonymous class's — `$deserializeLambda$` is not a " +
          "translation of a class descriptor"
      case MethodMismatch =>
        "the single member does not implement the interface's single abstract method by name and " +
          "arity, so what a lambda would supply is not what the anonymous class supplied"
      case GenericMethod =>
        "PERMANENT with the IR as it stands: the implemented method declares its own TYPE " +
          "PARAMETERS and a scala function literal has nowhere to put them, so the conversion " +
          "would drop a binder java wrote"
      case ConstructorArgs =>
        "the `new` carries CONSTRUCTOR ARGUMENTS, which java's SAM rule cannot produce — an " +
          "INTERFACE has no constructor to pass them to. A site reaching this guard is an engine " +
          "defect upstream of it, not a residue, and it is counted rather than silently dropped"

  /** the ANSWER for one site. `Convert` carries everything the rewrite needs, so the transformer
    * never re-reads the tree to find it. */
  enum Verdict:
    case Convert(method: Tree.DefDef, iface: String, javaClassName: String)
    case Refuse(guard: Guard, iface: String)

  /** THE DECISION, for one `new T(){…}` in one enclosing declaration.
    *
    * Order matters only for which guard a multiply-refused site is ATTRIBUTED to, and it is stated
    * cheapest-fact-first so the lane groups by the most informative reason: what the class file
    * says, then what the body is, then what the body does. */
  def decide(nw: Tree.New, anon: Tree.AnonClass)(using p: Program): Verdict =
    val iface = typeName(nw.tpt.tpe)
    def refuse(g: Guard) = Verdict.Refuse(g, iface)
    anon.sam match
      case Sam.Answer.Unreadable    => refuse(Guard.Unreadable)
      case Sam.Answer.No(_)         => refuse(Guard.NotSam)
      case Sam.Answer.Yes(m, arity, ser) =>
        if ser then refuse(Guard.Serializable)
        else if anon.dropped.nonEmpty then refuse(Guard.BodyNotSingle)
        else
          anon.body match
            case (d: Tree.DefDef) :: Nil =>
              val name = p.symbolOf(d.symbol).map(_.name).getOrElse("")
              if d.rhs.isEmpty then refuse(Guard.BodyNotSingle)
              else if d.tparams.nonEmpty then refuse(Guard.GenericMethod)
              else if name != m || d.paramss.flatten.sizeIs != arity then refuse(Guard.MethodMismatch)
              else if selfReferences(d, anon.symbol) then refuse(Guard.SelfReference)
              else if !captures(d, anon.symbol) then refuse(Guard.NonCapturing)
              else Verdict.Convert(d, iface, javaClassNameOf(anon))
            case _ => refuse(Guard.BodyNotSingle)

  /** does the body name the ANON INSTANCE — the §4.4 meaning change.
    *
    * On the `cls` SYMBOL, never on the node kind: `Outer.this.field` is a QUALIFIED outer `this`,
    * is perfectly fine under a lambda, and is what most of the real population looks like. A
    * `super` bound to the anon is refused for the same reason and one more of its own — a lambda
    * has no supertype to dispatch to.
    *
    * ==…AND THE SECOND HALF IS A NODE THAT IS NOT THERE, which is what makes this hard==
    * A `Tree.This(anonSym)` exists only where the source used `this` AS A VALUE. Where it is the
    * TARGET of a member access the frontend deliberately does not build one — Spoon reports the
    * anonymous class as the receiver's type whatever the member's real owner is, so trusting it
    * would qualify half the corpus's references with a type that does not exist in the emitted
    * code. The fallback is a BARE reference that scala resolves LEXICALLY, exactly as java did
    * (`SpoonTir.thisOf`). Inside a scala anonymous class that lands on the same member java chose;
    * inside a LAMBDA it lands on the ENCLOSING class's, because a lambda has no members.
    *
    * So `this.toString()` in an anonymous `Runnable` arrives here as
    * `Tree.Ident(java.lang.Object#toString)` — no `This`, no `Select`, nothing a guard reading
    * `This` can see — and converting it would silently print the ENCLOSING object. Measured on
    * this guard's own fixture, which is the only place it exists: zero corpus sites today.
    *
    * Guard 3 has already established that the anon body is exactly ONE method, so the members a
    * bare reference can reach that a lambda would re-resolve are enumerable and small: the public
    * members of `java.lang.Object` (which is what every anonymous class inherits and what java
    * bound to the ANON), and the anon's own SAM method reached recursively. Both are a bare
    * `Tree.Ident` whose symbol's OWNER says which it is — a structural test, and the same shape
    * §4.56 asks for. A reference to an enclosing class's own member has that class as its owner and
    * is untouched, which is the case that must keep working. */
  private def selfReferences(d: Tree.DefDef, anonSym: SymId)(using p: Program): Boolean =
    d.rhs.exists(b => StandardTraversal.scanTerm(b, false) { (acc, t) =>
      acc || (t match
        case Tree.This(c, _, _)  => c == anonSym
        case Tree.Super(c, _, _) => c == anonSym
        // the bare, lexically-resolved half — see the doc above
        case Tree.Ident(s, _, _) => p.symbolOf(s).exists { sym =>
          sym.owner == anonSym ||
            p.symbolOf(sym.owner).exists(_.fullName == SamLambda.JavaLangObject)
        }
        case _ => false)
    })

  /** the root every java class inherits from, and therefore the owner of every member a bare
    * reference inside an anonymous body could reach that the anonymous body did not declare. A fact
    * about java, spelled once. */
  private val JavaLangObject = "java.lang.Object"

  /** what one conversion was, carried from the rewrite to the attribution one hook later. */
  private[transform] final case class Converted(iface: String, javaClassName: String,
                                                method: String, origin: Origin)

  /** does the body CAPTURE an enclosing binding? See the object doc, delta 5, for why this decides
    * anything at all.
    *
    * Two shapes count and both are structural: a `this` naming an ENCLOSING class (the lambda then
    * closes over the outer instance), and a reference to a local or parameter owned by an
    * executable that is not the anon's own method. A local declared INSIDE the body is owned by
    * that method and is therefore not a capture, which is exactly right — it does not make the
    * lambda allocate.
    *
    * The design's other disjunct — "or the site is provably evaluated once" — is NOT implemented
    * and is not silently approximated: a purity/one-shot answer over an arbitrary expression is a
    * dataflow question this engine does not have (the same one `JS-E17` is Open over). Those sites
    * are counted under [[Guard.NonCapturing]] rather than converted on a guess. */
  private def captures(d: Tree.DefDef, anonSym: SymId)(using p: Program): Boolean =
    d.rhs.exists(b => StandardTraversal.scanTerm(b, false) { (acc, t) =>
      acc || (t match
        case Tree.This(c, _, _) => c != anonSym
        case Tree.Ident(s, _, _) => isEnclosingBinding(s, d.symbol, anonSym)
        case _                   => false)
    })

  /** is `s` a local or parameter of an executable OUTSIDE this anonymous class? */
  private def isEnclosingBinding(s: SymId, anonMethod: SymId, anonSym: SymId)(using p: Program): Boolean =
    p.symbolOf(s).exists { sym =>
      val owner = sym.owner
      owner != SymId.None && owner != anonMethod && owner != anonSym &&
        p.definitionOf(owner).exists(_.isInstanceOf[Tree.DefDef])
    }

  /** the name java gave the anonymous class — `Outer$1`. Recorded on the conversion's decision
    * because it is the one thing the lambda cannot carry: see the object doc's `getClass()` note. */
  def javaClassNameOf(anon: Tree.AnonClass)(using p: Program): String =
    p.symbolOf(anon.symbol).map(_.fullName).getOrElse("<anon>")

  private def typeName(t: TypeRepr)(using p: Program): String = t match
    case TypeRepr.TypeRef(_, s)       => p.symbolOf(s).map(_.fullName).getOrElse(s.toString)
    case TypeRepr.AppliedType(tc, _)  => typeName(tc)
    case other                        => other.toString

  // -------------------------------------------------------------------------------------------
  // the walk both consumers share
  // -------------------------------------------------------------------------------------------

  /** every anonymous-class site in a unit, with the DECLARATION it sits in.
    *
    * The subject is the enclosing DECLARATION and never the site, because `CLAUDE.md` §5.1 says a
    * decider records one row per declaration whose emitted form the decision changes — and because
    * a decision subjected at a `Tree.New` has no declaration symbol for the wave's blast
    * classification to join on, so every converted site would land in `members.tsv Δ \ blast` and
    * the gate would fail on its own successes.
    *
    * Reached through `StandardTraversal` — `allClassDefs` for the types (a method-LOCAL class is a
    * block statement, not a type member) and `scanTerm` for the terms, so a node kind added
    * tomorrow is visited by construction (§3). */
  def sites(unit: Tree.ClassDef)(using p: Program): List[(SymId, String, Tree.New, Tree.AnonClass)] =
    val out = List.newBuilder[(SymId, String, Tree.New, Tree.AnonClass)]
    StandardTraversal.allClassDefs(unit).foreach { cd =>
      val clsFqn = p.symbolOf(cd.symbol).map(_.fullName).getOrElse("<anon>")
      cd.body.foreach { st =>
        val (subj, fqn, terms) = st match
          case d: Tree.DefDef => (d.symbol, p.symbolOf(d.symbol).map(_.fullName).getOrElse(clsFqn), d.rhs.toList)
          case v: Tree.ValDef => (v.symbol, p.symbolOf(v.symbol).map(_.fullName).getOrElse(clsFqn), v.rhs.toList)
          case t: Term        => (cd.symbol, clsFqn, List(t))
          case _              => (cd.symbol, clsFqn, Nil)
        terms.foreach { t =>
          StandardTraversal.scanTerm(t, ()) { (_, x) =>
            x match
              case n @ Tree.New(_, _, _, Some(a)) => out += ((subj, fqn, n, a))
              case _                              => ()
          }
        }
      }
    }
    out.result()

/** WAVE 0's INERT CENSUS — the SAM population, published at the POSITION its transformer will take
  * and nowhere else.
  *
  * ==Why a phase and not a check==
  * `CLAUDE.md` §5's dry-run rule, read in the other direction, and it is the same failure either
  * way: '''a phase measures what it is HANDED'''. A dry run hands a phase untransformed input and
  * under-counts (measured: a warning lane priced at 1 by a dry run reads 25 in the live pipeline);
  * a POST-PIPELINE census hands it a tree every surface phase has already moved, and over- or
  * under-counts depending on which phase moved what. There is a live instance of the second
  * direction in this very transformer's ordering constraint — a `CollectionsTransform` retarget
  * moving `java.util.Comparator` to `scala.math.Ordering` changes what the conversion would ascribe
  * to — so a census asked at the end of the pipeline would publish a denominator that is not the
  * population the phase will meet, and the wave's blast would then be DISCOVERED rather than
  * confirmed. Which is the one thing this census exists to prevent.
  *
  * ==Inert, and the gate proves it==
  * `run` returns its argument. The wave-0 gate is 0 member digests on every port; a post-pipeline
  * check could not have been wrong about that and therefore could not have proved it either.
  */
final class SamLambdaCensus extends Phase, IdiomPhase:

  def name: String = "idiom-sam-census"

  def idiomKinds: Set[IdiomKind] = Set(IdiomKind.SamLambda)

  /** the transformer's own position, verbatim — so the descriptors it matches are java's own. */
  override def runsBefore: Set[String] = Set("java-collections->scala", "package-rename")

  override def run(program: Program): Program =
    given Program = program
    program.units.foreach { u =>
      SamLambda.sites(u).foreach { (_, fqn, nw, anon) =>
        val v = SamLambda.decide(nw, anon)
        val c = v match
          case SamLambda.Verdict.Convert(_, iface, _) =>
            // NOT `Converted` — nothing converted, and a lane that said otherwise would be a
            // number about a rewrite that did not happen. NOT a silent omission either: this is
            // the DENOMINATOR, filed under a guard whose whole content is "the transformer is not
            // here", so the wave that adds it moves exactly these rows from `refused` to
            // `converted` and the movement is predicted rather than discovered.
            IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Refused("PhaseNotEnabled",
              "NOT a property of the site: it passes every guard. The transformer that would " +
                "convert it is not in this pipeline, so this row is the population a later wave " +
                "converts — read it as the denominator, not as a delta the port carries"),
              fqn, s"anonymous `$iface`", nw.origin)
          case SamLambda.Verdict.Refuse(g, iface) =>
            IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Refused(g.toString, g.why), fqn,
              s"anonymous `$iface`", nw.origin)
        consider(c)
      }
    }
    program

/** WAVE 1 — the conversion itself. An anonymous class implementing a single-abstract-method
  * interface becomes a LAMBDA, ASCRIBED to that interface.
  *
  * ==§1 kind: (a), unparameterised, and in EVERY pipeline==
  * Which types are SAM is a structural fact about a class file; whether a body is a single method is
  * a structural fact about a tree. There is no library policy in it, so there is no constructor
  * parameter, no `RuleScope`, no `SurfacePolicy` and no fingerprint — and no on/off switch, because
  * a knob on an (a) is the shape `CLAUDE.md` §1 forbids. `PortRun` weaves it, exactly as it weaves
  * the package rename; it is not part of any manifest `surface`, so nothing about the shared surface
  * (§1.5) moves.
  *
  * ==The ASCRIPTION is the load-bearing part, and it is what removes a guard rather than adding one==
  * A java anonymous class is a value whose type javac WROTE DOWN. A bare scala function literal is a
  * POLY EXPRESSION whose type the CALLEE decides. Dropping the type is therefore not "the same value,
  * spelled shorter" — it discards the evidence javac left at every use of that value and hands the
  * choice to scala's own overload resolution, which is the one place this engine knows it cannot
  * follow javac (`JS-C22`/`JS-C23`, `ENGINE-LIMITS.md` T17). Emitted `((a, b) => …): p.Comparator[T]`
  * the argument's type at every callee is exactly what it was before the phase ran, so the candidate
  * set and the most-specific answer cannot move — nothing about the call moved. It costs emitted
  * characters and nothing else: no declaration moves, no symbol moves, no call site is rewritten.
  *
  * ==What it does NOT do==
  * It rewrites no call site (nothing outside the expression can name an anonymous class), moves no
  * declaration's `info`, and mints no top-level unit — so it owes no `Rewrite.accountedBy` lane and
  * §1.5's `RunScope` rule does not apply. The anon's synthetic TYPE symbol disappears, which
  * `Pipeline.recordPatch` deliberately does not report (a symbol swap is indistinguishable from a
  * legitimate drop), and that is verified by running it rather than asserted from this comment: if
  * `rewrite-callsites` ever names this phase `Unaccounted`, the premise is wrong.
  *
  * ==Ordering==
  * BEFORE every retyping phase, so the type it ASCRIBES TO is java's own. A
  * `CollectionsTransform(retarget)` moving `java.util.Comparator` to `scala.math.Ordering` changes
  * what the ascription would say, and a phase that ran afterwards would be writing a type java never
  * named at that site.
  */
final class SamLambdaTransform extends Phase, IdiomPhase:

  def name: String = "sam-anon->lambda"

  def idiomKinds: Set[IdiomKind] = Set(IdiomKind.SamLambda)

  override def runsBefore: Set[String] = Set("java-collections->scala", "package-rename")

  override def run(program: Program): Program =
    given Program = program
    program.rebuilt(program.units.map(u => StandardTraversal.mapClassDef(new Converter(this), u)))

  /** the rewriting traversal, and the ATTRIBUTION of what it did.
    *
    * The conversion happens at `transformNew`, which does not know the declaration it is inside; the
    * traversal is BOTTOM-UP, so the enclosing definition's hook fires AFTER every conversion in its
    * body. That is exactly the shape needed: conversions accumulate in a buffer and the first
    * definition hook to fire CLAIMS them, so a nested `def` takes its own and the enclosing member
    * takes the rest. One `Decision` per declaration with a `count`, never one per site — `CLAUDE.md`
    * §5.1's granularity rule, and here it is also what makes the wave's blast classification
    * COMPUTABLE, since a decision subjected at a `Tree.New` has no declaration symbol to join
    * `members.tsv` on.
    */
  private final class Converter(owner: SamLambdaTransform) extends Phase:
    def name: String = "sam-anon->lambda/convert"

    private val pending = collection.mutable.ListBuffer.empty[SamLambda.Converted]

    /** THE CONVERSION HAPPENS AT THE `Apply`, NOT AT THE `Tree.New` — and that is not a detail.
      *
      * The frontend models `new I(){…}` as `Tree.Apply(Tree.New(…, anon), args, <init>)`
      * (`SpoonTir`'s `ctorCall`). A phase that returned a lambda from `transformNew` would leave
      * the enclosing `Apply` standing and emit `((…) => …): I)()` — a lambda APPLIED to the
      * constructor's argument list, which is not what the java said and is not what the guards
      * decided about.
      *
      * The `Apply` is also where the ARGUMENTS are, which is the belt to guard 1's brace: java's
      * SAM rule admits only an INTERFACE, and an interface has no constructor to pass arguments
      * to, so a non-empty list here is a shape the guards should already have declined. Counted
      * rather than dropped — an argument list a conversion silently discarded is exactly the
      * defect class §3 keeps finding. */
    override def transformApply(t: Tree.Apply)(using p: Program): Term = t.fun match
      case nw @ Tree.New(_, _, _, Some(anon)) =>
        SamLambda.decide(nw, anon) match
          case SamLambda.Verdict.Convert(_, iface, _) if t.args.nonEmpty =>
            file(SamLambda.Guard.ConstructorArgs, iface, nw); t
          case SamLambda.Verdict.Convert(d, iface, javaName) =>
            pending += SamLambda.Converted(iface, javaName,
              p.symbolOf(d.symbol).map(_.name).getOrElse("?"), nw.origin)
            // the ASCRIPTION, and it is the whole of `TirEmitter`'s `(expr: T)` form:
            // `polyOperand` answers true for a lambda, so a `Typed` over one renders as an
            // ascription rather than as the `asInstanceOf` a java CAST renders as.
            Tree.Typed(Tree.Lambda(d.paramss.flatten, d.rhs.get, nw.tpe, nw.origin),
                       nw.tpt, nw.tpe, nw.origin)
          case SamLambda.Verdict.Refuse(g, iface) =>
            file(g, iface, nw); t
      case _ => t

    private def file(g: SamLambda.Guard, iface: String, nw: Tree.New): Unit =
      owner.consider(IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Refused(g.toString, g.why),
        declOf(nw), s"anonymous `$iface`", nw.origin))

    override def transformDefDef(t: Tree.DefDef)(using p: Program): Tree.DefDef =
      claim(t.symbol, t.origin); t

    override def transformValDef(t: Tree.ValDef)(using p: Program): Tree.ValDef =
      claim(t.symbol, t.origin); t

    override def transformClassDef(t: Tree.ClassDef)(using p: Program): Tree.ClassDef =
      claim(t.symbol, t.origin); t

    /** is this definition a MEMBER of a type, as opposed to a local or a parameter?
      *
      * The distinction is the whole of `CLAUDE.md` §4.55's ownership rule read for a different
      * purpose: the frontend interns a field under the CLASS and a local under the enclosing
      * EXECUTABLE, so "is this a member" is a symbol lookup. It has to be asked, because a
      * conversion inside `Runnable a = new Runnable(){…};` would otherwise be claimed by the LOCAL
      * `a` — and a decision subjected at a local has no row in `members.tsv` for the wave's blast
      * classification to join on, which is exactly the failure D3's rule exists to prevent. Skipped
      * here, the conversion bubbles up to the enclosing member, which is where it belongs. */
    private def isMember(sym: SymId)(using p: Program): Boolean =
      p.symbolOf(sym).map(_.owner).flatMap(p.definitionOf).exists(_.isInstanceOf[Tree.ClassDef])

    /** everything converted since the last MEMBER-level definition hook belongs to THIS declaration. */
    private def claim(sym: SymId, at: Origin)(using p: Program): Unit =
      val fqn = p.symbolOf(sym).map(_.fullName).getOrElse("<unknown>")
      if pending.nonEmpty && isMember(sym) then
        val cs = pending.toList
        pending.clear()
        cs.foreach(c => owner.consider(IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Converted,
          fqn, s"anonymous `${c.iface}` -> lambda", c.origin)))
        owner.record(Decision(Decision.Kind.SamLambda, sym, fqn, Map(
          "count"     -> cs.size.toString,
          "interface" -> cs.map(_.iface).distinct.sorted.mkString(" "),
          "method"    -> cs.map(_.method).distinct.sorted.mkString(" "),
          // the residue no guard can reach — see `Decision.Kind.SamLambda`.
          "was"       -> cs.map(_.javaClassName).distinct.sorted.mkString(" "),
          "why"       -> ("java's anonymous class had a STABLE class name; a lambda's is a hidden " +
                          "class, so getClass/getSimpleName/toString read differently here"),
        ), Reason.Universal("anon-SAM->lambda(DESIGN §8.15)"), at))

    /** a refusal filed from inside the traversal has no declaration in hand either; it is attributed
      * the same way every other row is, at the next definition hook — but a REFUSAL changes no
      * emitted text, so there is nothing for the blast gate to join and the site's own origin is the
      * more useful subject. Named rather than left implicit, because the two rows deliberately
      * differ. */
    private def declOf(t: Tree.New): String =
      s"${t.origin.javaPath}:${t.origin.line}"
