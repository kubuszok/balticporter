/*
 * Derived from guacamole v0.3.6 — https://github.com/crykn/guacamole
 * Original file: de/damios/guacamole/Preconditions.java
 * Copyright 2020 damios; licensed under the Apache License, Version 2.0
 *
 * The hand-written half of the libgdx-screenmanager port — see `package.scala`.
 */
package sge.screen.guacamole

/** Argument, state and null checks that throw the JDK exception upstream throws.
  *
  * WHICH exception each one raises is the whole observable contract: `checkArgument` is an
  * `IllegalArgumentException`, `checkState` an `IllegalStateException` and `checkNotNull` a
  * `NullPointerException`, and libgdx-screenmanager's own suite asserts on two of the three.
  * Scala's `require`/`assume` are NOT substitutes — both raise `IllegalArgumentException`, so
  * folding `checkState` into `require` would turn `pushScreen` before `initialize` from the
  * documented `IllegalStateException` into an argument error, silently, with a green compile.
  *
  * `checkNotEmpty` is upstream's fifth pair and is deliberately absent: nothing in this port calls
  * it, and a shim member with no caller is untested code that reads as verified.
  */
object Preconditions {

  def checkArgument(expr: Boolean): Unit = {
    if (!expr) throw new IllegalArgumentException()

  }
  def checkArgument(expr: Boolean, msg: String): Unit = {
    if (!expr) throw new IllegalArgumentException(msg)

  }
  def checkState(expr: Boolean): Unit = {
    if (!expr) throw new IllegalStateException()

  }
  def checkState(expr: Boolean, msg: String): Unit = {
    if (!expr) throw new IllegalStateException(msg)

  }
  def checkNotNull(obj: Object): Unit = {
    if (obj == null) throw new NullPointerException()

  }
  def checkNotNull(obj: Object, msg: String): Unit = {
    if (obj == null) throw new NullPointerException(msg)

  }
}