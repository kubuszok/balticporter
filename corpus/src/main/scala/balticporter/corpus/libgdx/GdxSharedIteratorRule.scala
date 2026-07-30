package balticporter.corpus.libgdx

import balticporter.tir.*

/** THE WORKED EXAMPLE of a CLAUDE.md §1(c) rule — a phase that lives OUTSIDE the engine.
  *
  * ==Read this first if you are porting a library==
  * Every other phase in this repository lives in `core/transform`, including the one CLAUDE.md
  * names as the canonical (c) (`IntToOpaqueTransform`). So there was no precedent for the thing a
  * consumer actually has to do: write a rule that only their library could ever want, put it in
  * their own repository, and get it into the pipeline. This file is that precedent, and it is
  * deliberately in `corpus` — the stand-in for "the porting program's own repository" — and
  * not in `core`.
  *
  * Three things it demonstrates, and they are the three a new port needs:
  *
  *  1. **Where the file goes.** Beside the migration program, in the porting repository. It names
  *     `com.badlogic.gdx` freely; the §1 enforcement grep covers `api`, `engine`,
  *     `frontend-spoon` and `runtime`, and it is precisely the point that a (c) rule is not there.
  *  2. **How it enters the pipeline.** As an ordinary element of `PortRun(phases = …)`. It
  *     implements `balticporter.tir.Phase` and nothing else; the engine has no registry, no
  *     service loader and no plugin descriptor to satisfy.
  *  3. **How it is tested.** With `balticporter.testkit.PortSuite`, on a Java snippet, in the
  *     porting repository's own test source set. See `GdxSharedIteratorRuleSpec`.
  *
  * ==Why this rule is genuinely (c) and not a (b) with the policy inlined==
  * §1 says to reach for (c) only after establishing that the mechanism cannot be shared. What is
  * encoded here is not a list of names — it is a libGDX INVARIANT:
  *
  * > `com.badlogic.gdx.utils.Array.iterator()` does not allocate. It returns one of two iterators
  * > cached on the collection, reset in place, so that iteration costs nothing on Android's GC.
  * > libGDX documents the consequence: nested iteration over the SAME collection reuses the same
  * > iterator, the inner loop resets it, and the outer loop then terminates early — silently. The
  * > fix libGDX itself prescribes is to construct a fresh `new Array.ArrayIterator<>(a)` for the
  * > inner loop.
  *
  * A "collections whose iterator is cached" parameter would make this look like a (b), but the
  * mechanism — knowing that the hazard is NESTING, that the fix is a distinct iterator type, and
  * that the outer loop is the one that misbehaves — is a statement about libGDX's allocation
  * strategy. No other library shares it, and parameterising it would produce a phase that no second
  * library could instantiate meaningfully. That is exactly the test §1 sets.
  *
  * ==Why it REPORTS rather than rewrites==
  * The faithful fix is to re-point the inner loop at a freshly constructed iterator, which changes
  * emitted code. This rule ships as an analysis first, for one honest reason: it was written during
  * a refactor whose acceptance criterion was that no measured number moves (CLAUDE.md §5), and a
  * rewriting phase would have moved several at once with nothing to attribute them to. The
  * detection is the hard half and it is complete; the rewrite is a follow-up, on its own
  * measurement. An analysis is a first-class `Phase` — `run` is the full-control entry point the
  * trait exists to offer — and this one reports through `CheckReport` like every other check, so a
  * finding lands in `findings.tsv` beside the engine's own.
  *
  * ==What it finds in the corpus==
  * The count is printed on every run and persisted as the `gdx-shared-iterator` check. A rule that
  * finds nothing is still doing its job — see CLAUDE.md §3 on checks that report zero — but the
  * number is what says whether the hazard is present, and it must not be assumed either way.
  */
final class GdxSharedIteratorRule extends Phase:

  def name: String = "gdx-shared-iterator"

  /** libGDX collections whose `iterator()` returns a CACHED instance. Not a configuration knob —
    * a fact about this library's `utils` package, listed because it is finite and known. */
  private val cachedIteratorCollections: Set[String] = Set(
    "com.badlogic.gdx.utils.Array",
    "com.badlogic.gdx.utils.SnapshotArray",
    "com.badlogic.gdx.utils.DelayedRemovalArray",
    "com.badlogic.gdx.utils.ArrayMap",
    "com.badlogic.gdx.utils.ObjectMap",
    "com.badlogic.gdx.utils.ObjectSet",
    "com.badlogic.gdx.utils.OrderedMap",
    "com.badlogic.gdx.utils.OrderedSet",
    "com.badlogic.gdx.utils.IntMap",
    "com.badlogic.gdx.utils.IntSet",
    "com.badlogic.gdx.utils.LongMap",
    "com.badlogic.gdx.utils.IdentityMap",
    "com.badlogic.gdx.utils.Queue",
  )

  final case class Finding(collection: String, receiver: String, outer: Origin, inner: Origin):
    def render: String =
      s"nested iteration over the same $collection — the cached iterator is reset by the inner " +
        s"loop and the outer loop ends early  (outer ${outer.javaPath}:${outer.line}, inner " +
        s"${inner.javaPath}:${inner.line})"

  private val found = collection.mutable.ListBuffer.empty[Finding]

  def findings: List[Finding] = found.toList

  /** Full-control entry point: a whole-program analysis, then the program returned UNCHANGED.
    *
    * The nesting question is answered with `StandardTraversal.scanTerm` over each candidate loop's
    * body rather than with a private recursion. That is not style: two of the four silent
    * correctness defects this project has found were hand-rolled walks that stopped one node short
    * (CLAUDE.md §3), and a walk that misses a node kind here reports zero hazards from a program
    * that has them — the worst answer a check can give. */
  override def run(program: Program): Program =
    given Program = program
    found.clear()
    val outerScan = new Phase:
      def name: String = "gdx-shared-iterator/outer"
      override def transformTerm(t: Term)(using Program): Term =
        t match
          case fe: Tree.ForEach =>
            for
              coll <- collectionOf(program, fe)
              recv <- receiverOf(program, fe)
              inner <- nestedOver(program, fe, recv)
            do found += Finding(coll, recv, fe.origin, inner.origin)
          case _ => ()
        t
    program.units.foreach(u => StandardTraversal.mapClassDef(outerScan, u))

    val fs = findings
    println(s"[gdx-shared-iterator] ${fs.size} nested-iteration hazard(s) over a cached libGDX iterator")
    if fs.nonEmpty then
      println(
        "  [§1(c) LIBRARY-SPECIFIC: rewrite the INNER loop to `new Array.ArrayIterator<>(a)`. " +
          "The engine cannot know this — it is libGDX's allocation strategy, not a Java/Scala fact.]"
      )
      fs.foreach(f => println("  " + f.render))
    // Registered even when empty, so `counts.tsv` can tell "found nothing" from "never ran".
    CheckReport.record(name, fs.map { f =>
      CheckReport.Finding(name, "nested-cached-iterator", f.receiver,
        CheckReport.relativise(f.inner.javaPath), f.inner.line, f.render)
    })
    program

  /** the libGDX collection FQN this loop iterates, if it is one with a cached iterator. */
  private def collectionOf(program: Program, fe: Tree.ForEach): Option[String] =
    headSymbol(fe.iterable.tpe)
      .flatMap(program.symbolOf)
      .map(_.fullName)
      .filter(cachedIteratorCollections.contains)

  /** the SYMBOL being iterated, named so two loops can be compared. Only a plain reference
    * qualifies: `for (X x : a)` and `for (Y y : a)` share `a`'s iterator, while `for (X x :
    * a.copy())` does not — and treating a call result as the same receiver would manufacture
    * hazards that do not exist. */
  private def receiverOf(program: Program, fe: Tree.ForEach): Option[String] =
    (fe.iterable match
      case i: Tree.Ident  => Some(i.sym)
      case s: Tree.Select => Some(s.sym)
      case _              => scala.None
    ).flatMap(program.symbolOf).map(_.fullName)

  /** a for-each INSIDE `fe`'s body over the same receiver. */
  private def nestedOver(program: Program, fe: Tree.ForEach, receiver: String)(using Program): Option[Tree.ForEach] =
    StandardTraversal
      .scanTerm(fe.body, List.empty[Tree.ForEach]) { (acc, t) =>
        t match
          case inner: Tree.ForEach if receiverOf(program, inner).contains(receiver) => inner :: acc
          case _                                                                   => acc
      }
      .lastOption

  private def headSymbol(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSymbol(tc)
    case _                           => scala.None
