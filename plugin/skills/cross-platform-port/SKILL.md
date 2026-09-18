---
name: cross-platform-port
description: Make ported code compile, LINK and pass on Scala.js and Scala Native as well as the JVM — what each javalib actually ships, how to check it in minutes, where a platform fact is recorded (catalog rows, portability rules) and where it is refused. Use when a JS or Native row fails to link or test, before adding any JVM-only API to emitted or injected code, and when a portability lane row moves.
---

# Three platforms, one port

A green JVM compile says nothing about the other two rows. Scala.js compiles everything and fails
at LINK on an unknown method; Scala Native links whole classes and fails on a method the javalib
never declared. Every platform question is answered the same way: read the javalib, record the
fact, then decide.

## 1. Check the javalib, do not guess

```
J=$(find ~/Library/Caches/Coursier/v1 -name 'javalib_native0.5_3-*.jar' | sort | tail -1)
unzip -l "$J" | grep 'java/net/'                       # Native: which classes exist
JS=$(find ~/Library/Caches/Coursier/v1 -name 'scalajs-javalib-*.jar' | sort | tail -1)
unzip -l "$JS" | grep 'java/lang/ThreadLocal'          # JS: class present?
unzip -p "$JS" java/lang/ThreadLocal.sjsir | strings | grep -i withInitial   # JS: member present?
```
A `Foo$.sjsir`/`Foo$.nir` entry is the companion — no companion, no static factory.

Measured so far (JS 1.22.0, Native 0.5.11/0.5.12):
- `java.net.URL`: absent on BOTH (Native ships `URI`, `URLEncoder`/`URLDecoder`,
  `MalformedURLException`). Catalog `JS-P35`; rule `java.net.URL` (both), `Class#getResource`
  (both), `Class#getResourceAsStream` (JS only — Native declares it and answers null).
- `ThreadLocal`: class on both; `withInitial` on Native only. A Java `static final` scratch instance
  is shared by all threads, so confine it to a `ThreadLocal` subclass overriding `initialValue`
  rather than `withInitial`, which Scala.js lacks: spell
  `new ThreadLocal[T] { override def initialValue(): T = e }`.
- `java.lang.invoke` (MethodHandle): JVM only. Reflection (`Class#getMethod`…): JVM only.

## 2. Where a fact goes

- `balticporter/api/…/catalog/ApiRows.scala`: one row per API, availability + verdict per
  platform. A rule that cites a row (`at = p(n)`) must not claim a platform the row calls `Keep`
  (`PortabilityTargetsSpec` enforces it, and its JS-only list must name every JS-only rule).
- `balticporter/engine/…/tir/PortabilityCheck.scala`: the rule, `on = Rule.JsOnly/NativeOnly`
  or both, `exactMember` for one method. A more specific rule (`java.net.URL`) precedes its
  package prefix (`java.net.`).
- The port's answer is policy: a `CallSiteSubstitutionTransform` respelling
  (`getResource(x) != null` → `getResourceAsStream(x)` closed and null-checked), a drop + inject,
  or a `PortManifest.targets` narrowing WITH its reason. Never a JVM-only dependency in the consumer
  (no jackson/ANTLR/reflection in ssg; "hand-maintained port" is not an answer).

## 3. The reference port already decided most of it

`git -C ../sge show origin/master:<path>` — master's migration notes name the platform decision
(`ThreadLocal … NOT the withInitial static factory (ISS-832 JS-link regression)`). Read it before
inventing a spelling; the port emits master's spelling, and the engine records why: the `ThreadLocal`
confinement above, and a member rename attached to a redirect of an external type (such as
`Comparable.compareTo` to `Ordered.compare`) must bind even though the run owns no declaration of
that type, honouring the redirect's scope.

## 4. Verify on every row before pushing

sge: `testCompile-jvm-3; testCompile-js-3; testCompile-native-3`, then `ci-jvm-3`, `test-js-3`,
`test-native-3` (aliases). Native tests link: a suite that compiles can still fail to link, so the
Native TEST run is the gate for injected code. Fixture differences between rows are real
(`DummyTextureData.type` vs `dataType`) — fix the fixture, never skip the row.

## 5. Behaviour: java wins, per platform

A Native suite expecting a different exception than java (`FileReadError` vs
`GdxRuntimeException`) is the hand port's divergence; the port keeps java's behaviour and the test
is adapted, recorded as a finding. JVM-only fallbacks (classpath resources) stay counted on JS.
