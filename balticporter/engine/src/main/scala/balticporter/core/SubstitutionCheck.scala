package balticporter.core

import java.nio.file.{ Files, Path }
import scala.meta.*
import scala.meta.tokenizers.Tokenized

/** Post-emission check over `outDir` that [[Substitutions]] were carried out. [[emittedDroppedTypes]] (before injection): the engine emitted a dropped type. [[scan]] (after injection): a dropped,
  * unreplaced type still named in CODE is fatal; one named only in a copied upstream comment is a counted, non-fatal mention. Both are found by a lexical scan, by upstream and emitted name.
  */
object SubstitutionCheck:

  enum Kind:
    /** The emitter wrote a file for a type the manifest dropped. */
    case Emitted

    /** Dropped, unreplaced, still referenced from code. */
    case Dangling

    /** Dropped, unreplaced, and named only in a comment the port copied verbatim from upstream. */
    case DocMention

  /** @param references
    *   how many emitted files still name the FQN in code (0 for [[Kind.Emitted]], 1 for [[Kind.DocMention]]).
    * @param file
    *   the emitted file holding a [[Kind.DocMention]], relative to the output directory; empty otherwise.
    */
  final case class Finding(kind: Kind, fqn: String, references: Int, file: String = "", line: Int = 0):

    /** Whether the finding must stop the run; a comment naming a missing type does not. */
    def fatal: Boolean = kind != Kind.DocMention

    /** Render with the engine/policy classification at the end. */
    def render: String = kind match
      case Kind.Emitted =>
        s"$fqn is declared dropped but the engine EMITTED it" +
          "  [engine bug: the emission skip did not fire — the mechanical translation is about " +
          "to shadow or collide with the replacement]"
      case Kind.Dangling =>
        s"$fqn is dropped, has no replacement, and is still referenced by $references file(s)" +
          "  [port policy or library-specific rule: supply an `inject` replacement at this FQN, or plug in a rule " +
          "that rewrites its uses away; the engine needs no change]"
      case Kind.DocMention =>
        s"$file:$line: an upstream comment names $fqn, a type this port does not have (the code no longer refers to it)" +
          "  [not a fault: comments are copied verbatim; edit the upstream comment or accept the mention]"

  /** Dropped types the engine nevertheless wrote a file for. Run BEFORE injection. */
  def emittedDroppedTypes(outDir: Path, subs: Substitutions): List[Finding] =
    subs.dropTypes.toList.sorted.filter(fqn => Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala"))).map(Finding(Kind.Emitted, _, 0))

  /** The fatal half of [[scan]]: dropped, unreplaced, still referenced from code. */
  def dangling(outDir: Path, subs: Substitutions, provided: String => Boolean = _ => false, emittedName: String => String = identity): List[Finding] =
    scan(outDir, subs, provided, emittedName).dangling

  /** What [[scan]] found: code references (fatal) and comment-only mentions (one row per type and file). */
  final case class Scan(dangling: List[Finding], docMentions: List[Finding])

  /** Every dropped, unreplaced type still named in the final tree. Run AFTER injection. `provided` says whether a root outside `outDir` (a platform row, `providedSources`) declares a drop's
    * replacement; `emittedName` maps an upstream FQN to the name the port spells it with, and both names are searched at identifier boundaries.
    */
  def scan(outDir: Path, subs: Substitutions, provided: String => Boolean = _ => false, emittedName: String => String = identity): Scan =
    def present(fqn: String): Boolean = Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala"))
    val open = subs.dropTypes.toList.sorted.filterNot(fqn => present(fqn) || present(emittedName(fqn)) || provided(fqn))
    if open.isEmpty then Scan(Nil, Nil)
    else
      val spellings: Map[String, List[String]] =
        open.map { fqn =>
          val at = emittedName(fqn)
          fqn -> List(fqn, fqn.replace('$', '.'), at, at.replace('$', '.')).distinct
        }.toMap
      val files = scalaSources(outDir).flatMap { p =>
        val text = Files.readString(p)
        // only a file naming a spelling somewhere is worth a lexical scan
        if spellings.valuesIterator.exists(_.exists(text.contains)) then Some(outDir.relativize(p).toString.replace('\\', '/') -> split(p.toString, text)) else None
      }
      val perType = open.map { fqn =>
        val names   = spellings(fqn)
        val inCode  = files.count((_, s) => names.exists(n => at(s.code, n) >= 0))
        val inNotes = files.flatMap { (rel, s) =>
          if names.exists(n => at(s.code, n) >= 0) then None
          else names.map(at(s.comments, _)).filter(_ >= 0).minOption.map(off => Finding(Kind.DocMention, fqn, 1, rel, lineOf(s.comments, off)))
        }
        (if inCode == 0 then None else Some(Finding(Kind.Dangling, fqn, inCode)), inNotes)
      }
      Scan(perType.flatMap(_._1), perType.flatMap(_._2))

  /** A file split by lexical class: `code` blanks every comment and literal text, `comments` keeps only upstream comments (porter notes and trivia markers blanked). Offsets and lines are the file's.
    */
  final private case class Split(code: String, comments: String)

  /** Tokenize as Scala 3; a file that does not tokenize is read as code throughout (minus engine-written notes), the conservative reading. */
  private def split(name: String, text: String): Split =
    dialects.Scala3(Input.VirtualFile(name, text)).tokenize match
      case Tokenized.Success(tokens) =>
        val code     = text.toCharArray
        val comments = blank(text)
        tokens.foreach {
          case t: Token.Comment =>
            val body = text.substring(t.start, t.end)
            if !body.startsWith(balticporter.tir.PorterNote.Marker) && !body.startsWith(balticporter.tir.TriviaMark.Marker) then text.getChars(t.start, t.end, comments, t.start)
            blankRange(code, t.start, t.end)
          case t @ (_: Token.Constant.String | _: Token.Constant.Char | _: Token.Interpolation.Part) => blankRange(code, t.start, t.end)
          case _                                                                                     => ()
        }
        Split(String(code), String(comments))
      case _ => Split(withoutPorterNotes(text), "")

  private def blank(text: String): Array[Char] = text.toCharArray.map(c => if c == '\n' then c else ' ')

  private def blankRange(cs: Array[Char], from: Int, until: Int): Unit =
    var i = from
    while i < until do
      if cs(i) != '\n' then cs(i) = ' '
      i += 1

  /** Offset of the first occurrence of `name` in `text` bounded by non-identifier characters on both sides, or -1. */
  private def at(text: String, name: String): Int =
    def ident(c: Char): Boolean = Character.isUnicodeIdentifierPart(c) || c == '$' || c == '_'
    var i = text.indexOf(name)
    var found = -1
    while i >= 0 && found < 0 do
      val end = i + name.length
      if (i == 0 || !ident(text.charAt(i - 1))) && (end >= text.length || !ident(text.charAt(end))) then found = i
      else i = text.indexOf(name, i + 1)
    found

  private def lineOf(text: String, offset: Int): Int = text.substring(0, offset).count(_ == '\n') + 1

  /** Strip porter notes and recovery markers, the text the engine itself wrote into comments. */
  def withoutPorterNotes(text: String): String = balticporter.tir.TriviaMark.stripAll(text)

  /** Every `.scala` file under `dir`. Delegates to [[Substitutions.scalaSources]]. */
  def scalaSources(dir: Path): List[Path] = Substitutions.scalaSources(dir)
