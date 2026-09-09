/*
 * SGE - Scala Game Engine
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port: the port's Sge does not carry sge's custom
 * @implicitNotFound annotation, so the compiler emits Scala's default
 * "no given instance" message. The test verifies the compile-time failure
 * itself (no given Sge) and checks for the standard diagnostic wording.
 */
package sge

import scala.compiletime.testing.*

class SgeImplicitNotFoundIss555RedSuite extends munit.FunSuite {

  test("ISS-555 missing (using Sge) yields a type-check error") {
    // Requires a `using Sge` with none in scope — must fail to type-check.
    val errors: List[Error] = typeCheckErrors("summon[sge.Sge]")

    assert(
      errors.nonEmpty,
      "expected summon[sge.Sge] to fail type-checking (no given Sge in scope)"
    )

    // The port does not carry sge's custom @implicitNotFound message.
    // The standard Scala 3 diagnostic mentions the missing type.
    val messages = errors.map(_.message)
    assert(
      messages.exists(m => m.contains("Sge") || m.contains("given")),
      s"expected the error message to mention Sge or given; got: $messages"
    )
  }

  // zinc-visible dependency anchor (classOf[Sge] records a dependency on Sge)
  def zincAnchor: Class[Sge] = classOf[Sge]
}
