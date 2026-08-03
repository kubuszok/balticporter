package balticporter.emit

import balticporter.core.*
import balticporter.core.BExpr.*

/** Java permits a field and a method with the same name in one class; Scala does not.
  * Non-public clashing fields are renamed `x` → `x$fld` with their own-field
  * references rewritten. Public clashing fields would change the class's API — those
  * fail loudly instead.
  */
object MemberClashPass:

  def apply(unit: BUnit, registry: Option[CtorRegistry] = None): BUnit =
    val collapsed = registry.map(_.collapsedAccessors).getOrElse(Set.empty)
    val dropped = registry.map(_.droppedDeprecatedClashes).getOrElse(Set.empty)
    val pkgPrefix = if unit.pkg.isEmpty then "" else unit.pkg + "."
    val u1 =
      if collapsed.isEmpty then unit
      else
        // resolved nilary calls to collapsed accessors become field reads — in
        // EVERY unit (the accessor's declaring unit no longer emits the method)
        unit.copy(types = unit.types.map(BirTransform.mapTypeDecl(_) {
          case Call(recv, n, Nil, _, Some(owner)) if collapsed((owner, n)) =>
            recv match
              case Recv.On(r)     => Select(r, n)
              case Recv.OnThis    => Ident(n, RefKind.OwnField)
              case Recv.Static(o) => Ident(n, RefKind.StaticField(o))
              case Recv.OnSuper   => Select(This, n)
          case e => e
        }))
    val u2 = u1.copy(types = u1.types.map(t => dropMembers(t, pkgPrefix + t.name, collapsed ++ dropped)))
    // inherited method names (CharSequence.length() etc.) so a local named like one
    // is renamed too — Java resolves `length()` in `int length = length()` to the method
    def inheritedMethods(t: BTypeDecl): Set[String] =
      registry match
        case None => Set.empty
        case Some(reg) =>
          val supers = t.superClass.toList.map(_.qname) ++ t.interfaces.collect { case BType.Ref(q, _) => q }
          supers.flatMap(reg.inheritedMethodNames(_)).toSet
    u2.copy(types = u2.types.map(t => qualifyParamShadowedThisCalls(fixLocals(fixType(t, unit), inheritedMethods(t)))))

  /** A method parameter shadowing a same-named this-callable method makes a bare
    * `m(...)` call resolve to the parameter (`param.apply(...)` → "does not take
    * parameters" for a nilary `length()`). Qualify such calls as `this.m(...)`. Safe:
    * a `Recv.OnThis` call always targets `this`'s method (a parameter used as a
    * function is invoked through `Recv.On`), so the qualification never changes which
    * member is meant — it only defeats the parameter's lexical shadow. */
  private def qualifyParamShadowedThisCalls(t0: BTypeDecl): BTypeDecl =
    val t = t0.copy(
      nested = t0.nested.map(qualifyParamShadowedThisCalls),
      inner = t0.inner.map(qualifyParamShadowedThisCalls),
    )
    def fixM(m: BMethod): BMethod =
      val pnames = m.params.map(_.name).toSet
      if pnames.isEmpty then m
      else
        m.copy(body = m.body.map(_.map(s => BirTransform.mapStmt(s) {
          case Call(Recv.OnThis, n, args, f, o) if pnames.contains(n) => Call(Recv.On(This), n, args, f, o)
          case e                                                      => e
        })))
    t.copy(methods = t.methods.map(fixM), staticMethods = t.staticMethods.map(fixM))

  /** removes collapsed/dropped accessor methods, hoisting their trivia onto the
    * same-named field so the comment invariant holds. Recurses with $-qualified
    * names matching the registry's keys. */
  private def dropMembers(t: BTypeDecl, fqcn: String, gone: Set[(String, String)]): BTypeDecl =
    def hoist(fields: List[BField], removed: List[BMethod]): List[BField] =
      fields.map { f =>
        removed.filter(_.name == f.name) match
          case Nil => f
          case ms  => f.copy(leading = f.leading ++ ms.flatMap(_.leading))
      }
    val (goneInst, keepInst) = t.methods.partition(m => m.params.isEmpty && gone((fqcn, m.name)))
    val (goneStat, keepStat) = t.staticMethods.partition(m => m.params.isEmpty && gone((fqcn, m.name)))
    t.copy(
      fields = hoist(t.fields, goneInst),
      staticFields = hoist(t.staticFields, goneStat),
      methods = keepInst,
      staticMethods = keepStat,
      nested = t.nested.map(n => dropMembers(n, s"$fqcn$$${n.name}", gone)),
      inner = t.inner.map(n => dropMembers(n, s"$fqcn$$${n.name}", gone)),
    )

  /** A Java local may share the name of a method it calls in its own initializer
    * (`Object x = x(...)`) — Scala's block scoping makes that a self-reference.
    * Rename such locals `x` → `x$loc` (Ident(Local) refs only; calls are untouched). */
  private def fixLocals(t0: BTypeDecl, inherited: Set[String] = Set.empty): BTypeDecl =
    val t = t0.copy(nested = t0.nested.map(fixLocals(_, inherited)))
    // this-method calls in the bodies — catches JDK-inherited methods (length() from
    // CharSequence) that aren't in the closure registry: a local shadowing one still clashes
    val thisCallNames = collection.mutable.Set[String]()
    def scan(body: List[BStmt]): Unit =
      body.foreach(s => BirTransform.mapStmt(s) {
        case c @ Call(Recv.OnThis, n, _, _, _) => thisCallNames += n; c
        case e                                 => e
      })
    (t.methods ++ t.staticMethods).foreach(_.body.foreach(scan))
    t.ctors.foreach(c => scan(c.body))
    val methodNames = (t.methods ++ t.staticMethods).map(_.name).toSet ++ inherited ++ thisCallNames
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
