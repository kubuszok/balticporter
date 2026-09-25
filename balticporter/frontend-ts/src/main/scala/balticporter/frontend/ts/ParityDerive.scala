package balticporter.frontend.ts

import scala.collection.mutable

/** The reference file is the skeleton; a translated body replaces a reference body where one exists for that member and nothing refuses it.
  *
  * A library supplies its translated bodies and its policy; the interleaving and the per-member record of where each body came from live here, once.
  */
object ParityDerive:

  /** Where an emitted body came from. */
  object Source:
    val Translated = "translated"
    val Reference  = "reference"

  /** Why a reference body was kept. `unclassified` should be empty by construction: every declined site names its guard. */
  object Why:
    val NoTranslatedBody     = "no-translated-body"
    val OccurrenceOutOfRange = "occurrence-out-of-range"
    val Unclassified         = "unclassified"
    val TranslatedElsewhere  = "translated-elsewhere"
    val Private              = "private"
    def uncompilablePattern(pattern: String): String = s"uncompilable-pattern:$pattern"
    def translatorRefusal(reason:    String): String = s"translator-refusal:$reason"
    def skeletonCannotOffer(kind:    String): String = s"skeleton-cannot-offer:$kind"
    def referenceOnly(reason:        String): String = s"reference-only:$reason"

  /** One translated body and every reason the translator gave for leaving part of it untranslated. */
  final case class TranslatedBody(text: String, refusals: List[String] = Nil)

  /** Translated bodies by reference member name, one entry per OCCURRENCE in source order: the n-th member of that name in the reference file takes the n-th body. */
  final case class Bodies(byName: Map[String, List[TranslatedBody]]):
    def ++(other: Bodies): Bodies =
      Bodies(
        (byName.keySet ++ other.byName.keySet).iterator.map(k => k -> (byName.getOrElse(k, Nil) ++ other.byName.getOrElse(k, Nil))).toMap
      )
    def names:   Set[String] = byName.keySet
    def isEmpty: Boolean     = byName.isEmpty

  object Bodies:
    val empty: Bodies = Bodies(Map.empty)

    /** From the older shape that carries a refusal COUNT only; the reasons are not recoverable and are recorded as such. */
    def fromCounts(bodies: Map[String, List[(String, Int)]]): Bodies =
      Bodies(bodies.map { case (k, v) => k -> v.map((text, n) => TranslatedBody(text, List.fill(n)("reason-not-recorded"))) })

  /** Per-library policy. `aliases` maps a reference member name to the translated names also tried for it, after its own; every default leaves the derive unchanged. `keepReferenceOnRefusal` keeps the
    * reference body wherever the translator left a hole, instead of emitting the hole.
    */
  final case class Policy(
    uncompilablePatterns:   List[String] = Nil,
    allowPrivate:           Boolean = true,
    aliases:                Map[String, List[String]] = Map.empty,
    keepReferenceOnRefusal: Boolean = false
  )

  /** Where one member's emitted body came from. `offered` is false for a member the skeleton reader cannot replace; such a member has no occurrence index (-1). */
  final case class BodyEntry(
    methodName:       String,
    source:           String,
    why:              String,
    rastRefusalCount: Int = 0,
    occurrence:       Int = 0,
    line:             Int = 0,
    offered:          Boolean = true
  )

  /** One reference file derived. The counts cover the OFFERED members only; `unoffered` lists the rest, all of them reference bodies. */
  final case class ParityResult(
    emittedSource:  String,
    bodies:         List[BodyEntry],
    totalMethods:   Int,
    rastCount:      Int,
    referenceCount: Int,
    totalRefusals:  Int,
    unoffered:      List[BodyEntry] = Nil
  )

  /** Interleave `translated` into `referenceSource` under `policy`. */
  def derive(referenceSource: String, translated: Bodies, policy: Policy): ParityResult =
    val lines   = referenceSource.split("\n", -1).toList
    val methods = ReferenceSkeleton.findMethodBoundaries(lines)

    def candidates(name: String): List[TranslatedBody] =
      translated.byName.getOrElse(name, Nil) ++ policy.aliases.getOrElse(name, Nil).flatMap(translated.byName.getOrElse(_, Nil))

    val sb            = new StringBuilder
    val bodyEntries   = mutable.ListBuffer.empty[BodyEntry]
    val consumed      = mutable.Map.empty[String, Int].withDefaultValue(0)
    var totalRefusals = 0

    var lineIdx   = 0
    var methodIdx = 0

    while lineIdx < lines.size do
      if methodIdx < methods.size && lineIdx == methods(methodIdx).signatureLine then
        val method = methods(methodIdx)

        val idx = consumed(method.name)
        consumed(method.name) = idx + 1

        val sigEndLineIdx = if method.equalsLine >= 0 then method.equalsLine else ReferenceSkeleton.findSignatureEnd(lines, method.signatureLine)
        val eqIdx         = if method.equalsLine >= 0 then method.equalsColumn else ReferenceSkeleton.findEqualsInSignature(lines(sigEndLineIdx))
        val named         = candidates(method.name)

        val decision: Either[String, TranslatedBody] =
          if !policy.allowPrivate && method.isPrivate then Left(Why.Private)
          else if named.isEmpty then Left(Why.NoTranslatedBody)
          else
            named.lift(idx) match
              case None       => Left(Why.OccurrenceOutOfRange)
              case Some(body) =>
                policy.uncompilablePatterns.find(body.text.contains) match
                  case Some(pattern)                                                   => Left(Why.uncompilablePattern(pattern))
                  case None if policy.keepReferenceOnRefusal && body.refusals.nonEmpty =>
                    Left(Why.translatorRefusal(body.refusals.distinct.sorted.mkString("+")))
                  case None if eqIdx < 0 => Left(Why.translatorRefusal("unreadable-signature"))
                  case None              => Right(body)

        decision match
          case Right(body) =>
            for i <- method.signatureLine to sigEndLineIdx do
              val line = lines(i)
              sb.append(if i == sigEndLineIdx then line.substring(0, eqIdx + 1) else line)
              sb.append("\n")
            // Replace the return-type placeholder with the method's declared return type
            val bodyText =
              if body.text.contains(dedicated.DefmethodBodyTranslator.ReturnTypePlaceholder) then
                val retType = extractReturnType(lines, method.signatureLine, sigEndLineIdx, eqIdx)
                retType match
                  case Some(t) => body.text.replace(dedicated.DefmethodBodyTranslator.ReturnTypePlaceholder, t)
                  case None    => body.text.replace(dedicated.DefmethodBodyTranslator.ReturnTypePlaceholder, "Any")
              else body.text
            sb.append(bodyText)
            bodyEntries += BodyEntry(method.name, Source.Translated, "", body.refusals.size, idx, method.signatureLine)
            totalRefusals += body.refusals.size

          case Left(why) =>
            for i <- method.signatureLine to method.bodyEndLine do
              sb.append(lines(i))
              sb.append("\n")
            bodyEntries += BodyEntry(method.name, Source.Reference, why, 0, idx, method.signatureLine)

        lineIdx = method.bodyEndLine + 1
        methodIdx += 1
      else
        sb.append(lines(lineIdx))
        sb.append("\n")
        lineIdx += 1

    // A member the reader never offers keeps its reference body; the skeleton's own classification says why it could not be offered.
    val unoffered = ReferenceSkeleton.unofferedMembers(lines, methods).map { m =>
      BodyEntry(m.name, Source.Reference, Why.skeletonCannotOffer(m.reason), 0, -1, m.line, offered = false)
    }

    ParityResult(
      emittedSource = sb.toString,
      bodies = bodyEntries.toList,
      totalMethods = methods.size,
      rastCount = bodyEntries.count(_.source == Source.Translated),
      referenceCount = bodyEntries.count(_.source == Source.Reference),
      totalRefusals = totalRefusals,
      unoffered = unoffered
    )

  /** Extract the return type from a method signature: the text between the last `)` and `:` before `=`. For `def f(x: Int): String =` the result is `Some("String")`.
    */
  private def extractReturnType(lines: List[String], sigStart: Int, sigEnd: Int, eqIdx: Int): Option[String] =
    // Join the signature lines into one string up to the =
    val sig = (sigStart to sigEnd)
      .map { i =>
        val line = lines(i)
        if i == sigEnd then line.substring(0, eqIdx) else line
      }
      .mkString(" ")
    // Find the last `:` after the last `)` and before the `=`
    val lastParen  = sig.lastIndexOf(')')
    val colonStart = if lastParen >= 0 then lastParen + 1 else 0
    val colonIdx   = sig.indexOf(':', colonStart)
    if colonIdx >= 0 then
      val retType = sig.substring(colonIdx + 1).trim
      if retType.nonEmpty then Some(retType) else None
    else None

  /** The older input shape, one occurrence list per name with a refusal count in place of the reasons. */
  def derive(
    referenceSource: String,
    rastBodies:      Map[String, List[(String, Int)]],
    policy:          Policy = Policy()
  )(using DummyImplicit): ParityResult =
    derive(referenceSource, Bodies.fromCounts(rastBodies), policy)

  /** The older input shape with ONE body per name; a second member of that name is out of range. */
  def derive(
    referenceSource: String,
    rastBodies:      Map[String, (String, Int)],
    policy:          Policy
  )(using DummyImplicit, DummyImplicit): ParityResult =
    derive(referenceSource, Bodies.fromCounts(rastBodies.map { case (k, v) => k -> List(v) }), policy)
