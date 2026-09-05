package balticporter.frontend.spoon

// Split out of SpoonTir.scala for file size (context diet S2): the symbol-key interner.

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

/** Interns symbols by a stable string key (qualified names for types, `owner#member`
  * for members, `decl$$Name` for type params). One id per key, monotonic. */
private[spoon] final class Minter:
  private[spoon] var next  = 0
  private[spoon] val byKey = collection.mutable.Map[String, SymId]()
  private[spoon] val syms  = collection.mutable.Map[SymId, Symbol]()

  private[spoon] def resolve(key: String): SymId =
    byKey.getOrElseUpdate(key, { val id = SymId(next); next += 1; id })

  private[spoon] def set(id: SymId, sym: Symbol): Unit = syms(id) = sym

  /** Register a SECOND key for an existing SymId, so `resolve`/`external` on the alias return
    * `id` rather than minting a new one. Used for anonymous classes, whose internal key and
    * Spoon's `getQualifiedName` key must resolve to the same symbol. */
  private[spoon] def alias(key: String, id: SymId): Unit = byKey(key) = id

  private[spoon] def define(key: String)(mk: SymId => Symbol): SymId =
    val id = resolve(key)
    syms(id) = mk(id)
    id

  /** Ensure a minimal stub exists for an external reference; never clobbers a real definition.
    * `owner` is `SymId.None` for a TYPE (external types are rooted outside the program); an
    * external MEMBER must carry its owning type's id, or `owner#name` cannot identify it and
    * every ownership-keyed lookup (e.g. `PortabilityCheck`) silently never fires. */
  private[spoon] def external(key: String, name: String, owner: SymId = SymId.None,
               descriptor: Option[Descriptor] = None, info: TypeRepr = NoType,
               annotations: List[Annot] = Nil, flags: Flags = Flags()): SymId =
    val id = resolve(key)
    if !syms.contains(id) then syms(id) = Symbol(id, name, key, flags, owner, info, descriptor = descriptor, annotations = annotations)
    else
      // fill holes only — never overwrite a real declaration (that happens via `define`)
      var s = syms(id)
      if descriptor.isDefined && s.descriptor.isEmpty then s = s.copy(descriptor = descriptor)
      if info != NoType && s.info == NoType then s = s.copy(info = info)
      if annotations.nonEmpty && s.annotations.isEmpty then s = s.copy(annotations = annotations)
      if flags.isFinal && !s.flags.isFinal then s = s.copy(flags = s.flags.copy(isFinal = true))
      // `isResolved` fills the same way: resolution is a property of the NAME against the
      // frontend classpath, so the first reference that resolved it answers for every later one,
      // and a `define` for a type the model declares replaces the stub whole.
      if flags.isResolved && !s.flags.isResolved then s = s.copy(flags = s.flags.copy(isResolved = true))
      syms(id) = s
    id

  private[spoon] def table: SymbolTable        = SymbolTable(syms.values)
  private[spoon] def idOf(key: String): SymId  = byKey(key)
  /** the symbol at `key` IF one was really defined there. Deliberately not `resolve`, which mints
    * an id for a key nobody defined and reaches the emitter as `?`. */
  private[spoon] def defined(key: String): Option[(SymId, Symbol)] =
    byKey.get(key).flatMap(id => syms.get(id).map(id -> _))
  private[spoon] def fullNameOf(id: SymId): String = syms.get(id).map(_.fullName).getOrElse("?")
  /** the DECLARED type this frontend interned for `id` — `NoType` where nothing was declared.
    * Answers *did this frontend retype this declaration?* (CLAUDE.md §4.56). */
  private[spoon] def infoOf(id: SymId): TypeRepr = syms.get(id).map(_.info).getOrElse(NoType)
  /** the interned OWNER of a member — the type that declares it (not the subclass name it was
    * reached through, T14). `SymId.None` for a type or an unresolved member. */
  private[spoon] def ownerOf(id: SymId): SymId = syms.get(id).map(_.owner).getOrElse(SymId.None)
