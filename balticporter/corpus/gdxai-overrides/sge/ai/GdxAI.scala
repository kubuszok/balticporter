/*
 * Ported from gdx-ai — https://github.com/libgdx/gdx-ai
 * Original source: com/badlogic/gdx/ai/GdxAI.java
 * Original authors: davebaol
 * Licensed under the Apache License, Version 2.0
 *
 * INJECTED SCALA (`Substitutions.dropTypes` + `inject`) — the SERVICE LOCATOR, minus the one
 * question this port cannot ask.
 *
 * Java's `GdxAI` chooses two of its three services by SNIFFING THE AMBIENT ENVIRONMENT at class
 * initialisation:
 *
 *   private static Logger     logger     = Gdx.app   == null ? new NullLogger()          : new GdxLogger();
 *   private static FileSystem fileSystem = Gdx.files == null ? new StandaloneFileSystem() : new GdxFileSystem();
 *
 * The libGDX base retired `Gdx.*` into a context threaded explicitly through `(using sge.Sge)`, and
 * a static field's initialiser runs before anything could pass it one. So the port cannot ask
 * "is a libGDX application running?" — not because the mechanism is missing, but because the
 * answer is a fact about a global this port has removed.
 *
 * WHAT IS INSTALLED INSTEAD IS JAVA'S OWN NEGATIVE BRANCH, and that is the whole of the change:
 * `NullLogger` and `StandaloneFileSystem` are exactly what java installs when there is no running
 * libGDX environment, and running gdx-ai OUT of a libGDX application is upstream's own headline
 * use case ("the libgdx jar must still be in the classpath but you don't need native libraries
 * since the libgdx environment is not initialized at all"). The libGDX-backed pair is still
 * emitted, still compiles, and is installed the way upstream already documents for every platform
 * its own sniff cannot serve: `GdxAI.setLogger(new GdxLogger())`,
 * `GdxAI.setFileSystem(new GdxFileSystem())` — which is the sentence in java's own class javadoc,
 * "if you want to use gdx-ai in Android … out of a libgdx application then you have to implement
 * and set proper providers".
 *
 * WHY A REPLACED TYPE AND NOT A `sites` POLICY. Both context-seam exits were measured and both are
 * worse (PROGRESS.md §10.7.6): `residual-global` answers only the SPELLING of the read and leaves
 * the two `unsuppliable use` seams, and `lazy-init` — which does move the initialisation to first
 * read — cannot express a MUTABLE static, so java's own `setLogger`/`setFileSystem` became
 * `E052 Reassignment to val` while the threading cascaded to two more singletons in other files.
 * A body substitution cannot reach this either: `MethodBodyTransform` replaces a METHOD's body and
 * these are field initialisers.
 *
 * Everything else here is the emitted translation verbatim — six accessors over three `private
 * var`s, in the `object` the all-static class collapses to.
 */
package sge.ai

/** Environment class holding references to the [[Timepiece]], [[Logger]] and [[FileSystem]]
  * instances. The references are held in static fields which allows static access to all sub
  * systems.
  *
  * Basically, this class is the locator of the service locator design pattern. The locator contains
  * references to the services and encapsulates the logic that locates them. Being a decoupling
  * pattern, the service locator provides a global point of access to a set of services without
  * coupling users to the concrete classes that implement them.
  *
  * @author davebaol
  */
object GdxAI {
  private var timepiece: sge.ai.Timepiece = new sge.ai.DefaultTimepiece()

  /** `NullLogger` rather than java's `Gdx.app == null ? … : new GdxLogger()` — see the file header.
    * Install the libGDX-backed one with `GdxAI.setLogger(new sge.ai.GdxLogger())`. */
  private var logger: sge.ai.Logger = new sge.ai.NullLogger()

  /** THE ONE SERVICE WITH NO DEFAULT THIS PORT CAN BUILD, and the asymmetry is forced rather than
    * chosen: `NullLogger` above needs nothing, and BOTH `FileSystem` implementations need a
    * context. `StandaloneFileSystem` hands out `DesktopFileHandle`s, `FileHandle` is one of the 188
    * base classes the globals policy threads, and the emitted constructor is therefore
    * `class StandaloneFileSystem(using sge.Sge)` — so this `object`'s initialiser, which has no
    * caller and no clause, cannot construct one any more than java's `<clinit>` could ask
    * `Gdx.files == null`.
    *
    * So `getFileSystem` REFUSES rather than answering, and the refusal is LOUDER than java and
    * never quieter (CLAUDE.md §1): java would have returned a `StandaloneFileSystem` that, on any
    * platform its own sniff could not serve, does not work — which is why its class javadoc already
    * says "if you want to use gdx-ai in Android … out of a libgdx application then you have to
    * implement and set proper providers". The message names the one line that fixes it, and both
    * implementations are emitted and installable.
    *
    * `null` and not an `Option`: this is java's own field, at java's own type, read through java's
    * own accessor. */
  private var fileSystem: sge.ai.FileSystem = null

  /** Returns the timepiece service. */
  def getTimepiece(): sge.ai.Timepiece = GdxAI.timepiece

  /** Sets the timepiece service. */
  def setTimepiece(timepiece: sge.ai.Timepiece): scala.Unit = GdxAI.timepiece = timepiece

  /** Returns the logger service. */
  def getLogger(): sge.ai.Logger = GdxAI.logger

  /** Sets the logger service. */
  def setLogger(logger: sge.ai.Logger): scala.Unit = GdxAI.logger = logger

  /** Returns the filesystem service. */
  def getFileSystem(): sge.ai.FileSystem =
    if GdxAI.fileSystem != null then GdxAI.fileSystem
    else throw new sge.utils.GdxRuntimeException(
      "GdxAI has no FileSystem: this port cannot build one at class initialisation, because every " +
      "FileHandle it would hand out takes the application context this port threads through " +
      "`(using sge.Sge)` and a static initialiser has no clause. Call " +
      "`sge.ai.GdxAI.setFileSystem(new sge.ai.StandaloneFileSystem())` — or `new sge.ai.GdxFileSystem()` " +
      "— from a scope that has one. Upstream documents the same act for every platform its own " +
      "`Gdx.files == null` sniff cannot serve.")

  /** Sets the filesystem service. */
  def setFileSystem(fileSystem: sge.ai.FileSystem): scala.Unit = GdxAI.fileSystem = fileSystem
}
