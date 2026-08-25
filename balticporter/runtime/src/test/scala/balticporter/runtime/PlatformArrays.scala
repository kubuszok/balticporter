package balticporter.runtime

/** Which facts about a REFERENCE ARRAY this platform can be asked at all.
  *
  * `Collection.toArray(T[])` is defined by java in terms of the argument's RUNTIME component type:
  * when the caller's array is too short, the result is a new array *of the same component type*,
  * so that the caller can store into it through a `T[]`-typed reference without an
  * `ArrayStoreException`. The shims reproduce that (`java.util.Arrays.copyOf` / `Array.copyOf`,
  * never a fresh `Array[A]`) and `JavaCollectionSpec`/`JavaCollectionsSpec` pin it.
  *
  * Scala Native does not reify it: EVERY reference array is one `ObjectArray` at run time, so
  * `new Array[String](0).getClass.getComponentType` is already `java.lang.Object` before any shim
  * is involved. The question java's contract is about therefore has no answer on that platform —
  * and it is not a divergence in the shim, which does exactly what it does on the other two rows.
  * The JVM and Scala.js both reify, and both assert the full contract.
  *
  * This is a PROBE and not a platform source directory on purpose: what the assertion needs to
  * know is whether the runtime distinguishes the two array classes, and that is a question the
  * runtime answers directly. A `src/test/scalanative` copy would state the same fact as a list of
  * platforms, which is the kind of list that goes stale the day a backend gains the feature.
  */
object PlatformArrays:

  /** `true` where a reference array carries its component type at run time. */
  val reifiesComponentType: Boolean =
    (new Array[String](0)).getClass.getComponentType == classOf[String]
