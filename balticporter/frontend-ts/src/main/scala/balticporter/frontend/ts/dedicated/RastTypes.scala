package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.RastNode

/** One function body to translate: a method added to a DEFNODE class via DEFMETHOD after definition, or any function a file declares. `outerScope` is every name the functions lexically enclosing it
  * bind (`RastFunctions.Function.outer`): such a name shadows anything at module level, and only the class a reference turned the enclosing function into can hold it.
  */
final case class DefmethodEntry(
  className:  String,
  methodName: String,
  params:     List[String],
  bodyNode:   RastNode,
  outerScope: Set[String] = Set.empty
)

/** A class extracted from a single `DEFNODE(type, props, ctor, methods, base)` call. */
final case class DefnodeClass(
  varName:   String,
  typeName:  String,
  selfProps: List[String],
  base:      Option[String],
  methods:   List[String]
):
  var isAbstract: Boolean = false

/** A standalone function from a source file. */
final case class FreeFunction(
  name:     String,
  params:   List[String],
  bodyNode: RastNode
)
