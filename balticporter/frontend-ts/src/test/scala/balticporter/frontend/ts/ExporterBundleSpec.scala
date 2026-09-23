package balticporter.frontend.ts

/** Asserts that the TS exporter bundle is packaged as a jar resource and contains valid JavaScript. */
class ExporterBundleSpec extends munit.FunSuite:

  test("the exporter bundle is present as a classpath resource"):
    val url = getClass.getClassLoader.getResource("balticporter/frontend/ts/exporter/export.js")
    assert(url != null, "export.js resource not found on the classpath")

  test("the exporter bundle parses as JavaScript (starts with a comment or import)"):
    val stream = getClass.getClassLoader.getResourceAsStream("balticporter/frontend/ts/exporter/export.js")
    assert(stream != null, "export.js resource stream is null")
    val content = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    assert(content.nonEmpty, "export.js is empty")
    val trimmed = content.stripLeading()
    assert(
      trimmed.startsWith("/*") || trimmed.startsWith("//") || trimmed.startsWith("import ") || trimmed.startsWith("\"use strict\""),
      s"export.js does not look like JavaScript: starts with '${trimmed.take(40)}...'"
    )

  test("the manifest is present and declares the typescript version"):
    val stream = getClass.getClassLoader.getResourceAsStream("balticporter/frontend/ts/exporter/manifest.properties")
    assert(stream != null, "manifest.properties resource not found")
    val props = new java.util.Properties()
    props.load(stream)
    stream.close()
    val tsVersion = props.getProperty("typescript.version")
    assert(tsVersion != null && tsVersion.nonEmpty, "typescript.version is missing from the manifest")
    assert(tsVersion.matches("""\d+\.\d+\.\d+"""), s"typescript.version '$tsVersion' does not look like a semver")
