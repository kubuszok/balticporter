package balticporter.tir

import java.nio.file.{Files, Path}

/** THE SPI DELIVERABLE, at each of its four answers. */
class ServiceProvidersSpec extends munit.FunSuite:

  private val tmp = FunFixture[Path](
    setup = _ => Files.createTempDirectory("bp-spi"),
    teardown = dir =>
      if Files.isDirectory(dir) then
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.deleteIfExists(_)),
  )

  private def descriptor(dir: Path, service: String, body: String): Path =
    val services = dir.resolve("META-INF").resolve("services")
    Files.createDirectories(services)
    val f = services.resolve(service)
    Files.writeString(f, body)
    f

  /** the rename this port declares, as `PortRun.emittedName` would answer it. */
  private val rename: String => String = fqn =>
    if fqn == "p.Spi" || fqn.startsWith("p.") then "q.port." + fqn.substring(2) else fqn

  private val noRename: String => String = identity

  // -------------------------------------------------------------------------------------------

  tmp.test("BOTH namespaces move: the FILE NAME is the renamed service and the LINES are renamed providers") { dir =>
    val f = descriptor(dir, "p.Spi", "p.impl.Alpha\np.impl.Beta\n")
    val List(d) = ServiceProviders.plan(List(f), rename): @unchecked
    assertEquals(d.upstreamService, "p.Spi")
    assertEquals(d.emittedService, "q.port.Spi")
    assertEquals(d.target, "META-INF/services/q.port.Spi")
    assertEquals(d.providers.flatMap(_.emitted), List("q.port.impl.Alpha", "q.port.impl.Beta"))
    assertEquals(d.text, "q.port.impl.Alpha\nq.port.impl.Beta\n")
  }

  tmp.test("a COMMENT and a blank line are carried verbatim — this file is upstream's text") { dir =>
    val f = descriptor(dir, "p.Spi", "# upstream says why\n\np.impl.Alpha\n")
    val List(d) = ServiceProviders.plan(List(f), rename): @unchecked
    assertEquals(d.providers.size, 1)
    assertEquals(d.text, "# upstream says why\n\nq.port.impl.Alpha\n")
  }

  tmp.test("…and a TRAILING comment keeps its text while the class name in front of it moves") { dir =>
    val f = descriptor(dir, "p.Spi", "p.impl.Alpha  # the date one\n")
    val List(d) = ServiceProviders.plan(List(f), rename): @unchecked
    assertEquals(d.text, "q.port.impl.Alpha  # the date one\n")
  }

  tmp.test("the descriptor is WRITTEN under the resource root, at the emitted service's name") { dir =>
    val f    = descriptor(dir, "p.Spi", "p.impl.Alpha\n")
    val out  = dir.resolve("out")
    val List(w) = ServiceProviders.write(ServiceProviders.plan(List(f), rename), out): @unchecked
    assertEquals(w, out.resolve("META-INF/services/q.port.Spi"))
    assertEquals(Files.readString(w), "q.port.impl.Alpha\n")
  }

  // -------------------------------------------------------------------------------------------
  // the residues — each one silent everywhere else
  // -------------------------------------------------------------------------------------------

  tmp.test("every shipped provider is a POSITIVE row, so the lane has a denominator") { dir =>
    val f  = descriptor(dir, "p.Spi", "p.impl.Alpha\np.impl.Beta\n")
    val fs = ServiceProviders.findings(ServiceProviders.plan(List(f), rename), _ => false, renaming = true)
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.Shipped.slug), 2)
    assertEquals(fs.size, 2)
  }

  tmp.test("a provider the port DROPS is a finding: the descriptor would advertise a class that is not there") { dir =>
    val f  = descriptor(dir, "p.Spi", "p.impl.Alpha\np.impl.Beta\n")
    // the drop is asked of the UPSTREAM name, because that is the namespace a `dropTypes` key is
    // written in (§4.56) — asking the emitted one is the same key read in the wrong namespace.
    val fs = ServiceProviders.findings(ServiceProviders.plan(List(f), rename), _ == "p.impl.Beta", renaming = true)
    val dropped = fs.filter(_.kind == ServiceProviders.Kind.DroppedProvider.slug)
    assertEquals(dropped.map(_.owner), List("p.impl.Beta"))
    assert(clue(dropped.head.detail).contains("q.port.impl.Beta"))
    assert(clue(dropped.head.detail).contains("ServiceConfigurationError"))
    // …and it is NOT also counted as shipped: a row cannot be in two answers at once.
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.Shipped.slug), 1)
  }

  tmp.test("the SERVICE ITSELF dropped is its own kind — the next action is to drop the descriptor") { dir =>
    val f  = descriptor(dir, "p.Spi", "p.impl.Alpha\n")
    val fs = ServiceProviders.findings(ServiceProviders.plan(List(f), rename), _ == "p.Spi", renaming = true)
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.DroppedService.slug), 1)
  }

  tmp.test("a descriptor with no provider line is reported — it is indistinguishable from an absent resource") { dir =>
    val f  = descriptor(dir, "p.Spi", "# nothing here\n")
    val fs = ServiceProviders.findings(ServiceProviders.plan(List(f), rename), _ => false, renaming = true)
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.Empty.slug), 1)
  }

  tmp.test("a name the rename did not move is reported on a RENAMING port, and never on one that renames nothing") { dir =>
    val f = descriptor(dir, "p.Spi", "other.Alpha\n")
    val renaming = ServiceProviders.findings(ServiceProviders.plan(List(f), rename), _ => false, renaming = true)
    assertEquals(renaming.count(_.kind == ServiceProviders.Kind.Unrenamed.slug), 1)
    val flat = ServiceProviders.findings(ServiceProviders.plan(List(f), noRename), _ => false, renaming = false)
    assertEquals(flat.count(_.kind == ServiceProviders.Kind.Unrenamed.slug), 0)
    assertEquals(flat.count(_.kind == ServiceProviders.Kind.Shipped.slug), 1)
  }

  // -------------------------------------------------------------------------------------------
  // …and the residue that is about the READER rather than about the file (ENGINE-LIMITS.md P9)
  // -------------------------------------------------------------------------------------------

  tmp.test("a descriptor shipped by a port with a NON-JVM target is counted unwired — `shipped` alone would say the opposite") { dir =>
    val f  = descriptor(dir, "p.Spi", "p.impl.Alpha\np.impl.Beta\n")
    val ds = ServiceProviders.plan(List(f), rename)
    val fs = ServiceProviders.findings(ds, _ => false, renaming = true,
      offJvm = Set(balticporter.catalog.Platform.ScalaJs, balticporter.catalog.Platform.ScalaNative))
    // ONE row per descriptor and not per provider: what is missing is the registration TRIGGER for
    // the service, which every line of one file shares.
    val unwired = fs.filter(_.kind == ServiceProviders.Kind.OffJvmUnwired.slug)
    assertEquals(unwired.size, 1)
    assertEquals(unwired.head.owner, "p.Spi")
    assert(clue(unwired.head.detail).contains("Js"))
    assert(clue(unwired.head.detail).contains("Native"))
    assert(clue(unwired.head.detail).contains("P9"))
    // …and the positives are untouched, so the lane still carries its denominator beside the residue.
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.Shipped.slug), 2)
  }

  tmp.test("…and a JVM-ONLY port has no such residue: the empty target set is the no-op") { dir =>
    val f  = descriptor(dir, "p.Spi", "p.impl.Alpha\n")
    val fs = ServiceProviders.findings(ServiceProviders.plan(List(f), rename), _ => false,
      renaming = true, offJvm = Set.empty)
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.OffJvmUnwired.slug), 0)
    assertEquals(fs.size, 1)
  }

  tmp.test("an EMPTY descriptor is not also reported unwired — there is no provider for a trigger to reach") { dir =>
    val f  = descriptor(dir, "p.Spi", "# nothing here\n")
    val fs = ServiceProviders.findings(ServiceProviders.plan(List(f), rename), _ => false,
      renaming = true, offJvm = Set(balticporter.catalog.Platform.ScalaJs))
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.Empty.slug), 1)
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.OffJvmUnwired.slug), 0)
  }

  tmp.test("a UTF-8 BOM is not part of the first provider's NAME — nor of what this port ships") { dir =>
    // `Files.readAllLines` hands the mark over as the first character of the first line. Left there
    // it is part of the name: the rename does not match it (so the line ships unmoved and is
    // reported `Unrenamed`, which reads as a provider legitimately outside the renamed namespace),
    // and `ServiceLoader.parseLine` rejects it — `U+FEFF` is no `isJavaIdentifierPart` — so the
    // resource's first entry throws while the lane counts it `shipped`.
    val bom = 0xFEFF.toChar
    val f   = descriptor(dir, "p.Spi", s"$bom" + "p.impl.Alpha\np.impl.Beta\n")
    val List(d) = ServiceProviders.plan(List(f), rename): @unchecked
    assertEquals(d.providers.flatMap(_.upstream), List("p.impl.Alpha", "p.impl.Beta"))
    assertEquals(d.providers.flatMap(_.emitted), List("q.port.impl.Alpha", "q.port.impl.Beta"))
    // …and the EMITTED text carries no mark either: the raw line is what this port writes.
    assertEquals(d.text, "q.port.impl.Alpha\nq.port.impl.Beta\n")
    val fs = ServiceProviders.findings(List(d), _ => false, renaming = true)
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.Unrenamed.slug), 0)
    assertEquals(fs.count(_.kind == ServiceProviders.Kind.Shipped.slug), 2)
  }

  tmp.test("…and only the FIRST line, and only a real mark — nothing else is touched") { dir =>
    val bom = 0xFEFF.toChar
    val f   = descriptor(dir, "p.Spi", s"p.impl.Alpha\n$bom" + "p.impl.Beta\n")
    val List(d) = ServiceProviders.plan(List(f), rename): @unchecked
    // a mark in the MIDDLE of a file is not an encoding artefact of the decode; it is content, and
    // guessing at it would be the fabricated fact this file already refuses for a two-token line.
    assertEquals(d.providers.flatMap(_.upstream), List("p.impl.Alpha", s"$bom" + "p.impl.Beta"))
  }

  tmp.test("an entry the format does not admit is carried verbatim, never guessed at (§4.6)") { dir =>
    // two tokens on one line is not a binary class name; a "best effort" rewrite of it would be a
    // fabricated fact, so it is neither rewritten nor counted as a provider.
    val f = descriptor(dir, "p.Spi", "p.impl.Alpha p.impl.Beta\n")
    val List(d) = ServiceProviders.plan(List(f), rename): @unchecked
    assertEquals(d.providers, Nil)
    assertEquals(d.text, "p.impl.Alpha p.impl.Beta\n")
  }
