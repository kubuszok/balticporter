package sge.gltf.data.extensions

/** Extension-object instantiation for a port that has no reflection. */
object GLTFExtensionFactories {

  private val factories = collection.mutable.Map.empty[Class[?], () => AnyRef]

  /** Register the cross-platform way to build an extension object. Open so a downstream port that
    * adds a vendor extension can supply its own, exactly as libGDX core's injected
    * `AssetTypeRegistry` is. */
  def register[T <: AnyRef](extensionType: Class[T], factory: () => T): Unit =
    factories.update(extensionType, factory.asInstanceOf[() => AnyRef])

  register(classOf[KHRMaterialsEmissiveStrength], () => new KHRMaterialsEmissiveStrength)
  register(classOf[KHRMaterialsIOR], () => new KHRMaterialsIOR)
  register(classOf[KHRMaterialsIridescence], () => new KHRMaterialsIridescence)
  register(classOf[KHRMaterialsSpecular], () => new KHRMaterialsSpecular)
  register(classOf[KHRMaterialsTransmission], () => new KHRMaterialsTransmission)
  register(classOf[KHRMaterialsUnlit], () => new KHRMaterialsUnlit)
  register(classOf[KHRMaterialsVolume], () => new KHRMaterialsVolume)

  /** The registered factory, or the `GdxRuntimeException` java raised — never a silent `null`. */
  def create[T <: AnyRef](extensionType: Class[T]): T =
    factories.get(extensionType) match {
      case Some(f) => f().asInstanceOf[T]
      case None =>
        throw new sge.utils.GdxRuntimeException(
          "no factory registered for glTF material extension " + extensionType.getName +
            " — register one with GLTFExtensionFactories.register (this port has no reflection)")
    }
}
