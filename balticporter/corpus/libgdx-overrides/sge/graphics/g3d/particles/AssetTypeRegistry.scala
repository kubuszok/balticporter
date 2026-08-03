package sge.graphics.g3d.particles

import sge.utils.reflect.ReflectionException

import scala.collection.mutable

/** INJECTED SCALA (Substitutions.inject) — the portable replacement for the ONE runtime class
  * lookup libGDX core performed: `ResourceData.AssetData` persists an asset's type as its class
  * NAME and, on read-back, turned that string into a `Class` again.
  *
  * A string→class lookup needs a runtime class registry, which Scala.js and Scala Native do not
  * have (and could not have: the linker keeps only what the program statically mentions). So the
  * candidate set is stated HERE instead, as `classOf[…]` literals every backend resolves at COMPILE
  * time. `ClassTableTransform` re-points the call site at this table, and the JVM-only wrapper
  * method is gone rather than merely unused.
  *
  * The table is seeded with every type libGDX core itself stores in a `ResourceData` — the four
  * `SaveData.saveAsset` call sites (`ModelInfluencer`, `MeshSpawnShapeValue`,
  * `PointSpriteParticleBatch`/`BillboardParticleBatch`, `ParticleControllerInfluencer`,
  * `RegionInfluencer`) — and is OPEN: a downstream port registers its own asset types before
  * deserializing effects that reference them.
  *
  * Keys come from `Class#getName` (portable on all three backends) — the same expression
  * `AssetData.write` uses to produce them, so a name written by this port always reads back.
  */
object AssetTypeRegistry:

  private val byName: mutable.HashMap[String, Class[?]] = mutable.HashMap.empty

  /** Make these types nameable in persisted `ResourceData`. Idempotent. */
  def register(types: Class[?]*): Unit = types.foreach(c => byName.update(c.getName, c))

  /** Every type currently nameable — the exact set `classFor` can resolve. */
  def registered: Iterable[Class[?]] = byName.values

  /** The registered type with this name.
    * @throws ReflectionException if nothing was registered under it — same failure the reflective
    *         lookup raised for an unresolvable name, so the caller's existing handler still applies.
    */
  def classFor(name: String): Class[?] =
    byName.getOrElse(name, throw new ReflectionException("Asset type not registered: " + name))

  register(
    classOf[sge.graphics.Texture],
    classOf[sge.graphics.g2d.TextureAtlas],
    classOf[sge.graphics.g3d.Model],
    classOf[sge.graphics.g3d.particles.ParticleEffect],
  )
