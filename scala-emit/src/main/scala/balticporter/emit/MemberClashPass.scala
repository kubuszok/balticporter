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
    unit.copy(types = unit.types.map(fixType(_, unit)))

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
