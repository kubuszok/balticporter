package balticporter.corpus.demo

import balticporter.emit.CtorOverride

/** PLAN §7 declaration-level constructor overrides for the flexmark-ext-xwiki-macros cold port —
  * the irreducible constructor shapes the funnel refuses (genuinely different super targets).
  * Only the constructor block is handwritten; the engine still translates every other member, so
  * these stay in sync with upstream on re-port. Keyed by FQCN. */
object XwikiOverrides:

  val map: Map[String, CtorOverride] = Map(
    // TagRange(tag, Range) → super(range) [Range copy-ctor]; TagRange(tag, int, int)
    // → super(start, end). super(range) ≡ super(range.getStart, range.getEnd), so the
    // primary takes (tag, start, end) and the copy form delegates through the getters.
    "com.vladsch.flexmark.util.sequence.TagRange" -> CtorOverride(
      headerSuffix = " protected (tag0: CharSequence, start: Int, end: Int) extends Range(start, end)",
      body = List(
        "protected val tag: String = String.valueOf(tag0)",
        "",
        "def this(tag: CharSequence, range: Range) = this(tag, range.getStart(), range.getEnd())",
      ),
    ),
  )
