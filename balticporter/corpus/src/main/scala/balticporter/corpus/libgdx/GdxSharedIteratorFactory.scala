package balticporter.corpus.libgdx

import balticporter.tir.{ConfigView, Phase, TransformFactory}

/** THE WORKED EXAMPLE of a §1(c) rule reaching the CONFIG front door — the companion piece to
  * [[GdxSharedIteratorRule]], which is the worked example of the rule itself.
  *
  * A `.conf` names transforms by string, so the natural (and wrong) way to let a consumer plug in
  * its own rule would be a class name in the config file that the engine reflectively instantiates.
  * That is behaviour arriving as data, and it is what CLAUDE.md §1.5 warns about one level down: the
  * consumer's compiler never sees it, a typo is a runtime failure at best and a silently-skipped
  * rule at worst, and the engine ends up an interpreter for strings.
  *
  * What happens instead is this file. The rule stays ordinary Scala in the porting repository; the
  * repository ALSO ships a five-line factory and one `META-INF/services` line, and the conf then
  * writes `{ transform = "gdx-shared-iterator" }` like any built-in. Everything is compiled by the
  * consumer's own build; the only thing the config file holds is a NAME, and an unknown one is a
  * loud error that lists every name the classpath actually offers.
  *
  * Note where the factory lives: beside the rule, in `corpus` — the stand-in for "the porting
  * program's own repository" — and not in the engine. The engine's own service file names only the
  * engine's own transforms, and it has no way to learn about this one except by finding it on the
  * classpath the consumer assembled.
  */
final class GdxSharedIteratorFactory extends TransformFactory:

  /** the same string the phase reports under, because this rule has no policy to configure and so
    * has no reason to be spelled twice. A parameterised phase's factory name is deliberately its
    * OWN identifier — `Phase.name` is a report identity and may contain characters config keys
    * should not. */
  def name: String = "gdx-shared-iterator"

  def fromConfig(config: ConfigView): Phase = new GdxSharedIteratorRule
