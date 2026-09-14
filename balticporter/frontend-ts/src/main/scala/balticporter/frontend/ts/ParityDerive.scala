package balticporter.frontend.ts

import scala.collection.mutable

/** Unified parity-derive mechanism: the reference file's structure with
  * RAST-translated method bodies interleaved where a match exists and the
  * translated body passes a per-library uncompilable-pattern filter.
  *
  * Replaces the four independent implementations in TerserCompressEmitter,
  * KaTeXEmitter, DartSassEmitter, and MermaidEmitter. Each emitter keeps
  * its library-specific body map construction and pattern list; ParityDerive
  * owns only the interleaving algorithm and the provenance recording.
  */
object ParityDerive:

  /** Per-library policy controlling how bodies are matched and filtered. */
  final case class Policy(
      uncompilablePatterns: List[String] = Nil,
      allowPrivate: Boolean = true,
  )

  /** Provenance record for one method in a parity-derived module. */
  final case class BodyEntry(
      methodName: String,
      source: String,
      why: String,
      rastRefusalCount: Int = 0,
  )

  /** Result of a parity-derive run over one reference file. */
  final case class ParityResult(
      emittedSource: String,
      bodies: List[BodyEntry],
      totalMethods: Int,
      rastCount: Int,
      referenceCount: Int,
      totalRefusals: Int,
  )

  /** Run parity-derive: interleave RAST bodies into a reference source.
    *
    * @param referenceSource the complete Scala source of the reference file
    * @param rastBodies map from method name to list of (translated body, refusal count);
    *   a list because the same method name may appear multiple times in the reference
    *   (e.g., inner class methods); bodies are consumed sequentially per name.
    * @param policy per-library filtering rules
    */
  def derive(
      referenceSource: String,
      rastBodies: Map[String, List[(String, Int)]],
      policy: Policy = Policy(),
  ): ParityResult =
    val lines = referenceSource.split("\n", -1).toList
    val methods = dedicated.TerserCompressEmitter.findMethodBoundaries(lines)

    val sb = new StringBuilder
    val bodyEntries = mutable.ListBuffer.empty[BodyEntry]
    val consumed = mutable.Map.empty[String, Int].withDefaultValue(0)
    var totalRefusals = 0

    var lineIdx = 0
    var methodIdx = 0

    while lineIdx < lines.size do
      if methodIdx < methods.size && lineIdx == methods(methodIdx).signatureLine then
        val method = methods(methodIdx)

        val idx = consumed(method.name)
        consumed(method.name) = idx + 1

        val candidate = rastBodies.get(method.name).flatMap(_.lift(idx))

        val (usable, why) =
          if !policy.allowPrivate && method.isPrivate then
            (None, "private")
          else candidate match
            case None =>
              (None, "no-rast-symbol")
            case Some((body, _)) if containsPattern(body, policy.uncompilablePatterns) =>
              val pattern = policy.uncompilablePatterns.find(body.contains).getOrElse("unknown")
              (None, s"uncompilable-pattern:$pattern")
            case some =>
              (some, "")

        usable match
          case Some((translatedBody, refusals)) =>
            val sigEndLineIdx = dedicated.TerserCompressEmitter.findSignatureEnd(lines, method.signatureLine)
            for i <- method.signatureLine to sigEndLineIdx do
              val line = lines(i)
              if i == sigEndLineIdx then
                val eqIdx = dedicated.TerserCompressEmitter.findEqualsInSignature(line)
                if eqIdx >= 0 then
                  sb.append(line.substring(0, eqIdx + 1))
                  sb.append("\n")
                else
                  sb.append(line)
                  sb.append("\n")
              else
                sb.append(line)
                sb.append("\n")

            sb.append(translatedBody)
            lineIdx = method.bodyEndLine + 1
            bodyEntries += BodyEntry(method.name, "rast", "", refusals)
            totalRefusals += refusals

          case _ =>
            for i <- method.signatureLine to method.bodyEndLine do
              sb.append(lines(i))
              sb.append("\n")
            lineIdx = method.bodyEndLine + 1
            bodyEntries += BodyEntry(method.name, "reference", why)

        methodIdx += 1
      else
        sb.append(lines(lineIdx))
        sb.append("\n")
        lineIdx += 1

    val rast = bodyEntries.count(_.source == "rast")
    val ref = bodyEntries.count(_.source == "reference")

    ParityResult(
      emittedSource = sb.toString,
      bodies = bodyEntries.toList,
      totalMethods = methods.size,
      rastCount = rast,
      referenceCount = ref,
      totalRefusals = totalRefusals,
    )

  /** Convenience overload for emitters that use a single-body map (Terser, Mermaid styles). */
  def derive(
      referenceSource: String,
      rastBodies: Map[String, (String, Int)],
      policy: Policy,
  )(using ev: DummyImplicit): ParityResult =
    val listBodies = rastBodies.map { case (k, v) => k -> List(v) }
    derive(referenceSource, listBodies, policy)

  /** Format body entries as a TSV string (for bodies.tsv output). */
  def formatBodiesTsv(bodies: List[BodyEntry]): String =
    val sb = new StringBuilder
    sb.append("method_name\tsource\twhy\trefusal_count\n")
    for entry <- bodies do
      sb.append(s"${entry.methodName}\t${entry.source}\t${entry.why}\t${entry.rastRefusalCount}\n")
    sb.toString

  private def containsPattern(body: String, patterns: List[String]): Boolean =
    patterns.exists(body.contains)
