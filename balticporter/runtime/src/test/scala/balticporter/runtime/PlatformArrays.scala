package balticporter.runtime

/** Which facts about a REFERENCE ARRAY this platform can be asked at all. */
object PlatformArrays:

  /** `true` where a reference array carries its component type at run time. */
  val reifiesComponentType: Boolean =
    (new Array[String](0)).getClass.getComponentType == classOf[String]
