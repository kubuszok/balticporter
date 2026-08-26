package sge.gltf.data.extensions

/** Extension-object instantiation for a port that has no reflection.
  *
  * `GLTFMaterialExporter.ext(GLTFMaterial, Class<T>, String)` builds a glTF material extension
  * object on demand with `ClassReflection.newInstance(type)`, and wraps the `ReflectionException`
  * in a `GdxRuntimeException`. The libGDX port drops `ClassReflection` outright — runtime
  * reflection is the one thing Scala.js and Scala Native do not have — so the body needs a
  * replacement, which `MethodBodyTransform` supplies from `GltfPolicy`.
  *
  * This is the third library in the corpus to need exactly this shape (libGDX's own injected
  * `Pools` takes a factory; Ashley's `ComponentFactories` is the same registry one repository out),
  * and that looked like the CLAUDE.md §2 signal that "reflective no-arg instantiation becomes a
  * registry" was moving from a per-library rule towards a shared one. **It is not, and the reading
  * that settled it is `ENGINE-LIMITS.md` P10.** The abstraction that covers all three has to be
  * value-generic — `Pools` keys shared pool VALUES, not factories — at which point it is a
  * `Class`-keyed map, which the runtime module does not admit (it takes semantics the target LACKS,
  * and a map is not one); narrowed to the two that really are instance registries it is deliverable
  * only by a BASE port, which this module and Ashley are not. So it stays here, and P10 states the
  * two conditions that would re-open it.
  *
  * Note also what P10 corrects about THIS file: the registry below is OPEN — `register` is public —
  * so "open with a fallback vs closed without" was never the axis between it and Ashley's. The one
  * axis is what each does at a MISS, which is the paragraph below.
  *
  * ==The seven types this actually has to build==
  * `ext` is reached from seven one-line wrappers in `GLTFMaterialExporter`, each with a concrete
  * type, so the JVM fallback below is a convenience and the table is the real answer — and the
  * table is what makes the port link on Scala.js and Scala Native, where the fallback cannot exist.
  * Each is a plain data holder with an implicit no-arg constructor.
  *
  * ==Faithfulness of the failure path, which is not the same as Ashley's==
  * libGDX's `ClassReflection.newInstance` wraps `InstantiationException` and
  * `IllegalAccessException` into `ReflectionException`; Ashley turned that into `null`, and
  * gdx-gltf turns it into a `GdxRuntimeException` (`GLTFMaterialExporter.java:219-223`). The
  * distinction matters and the exception is kept: returning `null` here would put a null extension
  * into `m.extensions.set(ext, e)` and the exporter would emit `"KHR_materials_ior": null` instead
  * of failing — a defect that compiles, passes review and writes a wrong document (CLAUDE.md §4.4).
  *
  * ==NO reflective fallback, deliberately — and this is where it differs from Ashley's==
  * `ComponentFactories` keeps `getConstructor().newInstance()` for an unregistered component,
  * because Ashley's components are the CONSUMER's types and the registry cannot be complete. This
  * registry is over gdx-gltf's OWN seven extension classes and is complete by construction, so a
  * fallback would buy nothing and cost the port its portability: `PortabilityCheck` counted it
  * immediately (`injected portability 4 -> 0` when it went, `getConstructor`/`newInstance` plus a
  * `ConcurrentHashMap` that Scala.js does not have either). An unregistered type raises the same
  * `GdxRuntimeException` java raised, naming the registry — which is the actionable message, where
  * a reflective retry on Scala.js or Native would be a link error.
  */
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
