/*
 * Derived from guacamole v0.3.6 — https://github.com/crykn/guacamole
 * Original file: de/damios/guacamole/annotations/Beta.java
 * Copyright 2020 damios; licensed under the Apache License, Version 2.0
 *
 * The hand-written half of the libgdx-screenmanager port — see `package.scala`.
 */
package sge.screen.guacamole

/** Marks an API that may change or be removed without notice.
  *
  * Reproduced rather than dropped, and that is not tidiness. An annotation IS the declaration's
  * contract (`Annot` in the TIR says so at length): three members of this library carry it —
  * `ScreenManager.pushScreen(Supplier, Supplier)` and both of `ScreenFboUtils`' FBO-status
  * helpers — and a consumer reading the emitted Scala would otherwise be told nothing about their
  * stability. It also costs nothing: `StaticAnnotation` is erased at run time.
  */
final class Beta extends scala.annotation.StaticAnnotation
