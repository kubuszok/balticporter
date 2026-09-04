/*
 * Ported from gdx-ai — https://github.com/libgdx/gdx-ai
 * Original source: com/badlogic/gdx/ai/btree/utils/BehaviorTreeParser.java (the reflective half)
 * Original authors: davebaol
 * Licensed under the Apache License, Version 2.0
 *
 * INJECTED SCALA — the REDIRECT TARGET for `com.badlogic.gdx.utils.reflect.Field`
 * (`TypeRedirectTransform`, see `GdxAiPolicy`).
 *
 * `DefaultBehaviorTreeReader` names `reflect.Field` in THREE SIGNATURES — `getField`'s return type,
 * `setField`'s and `castValue`'s first parameter — and the libGDX base drops that type outright,
 * because runtime reflection is the one thing Scala.js and Native cannot do. A body substitution
 * cannot reach a signature and a `dropMethods` cut would take `castValue` with it, which upstream
 * documents as an OVERRIDE POINT ("Subclasses may override this method to parse unsupported
 * types"). So every occurrence is re-pointed at this type instead: the three signatures stay, the
 * three call sites inside the enum constant `Statement.TreeTask` stay MECHANICALLY TRANSLATED, and
 * the port loses no member.
 *
 * WHAT IT IS. A `reflect.Field` carried two things the parser used: a way to COERCE a serialized
 * value to the field's declared type (`castValue` switched on `field.getType()`), and a way to
 * STORE it (`field.set`). This carries the same two as closures supplied at registration, so the
 * field's type is known statically where the closure is written instead of being asked of the JVM
 * at run time. `getName`/`getTypeName` are what the parser's error message needs
 * (`field.getName()` and `field.getType().getSimpleName()` in java).
 */
package sge.ai.btree.utils

/** One `@TaskAttribute` slot of a task class, resolved WITHOUT reflection.
  *
  * Constructed only by [[TaskRegistry]] — a slot with no coercion and no store is a slot that
  * cannot do either half of what `reflect.Field` did here.
  *
  * @param fieldName
  *   the JAVA field's name, which is what `AttrInfo.fieldName` holds and what `getField` is asked
  *   for. Deliberately NOT the emitted Scala name: a §4.55 rename may have moved it
  *   (`Random.success` emits as `success$shadow`), and the only place that difference belongs is
  *   inside the [[assign]] closure, which is written against the emitted member.
  * @param typeName
  *   the declared type's SIMPLE name, for the `attribute '…' must be of type …` message java
  *   builds from `field.getType().getSimpleName()`.
  * @param coerce
  *   java's `castValue` for THIS field's type — `null` where the value is not assignable, which is
  *   exactly the signal `setField` tests for.
  * @param assign
  *   the store. Takes the task and the coerced value as `Object`s, because the parser holds both at
  *   `Task<E>` and `Object`; the closure knows the concrete class and casts.
  */
final class TaskField private[utils] (
    fieldName: String,
    typeName: String,
    coerce: (java.lang.Object, sge.ai.btree.utils.DistributionAdapters) => java.lang.Object,
    assign: (java.lang.Object, java.lang.Object) => Unit,
) {

  /** java's `Field#getName()`. */
  def getName(): String = fieldName

  /** java's `field.getType().getSimpleName()`, precomputed — the port has no `Class` to ask. */
  def getTypeName(): String = typeName

  /** java's `castValue(field, value)` for this one field: the coerced value, or `null` where the
    * parsed value is not assignable to it. `null` and not an `Option` — this is java's own protocol
    * and `setField` tests it exactly as java did. */
  def cast(value: java.lang.Object, adapters: sge.ai.btree.utils.DistributionAdapters): java.lang.Object =
    coerce(value, adapters)

  /** java's `field.set(task, valueObject)`. */
  def set(task: java.lang.Object, value: java.lang.Object): Unit = assign(task, value)
}
