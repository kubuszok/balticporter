package balticporter.corpus.libgdx

import balticporter.tir.{ConfigView, Phase, TransformFactory}

/** A §1(c) rule reaching the CONFIG front door via `META-INF/services`, so a `.conf` can name it
  * (`{ transform = "gdx-shared-iterator" }`) without the engine reflectively instantiating a class
  * name from data (CLAUDE.md §1.5). Lives beside [[GdxSharedIteratorRule]] in `corpus`, not the
  * engine.
  */
final class GdxSharedIteratorFactory extends TransformFactory:

  /** same string the phase reports under; this rule has no policy to configure, so no reason to
    * spell it twice. */
  def name: String = "gdx-shared-iterator"

  def fromConfig(config: ConfigView): Phase = new GdxSharedIteratorRule
