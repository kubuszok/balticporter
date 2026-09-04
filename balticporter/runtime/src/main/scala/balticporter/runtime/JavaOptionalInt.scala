package balticporter.runtime

/** `java.util.OptionalInt`, as Scala — an ALIAS, because `Option[Int]` IS the answer.
  *
  * ==Why an alias and not the mapping straight to `scala.Option`==
  * Because the retype is ARITY-CHANGING and the mechanism is not. `CollectionsTransform` moves a
  * type by replacing the HEAD SYMBOL and carrying the arguments across, which is exact for every
  * other row in its table: `List<T>` and `Buffer[T]` both take one. `OptionalInt` takes NONE and
  * `Option` takes one, so the head swap alone emits `scala.Option` un-applied — a type error at
  * every occurrence, for a row whose entire purpose is that Scala.js ships no `OptionalInt` to link
  * against.
  *
  * An alias is that same type with the argument already supplied. Nothing is wrapped and nothing is
  * copied: a value of this type IS an `Option[Int]` at every slot that wants one, which is what
  * makes this a translation rather than a shim. One file per FQN,
  * because `RuntimeArtifact.vendored` indexes the published module by FILE NAME.
  *
  * What does NOT come across is the MEMBER NAMES — `getAsInt`, `isPresent`, `orElse`, `ifPresent` —
  * and those are the collections phase's `Kind.Opt` arms.
  */
type JavaOptionalInt = Option[Int]
