package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.RastNode

/** A method added to a DEFNODE class via DEFMETHOD after definition. */
final case class DefmethodEntry(
  className:  String,
  methodName: String,
  params:     List[String],
  bodyNode:   RastNode
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
