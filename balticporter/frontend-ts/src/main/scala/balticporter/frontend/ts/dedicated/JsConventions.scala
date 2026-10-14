package balticporter.frontend.ts.dedicated

/** How one library spells its own classes and helpers in JS, as the body translator must read them. The default reads plain JS: no name is special.
  *
  * @param classPrefix
  *   a JS class-variable prefix and the Scala prefix it becomes (`Foo_Bar` read as `FooBar`)
  * @param rootClass
  *   the Scala class `this` is in a method whose own class the reference does not declare
  * @param typeTag
  *   a field naming an object's class and the Scala member it becomes; `x.<tag> === "Y"` tests the class `<prefix>Y`
  * @param nodeFactories
  *   functions called as `f(Class, origin, { field: value })` that build `Class` and assign its fields
  * @param methodRewrites
  *   a method the port moved into a helper, rendered from a template where `$receiver` is the receiver
  */
final case class JsConventions(
  classPrefix:    Option[(String, String)] = None,
  rootClass:      Option[String] = None,
  typeTag:        Option[(String, String)] = None,
  nodeFactories:  Set[String] = Set.empty,
  methodRewrites: Map[String, String] = Map.empty
):

  /** True for a JS name spelled as one of the library's own classes. */
  def isClassName(js: String): Boolean = classPrefix.exists((js0, _) => js.startsWith(js0))

  /** The Scala class a JS class name spells; any other name unchanged. */
  def scalaClassName(js: String): String = classPrefix match
    case Some((jsPrefix, scalaPrefix)) if js.startsWith(jsPrefix) => scalaPrefix + js.drop(jsPrefix.length)
    case _                                                     => js

  /** The JS field naming an object's class, when the library has one. */
  def tagField: Option[String] = typeTag.map(_._1)

object JsConventions:
  val plain: JsConventions = JsConventions()
