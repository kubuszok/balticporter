// ---------------------------------------------------------------------------------------------
// HAND-WRITTEN (`src/`, never `src_managed/` — CLAUDE.md §5.5): the `sge.Sge` this port's
// DIFFERENTIAL suites get. It is a REWRITE and not an adaptation of the reference hand port's
// `HeadlessTextraSge`, which is what `PROGRESS.md` §10.8.13 correction 3 said it would have to be:
// that fixture rests on six things this port does not emit and never will, because the hand port
// invented them (`sge.noop.*`, `lowlevel.Nullable`, `sge.net.SgeHttpClient`,
// `sge.graphics.TextureTarget`, `sge.WorldUnits`, and a top-level `sge.files.FileType` this port
// emits NESTED as `sge.Files.FileType`).
//
// ==THE SERVICES ARE ABSENT, and the ONE that is not is the LIBRARY'S OWN HEADLESS CONTRACT==
// `sge.SgeTestFixture` in `ported/sge` argues the design and it is the design here: a service that
// is `null` fails at the exact field the moment a test reaches it, while a service that ANSWERS
// lets a test keep passing while asserting nothing about the thing it asked (CLAUDE.md §3). So
// five of the six are absent and `application` is not, because TextraTypist ITSELF asks it one
// question and documents the answer:
//
//   KnownFonts.java:104   `if (Gdx.app == null) throw new IllegalStateException("Gdx.app cannot
//                          be null; initialize KnownFonts in create() or later.")`
//   KnownFonts.java:121   `if (Gdx.app.getType() == ApplicationType.HeadlessDesktop) return;`
//
// — the second line is the library's own headless path: under `HeadlessDesktop` it compiles no
// shader and touches no GL. `HeadlessApplication` therefore answers exactly those two questions
// (`getType()` and the lifecycle-listener registration `KnownFonts`' constructor performs) and
// REFUSES every other member with an `UnsupportedOperationException` naming it. A stub that
// answered them all would be a fixture that can never fail.
//
// ==WHAT THIS FIXTURE DOES NOT UNLOCK, MEASURED==
// It does not reach `new Font()`, and no fixture can: `Font()` delegates to `new BitmapFont()`,
// whose own delegation is the ONE site `ENGINE-LIMITS.md` C11 drops, so `BitmapFont.data` is null
// and `Font`'s constructor dies at `data.scaleX`. Java's own `new Font()` is no better headlessly
// — it loads the default 15pt face through `new Texture(...)`, which needs GL — so the reference
// hand port's `new Font()` suites run only because that port DIVERGED and made `Font()` an empty
// font. `PROGRESS.md` §10.8.17 is the measurement and the re-census.
// ---------------------------------------------------------------------------------------------
package sge
package textra

/** The `given Sge` a differential suite declares, and the two service stubs that go with it. */
object HeadlessSge {

  /** An `Sge` whose every service is absent except the ones a suite hands in. Each parameter is
    * overridable so a suite that really needs one passes exactly that one and leaves the others
    * absent — which keeps the `NullPointerException` as the report for anything it did not think
    * it needed. `application` defaults to the headless one because the library asks for it by
    * name (see the header); every other default is `null` on purpose.
    */
  def apply(
    application: sge.Application = HeadlessApplication,
    graphics: sge.Graphics = null,
    audio: sge.Audio = null,
    files: sge.Files = null,
    input: sge.Input = null,
    net: sge.Net = null,
  ): sge.Sge = sge.Sge(application, graphics, audio, files, input, net)

  /** Answers `HeadlessDesktop` and accepts a lifecycle listener; refuses everything else LOUDLY.
    *
    * Those are the two members `KnownFonts` touches, and `KnownFonts` is what every `Font`
    * constructor reaches through `setDistanceField`. Anything else this object is asked for is a
    * question a headless differential suite has no business answering, so it throws with the
    * member's own name rather than returning a plausible value.
    */
  object HeadlessApplication extends sge.Application {

    private def absent(member: String): Nothing =
      throw new UnsupportedOperationException(
        s"sge.textra.HeadlessSge.HeadlessApplication.$member is deliberately absent — this is a " +
          "headless differential fixture, and a stub that answered this would let a test pass " +
          "while asserting nothing about it (CLAUDE.md §3)"
      )

    def getType(): sge.Application.ApplicationType = sge.Application.ApplicationType.HeadlessDesktop
    def addLifecycleListener(listener: sge.LifecycleListener): Unit = ()
    def removeLifecycleListener(listener: sge.LifecycleListener): Unit = ()

    def getApplicationListener(): sge.ApplicationListener = absent("getApplicationListener")
    def getGraphics(): sge.Graphics = absent("getGraphics")
    def getAudio(): sge.Audio = absent("getAudio")
    def getInput(): sge.Input = absent("getInput")
    def getFiles(): sge.Files = absent("getFiles")
    def getNet(): sge.Net = absent("getNet")
    def log(tag: String, message: String): Unit = absent("log")
    def log(tag: String, message: String, exception: Throwable): Unit = absent("log")
    def error(tag: String, message: String): Unit = absent("error")
    def error(tag: String, message: String, exception: Throwable): Unit = absent("error")
    def debug(tag: String, message: String): Unit = absent("debug")
    def debug(tag: String, message: String, exception: Throwable): Unit = absent("debug")
    def setLogLevel(logLevel: Int): Unit = absent("setLogLevel")
    def getLogLevel(): Int = absent("getLogLevel")
    def setApplicationLogger(applicationLogger: sge.ApplicationLogger): Unit = absent("setApplicationLogger")
    def getApplicationLogger(): sge.ApplicationLogger = absent("getApplicationLogger")
    def getVersion(): Int = absent("getVersion")
    def getJavaHeap(): Long = absent("getJavaHeap")
    def getNativeHeap(): Long = absent("getNativeHeap")
    def getPreferences(name: String): sge.Preferences = absent("getPreferences")
    def getClipboard(): sge.utils.Clipboard = absent("getClipboard")
    def postRunnable(runnable: Runnable): Unit = absent("postRunnable")
    def exit(): Unit = absent("exit")
  }

  /** A headless `Sge` whose `files` service resolves every lookup out of an IN-MEMORY table, and
    * reports a MISSING handle for a name it does not hold — which is what `Font.getJsonExtension`'s
    * probe order has to be able to observe.
    *
    * The context and the service are one value because an emitted `FileHandle` takes `(using Sge)`:
    * a handle cannot exist before the `Sge` whose `files` will hand it out, so the table is filled
    * after construction and the service reads the context back through this object.
    */
  final class InMemoryFiles {
    private val table = scala.collection.mutable.LinkedHashMap.empty[String, sge.files.FileHandle]

    private object Service extends sge.Files {
      // `Files` is emitted with java's own arity, and this fixture holds ONE table for every file
      // type: a differential suite names an asset, not a storage location.
      def getFileHandle(path: String, `type`: sge.Files.FileType): sge.files.FileHandle = internal(path)
      def classpath(path: String): sge.files.FileHandle = internal(path)
      def internal(path: String): sge.files.FileHandle =
        table.getOrElse(path, new HeadlessSge.MissingFileHandle(path)(using context))
      def external(path: String): sge.files.FileHandle = internal(path)
      def absolute(path: String): sge.files.FileHandle = internal(path)
      def local(path: String): sge.files.FileHandle = internal(path)
      def getExternalStoragePath(): String = ""
      def isExternalStorageAvailable(): Boolean = false
      def getLocalStoragePath(): String = ""
      def isLocalStorageAvailable(): Boolean = false
    }

    /** The context a suite declares as its `given`. */
    val context: sge.Sge = HeadlessSge(files = Service)

    /** Registers one in-memory file under a name; returns the handle so a suite can keep using it. */
    def put(name: String, handle: sge.files.FileHandle): sge.files.FileHandle = {
      table.put(name, handle)
      handle
    }
  }

  /** A path the probe must see as ABSENT. */
  final class MissingFileHandle(name: String)(using sge.Sge) extends sge.files.FileHandleStream(name) {
    override def exists(): Boolean = false
  }

  /** An in-memory readable "file": only `read()` and the base-class helpers built on it run. */
  final class BytesFileHandle(name: String, bytes: Array[Byte])(using sge.Sge)
      extends sge.files.FileHandleStream(name) {
    override def read(): java.io.InputStream = new java.io.ByteArrayInputStream(bytes)
  }

  /** An in-memory writable "file" capturing everything written through `write(false)`. */
  final class CapturingFileHandle(name: String)(using sge.Sge) extends sge.files.FileHandleStream(name) {
    private val sink = new java.io.ByteArrayOutputStream()
    override def write(append: Boolean): java.io.OutputStream = sink
    def bytes: Array[Byte] = sink.toByteArray
  }
}
