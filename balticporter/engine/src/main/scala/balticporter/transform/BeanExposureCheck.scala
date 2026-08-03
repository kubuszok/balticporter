package balticporter.transform

import balticporter.tir.{CheckReport, Origin}

/** WHAT A REFLECTIVE FRAMEWORK CANNOT SEE — the count `ENGINE-LIMITS.md` K21 face 2 asks for.
  *
  * A java `public` field is part of the class file's public surface; the scala the port emits has
  * no public JVM field for it under any declaration form. Nothing else in the pipeline can report
  * that: the port compiles, every other count is flat, and the framework's answer is an ABSENT
  * property, which a library that defaults `null` then turns into a plausible wrong answer.
  *
  * Two rows, and they are two different questions:
  *
  *   - [[Issue.NameTaken]] is a seam this phase's own POLICY created — a class it was asked to
  *     expose and could not. §1(b): a scope that produces a silent hole is worse than no scope.
  *   - [[Issue.Unexposed]] is the REVIEW LIST — a type with java-public fields that the port did
  *     not ask about. One row per TYPE, because "is this class read reflectively?" is asked once
  *     per class, and the same shape as `CollectionBoundaryCheck.Issue.OpaqueEgress` for the same
  *     reason.
  *
  * Recorded only where the phase RAN, exactly as the collection and nullability boundaries are: a
  * port that never declared a reflective consumer has no policy for this to be a residue of, and
  * the population would be every public field in the library.
  */
object BeanExposureCheck:
  val Name = "bean-exposure"

  enum Issue:
    /** the class is in scope and a bean name is already taken by a member java declared. */
    case NameTaken
    /** the type has java-public fields and was not in scope — the list a port picks entries from. */
    case Unexposed

  object Issue:
    def classification(i: Issue): String = i match
      case NameTaken =>
        "§1(b) PER-LIBRARY: this class is inside `PublicFieldAccessorTransform(scope)` and one of " +
          "the bean names the field would need is already declared by the java — a `public` field " +
          "beside its own hand-written accessor, which is an ordinary shape. Emitting the second " +
          "one is a duplicate-definition error, and emitting an `isX` beside a `getX` makes a bean " +
          "reader report a conflicting property, so the field is left unexposed and counted here. " +
          "It is not a defect to fix in the engine: the java already publishes that property " +
          "through the accessor it declares, so a framework reading beans sees it. What it does " +
          "NOT see is the FIELD, which is K21's stated limit and is not expressible in Scala."
      case Unexposed =>
        "§1(b) PER-LIBRARY, and a REVIEW LIST rather than a defect. This type has fields java " +
          "declared `public` — part of its class file's surface — and the emitted scala publishes " +
          "them as private fields with scala accessors, so `getFields` answers `[]` and a bean " +
          "reader finds no `getX`. Nothing is broken unless something REFLECTS over this type: no " +
          "compile error, no failing check, and a framework that finds no properties reads every " +
          "one of them as absent rather than failing. Where a type is handed to a serialiser, a " +
          "template engine or an injector, name it in `PublicFieldAccessorTransform(scope)`. The " +
          "engine cannot derive the list — which types a library exposes is a fact about the " +
          "library — and applying it everywhere would rewrite the emitted surface of every port."

  final case class Finding(issue: Issue, subject: String, detail: String, origin: Origin):
    def render: String = s"$issue $subject — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, subject,
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  (none)"
    else
      fs.groupBy(_.issue).toList.sortBy(_._1.toString).map { (issue, vs) =>
        s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}\n" +
          vs.take(20).map(v => "    " + v.render).mkString("\n") +
          (if vs.sizeIs > 20 then s"\n    … ${vs.size - 20} more" else "")
      }.mkString("\n")
