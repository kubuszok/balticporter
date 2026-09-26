package balticporter.verify

import balticporter.tir.CheckReport

import scala.meta.*

/** A repeated parameter (`xs: T*`) or a reference array handed BARE to another repeated parameter whose element is `Object`/`AnyRef`/`Any` or an unbounded type parameter. It compiles — a `Seq` and an
  * array are each one `Object` — and the callee receives ONE element holding the whole sequence where java forwarded the elements; the fix is `xs*`. Read over Scala TEXT the port ships (spliced
  * bodies, added members, injected and provided sources, the emitted tree), with the callee resolved only among the declarations that text holds: an unresolvable callee or identifier is not flagged.
  */
object RepeatedForwardCheck:

  val Name = "repeated-forward"

  enum Issue:
    /** a repeated parameter forwarded without `*` */
    case UnspreadRepeated

    /** a reference array at the repeated tail, without `*` */
    case UnspreadArray

  object Issue:
    def classification(i: Issue): String =
      "engine (true of every Java program read as Scala), and IT COMPILES: at a repeated `Object`-like " +
        "slot the whole sequence is ONE element, where java forwarded its elements. Spread it " +
        (if i == UnspreadArray then "(`arr*`) — java passed the array AS the varargs array." else "(`xs*`) — the text was written before the parameter became repeated.")

  final case class Finding(issue: Issue, file: String, line: Int, enclosing: String, param: String, callee: String):
    def detail: String = s"`$param` passed to `$callee`'s repeated `Object` slot without `*`: the callee receives one element holding it — write `$param*`"
    def render: String = s"$issue $enclosing: $detail  ($file:$line)"
    def report: CheckReport.Finding = CheckReport.Finding(Name, issue.toString, enclosing, file, line, detail)

  private val Universal = Set("Object", "java.lang.Object", "AnyRef", "scala.AnyRef", "Any", "scala.Any")
  private val Primitive = Set("Int", "Long", "Short", "Byte", "Char", "Float", "Double", "Boolean").flatMap(n => Set(n, s"scala.$n"))

  /** one callee shape: its parameter count, and whether the last one is a repeated `Object`-like slot. */
  private final case class Shape(params: Int, repeated: Boolean, universal: Boolean)

  /** @param sources (label, text) — a label is what a finding names as its file */
  def check(sources: List[(String, String)]): List[Finding] =
    val parsed = sources.flatMap { (label, text) =>
      dialects.Scala3(Input.VirtualFile(label, text)).parse[Source] match
        case Parsed.Success(tree) => List(label -> tree)
        case _                    => Nil
    }
    val shapes = collection.mutable.Map.empty[String, List[Shape]].withDefaultValue(Nil)
    def unbounded(tps: List[Type.Param]): Set[String] =
      tps.filter(tp => tp.tbounds.hi.forall(b => Universal(b.syntax))).map(_.name.value).toSet
    def shapeOf(ps: List[Term.Param], inScope: Set[String]): Shape =
      ps.lastOption.flatMap(_.decltpe) match
        case Some(Type.Repeated(el)) => Shape(ps.size, true, Universal(el.syntax) || inScope(el.syntax))
        case _                       => Shape(ps.size, false, false)
    def valueParams(d: Defn.Def | Decl.Def): List[Term.Param] =
      d.paramClauseGroups.flatMap(_.paramClauses).filterNot(_.mod.exists(_.is[Mod.Using])).flatMap(_.values)
    parsed.foreach { (_, tree) =>
      tree.traverse {
        case c: Defn.Class =>
          val cls = unbounded(c.tparamClause.values)
          c.templ.body.stats.foreach {
            case d: Defn.Def => shapes(d.name.value) = shapeOf(valueParams(d), cls ++ unbounded(d.paramClauseGroups.flatMap(_.tparamClause.values))) :: shapes(d.name.value)
            case d: Decl.Def => shapes(d.name.value) = shapeOf(valueParams(d), cls ++ unbounded(d.paramClauseGroups.flatMap(_.tparamClause.values))) :: shapes(d.name.value)
            case _           => ()
          }
        case o: Defn.Object =>
          o.templ.body.stats.foreach {
            case d: Defn.Def => shapes(d.name.value) = shapeOf(valueParams(d), unbounded(d.paramClauseGroups.flatMap(_.tparamClause.values))) :: shapes(d.name.value)
            case _           => ()
          }
        case t: Defn.Trait =>
          val cls = unbounded(t.tparamClause.values)
          t.templ.body.stats.foreach {
            case d: Defn.Def => shapes(d.name.value) = shapeOf(valueParams(d), cls ++ unbounded(d.paramClauseGroups.flatMap(_.tparamClause.values))) :: shapes(d.name.value)
            case d: Decl.Def => shapes(d.name.value) = shapeOf(valueParams(d), cls ++ unbounded(d.paramClauseGroups.flatMap(_.tparamClause.values))) :: shapes(d.name.value)
            case _           => ()
          }
      }
    }
    // every candidate by name that could take this many arguments must put this one in a repeated
    // universal tail — one ambiguous candidate is not proof, and is not flagged
    def slotIsUniversalTail(callee: String, argc: Int, at: Int): Boolean =
      val fits = shapes(callee).filter(s => if s.repeated then argc >= s.params - 1 else argc == s.params)
      fits.nonEmpty && fits.forall(s => s.repeated && s.universal && at >= s.params - 1)
    def calleeName(fun: Term): Option[String] = fun match
      case Term.Name(n)                => Some(n)
      case Term.Select(_, Term.Name(n)) => Some(n)
      case Term.ApplyType(f, _)        => calleeName(f)
      case _                           => scala.None
    val out = List.newBuilder[Finding]
    parsed.foreach { (label, tree) =>
      tree.traverse { case d: Defn.Def =>
        val forwardable: Map[String, Issue] = valueParams(d).flatMap { p =>
          p.decltpe match
            case Some(Type.Repeated(_)) => List(p.name.value -> Issue.UnspreadRepeated)
            case Some(t: Type.Apply) if (t.tpe.syntax == "Array" || t.tpe.syntax == "scala.Array") && t.argClause.values.sizeIs == 1 && !Primitive(t.argClause.values.head.syntax) =>
              List(p.name.value -> Issue.UnspreadArray)
            case _ => Nil
        }.toMap
        // a name bound again inside the body is not provably the parameter
        val rebound = collection.mutable.Set.empty[String]
        d.body.traverse {
          case Pat.Var(n)     => rebound += n.value
          case p: Term.Param  => rebound += p.name.value
          case dd: Defn.Def   => rebound += dd.name.value
        }
        val live = forwardable -- rebound
        if live.nonEmpty then
          d.body.traverse { case app: Term.Apply =>
            val fun  = app.fun
            val args = app.argClause.values
            calleeName(fun).foreach { callee =>
              args.zipWithIndex.foreach {
                case (Term.Name(n), at) if live.contains(n) && slotIsUniversalTail(callee, args.size, at) =>
                  out += Finding(live(n), label, args(at).pos.startLine + 1, d.name.value, n, callee)
                case _ => ()
              }
            }
          }
      }
    }
    out.result().distinct.sortBy(f => (f.file, f.line, f.param))
