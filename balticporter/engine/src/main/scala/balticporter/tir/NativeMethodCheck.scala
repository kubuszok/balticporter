package balticporter.tir

/** Counts the JNI `native` methods emitted as a bodiless `@scala.native` declaration: each one links only on the JVM, through a library loaded at run time, so a port carrying any is JVM-only at that
  * member however it compiles. One row per declaration, so a JVM-only port is visible in `findings.tsv` rather than discovered at a Scala.js or Scala Native link.
  */
object NativeMethodCheck:

  val Name = "jni-native"

  /** THE predicate the emitter writes `@scala.native` on, so the lane counts exactly what is emitted. */
  def bodiless(s: Symbol, d: Tree.DefDef): Boolean =
    s.flags.isNative && d.rhs.isEmpty && s.name != "<init>"

  val Classification: String =
    "configuration (a reusable mechanism, per-port policy): a java `native` method has no body to translate, so it is emitted " +
      "as a bodiless `@scala.native` declaration that binds to a JNI library on the JVM and to nothing on Scala.js or " +
      "Scala Native. Answer it per port: a `PanamaFfiTransform` downcall (JVM), a `platformDirs` replacement per row, " +
      "or `targets` narrowed to the JVM with its reason"

  final case class Finding(owner: String, member: String, origin: Origin):
    def render: String = s"$owner#$member  (${origin.javaPath}:${origin.line})"

    def report: CheckReport.Finding =
      CheckReport.Finding(
        Name,
        "Bodiless",
        s"$owner#$member",
        CheckReport.relativise(origin.javaPath),
        origin.line,
        "a JNI native emitted as a bodiless `@scala.native` — links on the JVM only"
      )

  /** Every bodiless native declared in the given units, nested and anonymous classes included. */
  def check(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    given Program = program
    def scan(owner: String, body: List[Statement]): List[Finding] =
      body.collect {
        case d: Tree.DefDef if program.symbolOf(d.symbol).exists(bodiless(_, d)) =>
          Finding(owner, program.symbolOf(d.symbol).map(_.name).getOrElse("?"), d.origin)
      }
    units
      .flatMap { u =>
        val named = StandardTraversal.allClassDefs(u).flatMap(cd => scan(program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"), cd.body))
        val anon  = StandardTraversal.allAnonClasses(u).flatMap((a, _) => scan(program.symbolOf(u.symbol).map(_.fullName).getOrElse("?"), a.body))
        named ++ anon
      }
      .sortBy(f => (f.origin.javaPath, f.origin.line, f.member))

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else (s"  ${fs.size} x Bodiless\n  $Classification" :: fs.take(10).map("    " + _.render)).mkString("\n")
