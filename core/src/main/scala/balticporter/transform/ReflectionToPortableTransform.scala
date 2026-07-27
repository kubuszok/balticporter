package balticporter.transform

import balticporter.tir.*
import balticporter.tir.TypeRepr.NoType

/** Re-points libGDX's `utils.reflect` wrapper calls at the `java.lang.Class` members they merely
  * forward to — the portable subset.
  *
  * libGDX routes everything through `ClassReflection` so GWT/Android backends can supply their own
  * implementation. sge drops that wrapper: it targets Scala Native and Scala.js, where runtime
  * reflection does not exist. But most of what the corpus actually calls is not reflection at all —
  * `getSimpleName`, `isInstance`, `isAssignableFrom`, `isArray` and friends are plain `Class`
  * members that Scala.js and Native DO provide. Only the members that read declared fields,
  * methods and constructors, or resolve a class by name, are genuinely JVM-only.
  *
  * So this phase rewrites `ClassReflection.isInstance(c, x)` → `c.isInstance(x)`, eliminating the
  * wrapper dependency at those sites without changing behaviour or any signature. What remains
  * referencing `ClassReflection` afterwards is exactly the irreducible set that needs a real
  * per-library adjustment (a factory table instead of `forName`, a constructor function instead of
  * `getDeclaredConstructor`) — which is the point: this phase's job is to make that residue small
  * and explicit rather than to hide it.
  *
  * Mints proper `java.lang.Class#…` symbols rather than re-using the wrapper's, so the xref and
  * [[PortabilityCheck]] see the call for what it now is.
  */
final class ReflectionToPortableTransform extends Phase:
  def name: String = "reflection-to-portable"

  private val wrapper = "com.badlogic.gdx.utils.reflect.ClassReflection"

  /** `ClassReflection` statics that are plain `java.lang.Class` members on every platform.
    * Each takes the class as its FIRST argument, which becomes the receiver. */
  private val portable = Set(
    "getSimpleName", "getName", "isInstance", "isAssignableFrom",
    "isArray", "isEnum", "isInterface", "isPrimitive", "isAnnotation", "getComponentType",
  )

  private var mapping: Map[SymId, SymId] = Map.empty

  override def run(program: Program): Program =
    val targets = program.symbols.all.toList.filter { s =>
      portable(s.name) && program.symbolOf(s.owner).exists(_.fullName == wrapper)
    }
    if targets.isEmpty then program
    else
      var table = program.symbols
      var next  = program.symbols.all.map(_.id.raw).max + 1

      // the `java.lang.Class` owner — reuse the interned symbol if the program already refers to
      // it, otherwise mint it.
      val classOwner = program.symbols.all.find(_.fullName == "java.lang.Class").map(_.id).getOrElse {
        val id = SymId(next); next += 1
        table = table.updated(Symbol(id, "Class", "java.lang.Class", Flags(), SymId.None, NoType))
        id
      }

      mapping = targets.map { t =>
        val id = SymId(next); next += 1
        table = table.updated(Symbol(id, t.name, s"java.lang.Class#${t.name}", Flags(), classOwner, NoType))
        t.id -> id
      }.toMap

      given Program = program
      val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
      new Program(units, table, program.xref) // xref rebuilt by the Pipeline

  /** `Wrapper.m(c, rest…)` → `c.m(rest…)`. */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    mapping.get(t.method) match
      case Some(target) if t.args.nonEmpty =>
        Tree.Apply(Tree.Select(t.args.head, target, NoType, t.origin), t.args.tail, target, t.tpe, t.origin)
      case _ => t
