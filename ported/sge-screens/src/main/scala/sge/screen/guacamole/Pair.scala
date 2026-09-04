/*
 * Derived from guacamole v0.3.6 — https://github.com/crykn/guacamole
 * Original file: de/damios/guacamole/tuple/Pair.java
 * Copyright 2020 damios; licensed under the Apache License, Version 2.0
 *
 * The hand-written half of the libgdx-screenmanager port — see `package.scala`.
 */
package sge.screen.guacamole

/** An immutable pair, with upstream's FIELD NAMES `x` and `y`.
  *
  * The names are the contract, not the shape: the emitted `ScreenManager` reads
  * `nextTransition.x` / `nextTransition.y` directly, because that is what Java resolved. A Scala
  * `Tuple2` — the obvious substitute — has `_1`/`_2` and would compile at the declaration and fail
  * at every use.
  *
  * `equals`/`hashCode`/`toString` are reproduced because upstream declares them and a queue of
  * pairs is compared in libgdx-screenmanager's own suite. `equals` uses `Objects.equals`, which is
  * null-tolerant on both sides — a `case class` would derive `==` that is null-tolerant too, but
  * would also add `copy`, `unapply` and a companion this port has no upstream counterpart for.
  */
final class Pair[X, Y](val x: X, val y: Y) {

  override def toString: String = "Pair{" + x + "," + y + "}"

  override def equals(other: Any): Boolean = other match {
    case that: Pair[?, ?] => java.util.Objects.equals(that.x, this.x) && java.util.Objects.equals(that.y, this.y)
    case _                => false
  }

  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + (if (x == null) 0 else x.hashCode())
    result = prime * result + (if (y == null) 0 else y.hashCode())
    result
  }
}
