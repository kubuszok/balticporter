package balticporter.transform

import balticporter.tir.{CheckReport, Origin}

/** Counts java `public` fields a reflective framework cannot see once emitted as private scala
  * members. `ENGINE-LIMITS.md` K21 face 2
  *
  * [[Issue.NameTaken]]: seam from this phase's own scope. [[Issue.Unexposed]]: review list of
  * java-public-field types not yet scoped. [[Issue.NameUnreachable]]: name unreachable via
  * `decapitalize`. Recorded only when the phase ran.
  */
object BeanExposureCheck:
  val Name = "bean-exposure"

  enum Issue:
    /** bean name already taken by a member java declared. */
    case NameTaken
    /** java-public-field type not yet in scope — review list. */
    case Unexposed
    /** field name unreachable via `decapitalize`. */
    case NameUnreachable

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
      case NameUnreachable =>
        "§1(a) ENGINE, and a REFUSAL. A bean reader derives the PROPERTY name it registers by " +
          "running `java.beans.Introspector.decapitalize` over the accessor's suffix, and for a " +
          "`lowerUpper` field name (`eMail`, `eTag`, `xAxis`) that is not the field's name: " +
          "`eMail` capitalises to `getEMail`, whose suffix decapitalises to `EMail` — two leading " +
          "capitals keep their spelling — so the property is registered under a name the framework " +
          "never asks for. Emitting it anyway is this face's OWN failure class arriving through " +
          "the repair for it: the accessor exists, the port compiles, every count is flat, and the " +
          "lookup reads ABSENT. So the field is left unexposed and counted here. There is nothing " +
          "to configure: no capitalisation of a `lowerUpper` java field round-trips through " +
          "`decapitalize`, and the property is only reachable if the LIBRARY renames the field."
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
