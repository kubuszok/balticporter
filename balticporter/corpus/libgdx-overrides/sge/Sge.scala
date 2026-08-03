package sge

import scala.annotation.implicitNotFound

/** INJECTED SCALA (`Substitutions.inject`) — the CONTEXT TYPE the globals policy threads.
  *
  * `com.badlogic.gdx.Gdx` is libGDX's ambient global: eleven `public static` fields that every one
  * of 100 files reads directly. `GlobalsToImplicitsTransform` retires it by threading this value as
  * a Scala 3 `using` parameter (DESIGN.md §8.4), and the member map in
  * `LibgdxPolicy.globalsToContext` says which static maps to which path ON THIS TYPE — `app` is
  * `application`, and the five `gl*` are two-hop reads through `graphics`.
  *
  * ==Why it is INJECTED and not MINTED==
  * `ContextType.Minted` synthesises one mutable member per mapped field, which is the right answer
  * for a port that only wants the statics moved. This one is a published API: it is what every
  * consumer of the port writes `(using Sge)` against, and it is the shape the reference hand port
  * arrived at (`../sge/sge-extension/../sge/Sge.scala`) — an immutable bundle of the six SERVICES
  * with `@implicitNotFound` on it, so a missing context is a sentence rather than "no given
  * instance of type sge.Sge". A mechanism should not try to guess any of that.
  *
  * The five GL handles are deliberately NOT fields here. `Gdx.gl`, `gl20`, `gl30`, `gl31` and
  * `gl32` were duplicates of what `Graphics` already owns (`getGL20()` … `getGL32()`), kept on the
  * global purely so a caller could reach them in one hop; the member map spends the second hop
  * instead, which is why `LibgdxPolicy.beanPropertyPairs` carries the four `Graphics#gl2x` entries —
  * a path segment is an identifier, so `graphics.gl20` can only land on a member of that name.
  *
  * ==The constructor is PUBLIC, and that is a decision==
  * The reference port makes it `private[sge]` and hands out a builder. This port cannot: a
  * dependent module's hand-written suite (gdx-vfx's, libgdx-screenmanager's) has to be able to
  * build one, and `sge.SgeTestFixture` — the ABSENT-service fixture in this module's test source
  * set — is the shape they all use.
  */
@implicitNotFound(
  "No given `sge.Sge` is in scope. `Sge` is this application's context — application, graphics, " +
    "audio, files, input, net — passed explicitly through `(using sge.Sge)`; it replaces libGDX's " +
    "global `Gdx.*` static fields. Add a `(using sge.Sge)` clause to the enclosing class " +
    "constructor or method and propagate the one your `ApplicationListener` was handed."
)
final case class Sge(
  application: sge.Application,
  graphics: sge.Graphics,
  audio: sge.Audio,
  files: sge.Files,
  input: sge.Input,
  net: sge.Net,
)

object Sge:

  /** Sugar for `summon[Sge]`, so a service reads `sge.Sge().graphics`. The emitted code does NOT
    * use it — `reader = "summon"` is what this port configures, because `summon` needs no import
    * and no companion — but a consumer writing against the port does, and the reference hand port
    * declares exactly this. */
  inline def apply()(using s: Sge): Sge = s
