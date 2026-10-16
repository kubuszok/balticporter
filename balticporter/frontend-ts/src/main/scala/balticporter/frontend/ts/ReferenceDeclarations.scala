package balticporter.frontend.ts

import scala.meta.*

/** Reads a hand-written reference Scala source STRUCTURALLY: every member a template, an extension group or a package declares, with the owner that declares it, its access and the line its name is
  * on. Indentation says nothing about ownership; the parse does.
  */
object ReferenceDeclarations:

  enum Kind:
    case Def, AbstractDef, Constructor, Val, Var, Type, Object, Class, Trait, Enum, EnumCase

  /** Where a method's body sits, 0-based: the signature's `=` (line and column) and the body's last line. */
  final case class BodySpan(equalsLine: Int, equalsColumn: Int, endLine: Int)

  /** `owner` is the path of the enclosing templates (`Outer.Inner`), empty at package level. `ownerIsObject` holds when every template on that path is an object, so the member is reachable as
    * `owner.name`. `isPrivate` is an UNQUALIFIED `private` or `protected`: such a member is not accessible from outside its owner. `line` is 0-based, the line of the member's NAME.
    */
  final case class Declaration(
    owner:         String,
    ownerIsObject: Boolean,
    name:          String,
    kind:          Kind,
    isPrivate:     Boolean,
    line:          Int,
    body:          Option[BodySpan] = None,
    tpe:           Option[String] = None,
    typeParams:    Int = 0,
    result:        Option[String] = None,
    params:        List[String] = Nil,
    parents:       List[String] = Nil,
    constant:      Option[String] = None,
    alias:         Option[String] = None
  ):
    /** A member a reference skeleton emits as a method: a def, abstract or concrete, or a secondary constructor. */
    def isMethod: Boolean = kind == Kind.Def || kind == Kind.AbstractDef || kind == Kind.Constructor

  /** The declarations of one source, or the parser's message when it does not parse. */
  def read(fileName: String, source: String): Either[String, List[Declaration]] =
    dialects.Scala3(Input.VirtualFile(fileName, source)).parse[Source] match
      case e: Parsed.Error => Left(s"$fileName: ${e.message}")
      case Parsed.Success(tree) =>
        val out = List.newBuilder[Declaration]

        def closed(mods: List[Mod]): Boolean =
          mods.exists {
            case p: Mod.Private   => p.within.syntax.isEmpty || p.within.is[Term.This]
            case p: Mod.Protected => p.within.syntax.isEmpty || p.within.is[Term.This]
            case _ => false
          }

        def child(owner: String, name: String): String = if owner.isEmpty then name else s"$owner.$name"

        def add(
          owner:  String,
          isObj:  Boolean,
          name:   Name,
          kind:   Kind,
          mods:   List[Mod],
          body:   Option[BodySpan] = None,
          tpe:    Option[Type] = None,
          result: Option[Type] = None,
          params: List[String] = Nil,
          value:  Option[Term] = None,
          alias:  Option[String] = None
        ): Unit =
          out += Declaration(
            owner,
            isObj,
            name.value,
            kind,
            closed(mods),
            name.pos.startLine,
            body,
            tpe.map(_.syntax),
            result = result.map(_.syntax),
            params = params,
            constant = value.collect { case Lit.String(s) => s },
            alias = alias
          )

        // the declared types of a method's explicit parameters, every clause but a `using` one, in order
        def paramTypes(groups: List[Member.ParamClauseGroup]): List[String] =
          for
            g <- groups
            c <- g.paramClauses
            if !c.mod.exists(_.is[Mod.Using])
            p <- c.values
          yield p.decltpe.fold("?")(_.syntax)

        // the type a value-like member is read at: a val or var's, or a parameterless def's result
        def valueType(d: Defn.Def): Option[Type] = if d.paramClauseGroups.isEmpty then d.decltpe else None

        // The signature's `=` is the last one before the body starts; a default parameter's comes earlier.
        def span(whole: Tree, body: Tree): Option[BodySpan] =
          whole.tokens.filter(t => t.is[Token.Equals] && t.end <= body.pos.start).lastOption.map { eq =>
            BodySpan(eq.pos.startLine, eq.pos.startColumn, body.pos.endLine)
          }

        def pats(ps: List[Pat]): List[Name] = ps.flatMap(_.collect { case Pat.Var(n) => n })

        // the simple names of the classes and traits a template extends, type arguments dropped
        def parentNames(t: Template): List[String] =
          t.inits.map(_.tpe.syntax.takeWhile(c => c != '[' && c != '(').split('.').last.trim).filter(_.nonEmpty)

        def template(t: Template, owner: String, isObj: Boolean): Unit = t.body.stats.foreach(member(_, owner, isObj))

        def member(t: Tree, owner: String, isObj: Boolean): Unit = t match
          case p: Pkg         => p.stats.foreach(member(_, "", true))
          case p: Pkg.Object  => template(p.templ, "", true)
          case d: Defn.Object =>
            add(owner, isObj, d.name, Kind.Object, d.mods)
            template(d.templ, child(owner, d.name.value), isObj)
          case d: Defn.Class =>
            out += Declaration(
              owner,
              isObj,
              d.name.value,
              Kind.Class,
              closed(d.mods),
              d.name.pos.startLine,
              typeParams = d.tparamClause.values.size,
              parents = parentNames(d.templ)
            )
            template(d.templ, child(owner, d.name.value), false)
          case d: Defn.Trait =>
            out += Declaration(
              owner,
              isObj,
              d.name.value,
              Kind.Trait,
              closed(d.mods),
              d.name.pos.startLine,
              typeParams = d.tparamClause.values.size,
              parents = parentNames(d.templ)
            )
            template(d.templ, child(owner, d.name.value), false)
          case d: Defn.Enum =>
            add(owner, isObj, d.name, Kind.Enum, d.mods)
            template(d.templ, child(owner, d.name.value), false)
          case d: Defn.Given    => template(d.templ, child(owner, d.name.value), false)
          case d: Defn.EnumCase => add(owner, isObj, d.name, Kind.EnumCase, d.mods)
          case d: Defn.Def      =>
            add(
              owner,
              isObj,
              d.name,
              Kind.Def,
              d.mods,
              span(d, d.body),
              valueType(d),
              d.decltpe,
              paramTypes(d.paramClauseGroups),
              Option.when(d.paramClauseGroups.isEmpty)(d.body)
            )
          case d: Ctor.Secondary => add(owner, isObj, d.name, Kind.Constructor, d.mods)
          case d: Decl.Def       =>
            add(
              owner,
              isObj,
              d.name,
              Kind.AbstractDef,
              d.mods,
              tpe = if d.paramClauseGroups.isEmpty then Some(d.decltpe) else None,
              result = Some(d.decltpe),
              params = paramTypes(d.paramClauseGroups)
            )
          case d: Defn.Type =>
            // a type alias with no type parameters names its right-hand side
            add(owner, isObj, d.name, Kind.Type, d.mods, alias = Option.when(d.tparamClause.values.isEmpty)(d.body.syntax))
          case d: Decl.Type           => add(owner, isObj, d.name, Kind.Type, d.mods)
          case d: Defn.Val            => pats(d.pats).foreach(add(owner, isObj, _, Kind.Val, d.mods, tpe = d.decltpe, value = Some(d.rhs)))
          case d: Decl.Val            => pats(d.pats).foreach(add(owner, isObj, _, Kind.Val, d.mods, tpe = Some(d.decltpe)))
          case d: Defn.Var            => pats(d.pats).foreach(add(owner, isObj, _, Kind.Var, d.mods, tpe = d.decltpe))
          case d: Decl.Var            => pats(d.pats).foreach(add(owner, isObj, _, Kind.Var, d.mods, tpe = Some(d.decltpe)))
          case d: Defn.ExtensionGroup =>
            d.body match
              case Term.Block(stats) => stats.foreach(member(_, owner, false))
              case other             => member(other, owner, false)
          case _ => ()

        tree.stats.foreach(member(_, "", true))
        Right(out.result())

  /** What a body emitted into one reference file can name bare: the type names the file writes in a type position, and the values it declares at package level with their declared types. */
  final case class FileNames(types: Set[String], terms: Map[String, Option[String]])

  object FileNames:
    val empty: FileNames = FileNames(Set.empty, Map.empty)

  /** The [[FileNames]] of one reference source; empty when it does not parse. */
  def namesIn(fileName: String, source: String): FileNames =
    val terms = read(fileName, source).toOption.toList.flatten.collect { case d if d.owner.isEmpty && (d.kind == Kind.Val || d.kind == Kind.Var) => d.name -> d.tpe }
    FileNames(typeNamesIn(fileName, source), terms.toMap)

  /** The simple type names `source` writes in a type position, never a type parameter, a declaration's own name or the last part of a qualified type: a body emitted into this file can write each one
    * and mean what the file's own signatures mean by it. Empty when the source does not parse.
    */
  def typeNamesIn(fileName: String, source: String): Set[String] =
    dialects.Scala3(Input.VirtualFile(fileName, source)).parse[Source] match
      case _: Parsed.Error => Set.empty
      case Parsed.Success(tree) =>
        val notWritten = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[Tree, java.lang.Boolean]())
        val tparams    = scala.collection.mutable.Set.empty[String]
        tree.traverse {
          case s: Type.Select  => notWritten.add(s.name)
          case p: Type.Project => notWritten.add(p.name)
          case d: Defn.Class   => notWritten.add(d.name)
          case d: Defn.Trait   => notWritten.add(d.name)
          case d: Defn.Enum    => notWritten.add(d.name)
          case d: Defn.Type    => notWritten.add(d.name)
          case d: Decl.Type    => notWritten.add(d.name)
          case p: Type.Param   => tparams += p.name.value; notWritten.add(p.name)
        }
        tree.collect { case n: Type.Name if !notWritten.contains(n) && !tparams.contains(n.value) => n.value }.toSet
