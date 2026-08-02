package sge

/** HAND-WRITTEN (`src/`, never `src_managed/` — CLAUDE.md §5.5): the `sge.Sge` a test gets.
  *
  * It exists for exactly one emitted declaration. `AnimationControllerTest` constructs `new Model()`
  * and `Model` is one of the classes the globals policy threads, so the suite is threaded too — and
  * MUnit constructs a suite reflectively, which cannot supply a `using`. `LibgdxPolicy.test` names
  * this method as that suite's `selfSupplied` context source, and the engine emits
  * `private given sge.Sge = sge.SgeTestFixture.testSge()` at the head of the suite's body
  * (`ENGINE-LIMITS.md` CT7). The reference hand port writes that line by hand in the same file.
  *
  * ==The services are ABSENT, not NOOPS, and that is the whole design of this file==
  * The reference port's fixture hands out noop implementations of all six services. This one hands
  * out nothing, and the difference decides what a wrong port looks like:
  *
  *   - an ABSENT service fails at the exact field the moment a test reaches it — `graphics` is
  *     `null`, so a `summon[Sge].graphics.gl20.…` that should never have been threaded here throws
  *     a `NullPointerException` naming the line;
  *   - a NOOP service ANSWERS. A test that silently began depending on `Gdx.graphics` would keep
  *     passing while asserting nothing about the thing it was asking, which is the failure mode
  *     CLAUDE.md §3 exists to prevent — and the one a fixture is uniquely placed to cause.
  *
  * That choice is affordable because it was MEASURED rather than assumed: the one suite this
  * fixture reaches touches no service at all. Three of its five tests are a keyframe binary search
  * over `Animation#keyFrames`, two drive `AnimationController`'s loop/action state machine, and
  * `Model` carries the clause for PROPAGATION and dereferences it nowhere. Nothing in that closure
  * reaches graphics, audio, input, files or net.
  *
  * If a future suite legitimately needs a service, give THAT suite a fixture of its own with the
  * one service it needs — do not soften this one. A fixture that answers everything is a fixture
  * that can never fail, and then nothing measures whether the threading was right.
  */
object SgeTestFixture:

  /** An `Sge` whose every service is absent. Each parameter is overridable so a suite that really
    * needs one can pass exactly that one and leave the other five absent — which keeps the NPE as
    * the report for anything it did not think it needed. */
  def testSge(
    application: sge.Application = null,
    graphics: sge.Graphics = null,
    audio: sge.Audio = null,
    files: sge.Files = null,
    input: sge.Input = null,
    net: sge.Net = null,
  ): Sge = Sge(application, graphics, audio, files, input, net)
