package balticporter.frontend.spoon

// Split out of SpoonTir.scala for file size (context diet S2): BodyTranslator's own state, its small term-building helpers, and the exported view of its enclosing Builder.

import balticporter.core.{AnnotationPolicy, FrontendConfig, RealPath, Substituted, Substitutions}
import balticporter.catalog.{CatalogLog, Dispatch, JS, Lowering, Obligations, Typing}
import balticporter.tir.*
import balticporter.tir.TypeRepr.*

import spoon.Launcher
import spoon.reflect.code.*
import spoon.reflect.declaration.*
import spoon.reflect.reference.*
import spoon.support.adaption.TypeAdaptor
import spoon.support.compiler.VirtualFile

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

private[spoon] trait SpoonTirBodyCore:
  private[spoon] val outer: Builder
  private[spoon] def methodId: SymId
  private[spoon] def classId: SymId
  private[spoon] def anonSelf: SymId
  private[spoon] def anonQName: String
  export outer.*
  export outer.given

  private[spoon] val varIds  = new java.util.IdentityHashMap[CtVariable[?], SymId]()
  private[spoon] val nameIds = collection.mutable.Map[String, SymId]()

  private[spoon] def registerVar(v: CtVariable[?], id: SymId): Unit =
    varIds.put(v, id); nameIds(v.getSimpleName) = id

  /** locals visible from HERE, by name — handed to a nested anonymous class so the locals it
    * CAPTURES resolve to the real symbols rather than to `?var$x` stubs (the emitted text was
    * already right, since Scala captures by name too; this keeps the xref honest). */
  private[spoon] def varScope: Map[String, SymId] = nameIds.toMap
  private[spoon] def seedVars(m: Map[String, SymId]): Unit = m.foreach { (n, id) => if !nameIds.contains(n) then nameIds(n) = id }

  private[spoon] def nothingT = TypeRef(NoPrefix, minter.external("scala.Nothing", "Nothing"))
  private[spoon] def selfT    = TypeRef(NoPrefix, classId)
  private[spoon] def ty(e: CtTypedElement[?]): TypeRepr = Option(e.getType).map(tpe).getOrElse(NoType)
  private[spoon] def thisTerm(el: CtElement): Term  = Tree.This(classId, selfT, originOf(el))
  /** a java LABEL on a loop (`outer: for (…)`), the target of `break outer` / `continue outer`.
    * Kept so the emitter can name the corresponding `boundary` and jump to it explicitly. */
  private[spoon] def labelOf(s: CtStatement): Option[String] =
    try Option(s.getLabel).filter(_.nonEmpty) catch { case _: Throwable => scala.None }

  private[spoon] def superTerm(el: CtElement): Term = Tree.Super(classId, selfT, originOf(el))
  /** true when a `this`-access targets THIS class (not an enclosing one) — only then does
    * it need qualifying; an outer `Outer.this.x` resolves bare in Scala. */
  private[spoon] def isOwnThis(ta: CtThisAccess[?]): Boolean =
    Option(ta.getType).map(_.getQualifiedName).forall(_ == minter.fullNameOf(classId))

  /** A `this` used as a VALUE. Inside an anonymous class body it denotes the ANONYMOUS instance,
    * not the enclosing one — only for a `this` Spoon EXPLICITLY types as the anonymous class,
    * and only in value position (as a member-access TARGET, the existing bare-name resolution
    * stays in charge, matching java's own lexical resolution). */
  private[spoon] def thisOf(ta: CtThisAccess[?], el: CtElement): Term =
    if anonSelf != SymId.None && anonQName.nonEmpty &&
       Option(ta.getType).map(_.getQualifiedName).contains(anonQName)
    then Tree.This(anonSelf, TypeRef(NoPrefix, anonSelf), originOf(el))
    else thisTerm(el)

  /** `Outer.this` — the enclosing instance. Only for a type that LEXICALLY ENCLOSES the access;
    * Spoon also reports a plain `this` typed at an INHERITED member's DECLARING type, which
    * `Outer.this` syntax cannot denote and must not be qualified as. */
  private[spoon] def outerThis(ta: CtThisAccess[?]): Option[Term] =
    val q     = ta.getType.getQualifiedName
    var here  = ta.getParent(classOf[CtType[?]])
    var found = false
    // walk OUT only while each step captures an enclosing (non-static inner) instance
    while here != null && capturesEnclosing(here) && !found do
      here = here.getDeclaringType
      if here != null && here.getQualifiedName == q then found = true
    // an ANONYMOUS enclosing class has NO NAME — emitted bare, it resolves lexically as java did
    val anonymous = here match { case c: CtClass[?] => c.isAnonymous; case _ => false }
    Option.when(found && !anonymous) {
      val id = minter.external(q, simpleName(q))
      Tree.This(id, TypeRef(NoPrefix, id), originOf(ta))
    }

