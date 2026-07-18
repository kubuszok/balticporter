package balticporter.emit

import balticporter.core.*
import balticporter.core.BExpr.*

/** Java permits a field and a method with the same name in one class; Scala does not.
  * Non-public clashing fields are renamed `x` → `x$fld` with their own-field
  * references rewritten. Public clashing fields would change the class's API — those
  * fail loudly instead.
  */
object MemberClashPass:

  def apply(unit: BUnit): BUnit =
    unit.copy(types = unit.types.map(t => fixLocals(fixType(t, unit))))

  /** A Java local may share the name of a method it calls in its own initializer
    * (`Object x = x(...)`) — Scala's block scoping makes that a self-reference.
    * Rename such locals `x` → `x$loc` (Ident(Local) refs only; calls are untouched). */
  private def fixLocals(t0: BTypeDecl): BTypeDecl =
    val t = t0.copy(nested = t0.nested.map(fixLocals))
    val methodNames = (t.methods ++ t.staticMethods).map(_.name).toSet
    if methodNames.isEmpty then t
    else
      def renStmt(s: BStmt): BStmt =
        val mapped = BirTransform.mapStmt(s) {
          case Ident(n, RefKind.Local) if methodNames.contains(n) => Ident(n + "$loc", RefKind.Local)
          case e                                                  => e
        }
        renameLocalDecls(mapped, methodNames)
      def fixM(m: BMethod): BMethod = m.copy(body = m.body.map(_.map(renStmt)))
      t.copy(
        methods = t.methods.map(fixM),
        staticMethods = t.staticMethods.map(fixM),
        ctors = t.ctors.map(c => c.copy(body = c.body.map(renStmt))),
        staticInit = t.staticInit.map(renStmt),
        instanceInit = t.instanceInit.map(renStmt),
      )

  private def renameLocalDecls(s: BStmt, names: Set[String]): BStmt =
    def r(x: BStmt): BStmt = renameLocalDecls(x, names)
    val k = s.k match
      case lv: BStmtK.LocalVar if names.contains(lv.name) => lv.copy(name = lv.name + "$loc")
      case BStmtK.If(c, tb, eb)      => BStmtK.If(c, tb.map(r), eb.map(_.map(r)))
      case BStmtK.While(c, b)        => BStmtK.While(c, b.map(r))
      case BStmtK.Block(b)           => BStmtK.Block(b.map(r))
      case BStmtK.Try(b, cs, f)      => BStmtK.Try(b.map(r), cs.map(c => c.copy(body = c.body.map(r))), f.map(_.map(r)))
      case BStmtK.Boundary(b, l)     => BStmtK.Boundary(b.map(r), l)
      case BStmtK.Match(scr, cases)  => BStmtK.Match(scr, cases.map(c => c.copy(body = c.body.map(r))))
      case other                     => other
    s.copy(k = k)

  private def fixType(t0: BTypeDecl, unit: BUnit): BTypeDecl =
    val t = t0.copy(nested = t0.nested.map(fixType(_, unit)))

    val instClash = t.fields.map(_.name).toSet.intersect(t.methods.map(_.name).toSet)
    val statClash = t.staticFields.map(_.name).toSet.intersect(t.staticMethods.map(_.name).toSet)
    if instClash.isEmpty && statClash.isEmpty then t
    else
      (instClash ++ statClash).find { n =>
        (t.fields ++ t.staticFields).exists(f => f.name == n && f.mods.vis == Vis.Public)
      } match
        case Some(n) =>
          throw Unsupported(unit.sourcePath, t.name, s"public field '$n' clashes with method of the same name")
        case None => ()

      def newName(n: String) = n + "$fld"
      val fqcn = if unit.pkg.isEmpty then t.name else s"${unit.pkg}.${t.name}"

      val renameRefs: BExpr => BExpr = {
        case Ident(n, RefKind.OwnField) if instClash.contains(n) => Ident(newName(n), RefKind.OwnField)
        case Ident(n, RefKind.StaticField(owner)) if statClash.contains(n) && owner.replace('$', '.') == fqcn =>
          Ident(newName(n), RefKind.StaticField(owner))
        case e => e
      }

      // rewrite refs everywhere in this type EXCEPT nested types (they were fixed
      // independently and may legitimately reuse the names)
      val nested = t.nested
      val rewritten = BirTransform.mapTypeDecl(t.copy(nested = Nil))(renameRefs)
      rewritten.copy(
        fields = rewritten.fields.map(f => if instClash.contains(f.name) then f.copy(name = newName(f.name)) else f),
        staticFields = rewritten.staticFields.map(f => if statClash.contains(f.name) then f.copy(name = newName(f.name)) else f),
        nested = nested,
      )
