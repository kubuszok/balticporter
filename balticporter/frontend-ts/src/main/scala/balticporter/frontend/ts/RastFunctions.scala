package balticporter.frontend.ts

/** The function bodies one exported TS/JS file declares: function declarations, class methods and `const f = (…) => …` values, each with the class declaring it, so a translator can read `this` as
  * that class.
  */
object RastFunctions:

  /** The class a method's `this` is, for a body no class declares. */
  val NoClass = "_free_"

  /** One function body: `owner` is the declaring class's name, or [[NoClass]]. A braceless arrow's expression is wrapped as `{ return e }`. */
  final case class Function(owner: String, name: String, params: List[String], body: RastNode)

  /** Every function body in `file`, in source order. */
  def of(file: RastFile): List[Function] =
    val out = List.newBuilder[Function]

    def paramsOf(fn: RastNode): List[String] =
      fn.children.filter(_.kind == "Parameter").flatMap(_.children.find(_.kind == "Identifier").flatMap(_.text))

    def walk(node: RastNode, cls: String): Unit =
      node.kind match
        case "FunctionDeclaration" | "MethodDeclaration" =>
          val name  = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
          val owner = if node.kind == "MethodDeclaration" then cls else NoClass
          if name.nonEmpty then node.children.find(_.kind == "Block").foreach(body => out += Function(owner, name, paramsOf(node), body))
        case "VariableStatement" =>
          for
            vdl <- node.children.find(_.kind == "VariableDeclarationList")
            vd <- vdl.children.filter(_.kind == "VariableDeclaration")
            fn <- vd.children.find(c => c.kind == "ArrowFunction" || c.kind == "FunctionExpression")
          do
            val name = vd.children.headOption.flatMap(_.text).getOrElse("")
            // a braceless arrow's body is its LAST child; a return-type annotation may precede it
            val body = fn.children.find(_.kind == "Block").orElse {
              fn.children.lastOption.filter(_.kind != "Parameter").map(e => RastNode("Block", 0, (0, 0), children = List(RastNode("ReturnStatement", 0, (0, 0), children = List(e)))))
            }
            body.foreach(b => out += Function(NoClass, name, paramsOf(fn), b))
        case _ => ()
      val inner = if node.kind == "ClassDeclaration" then node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse(cls) else cls
      node.children.foreach(walk(_, inner))

    file.nodes.foreach(walk(_, NoClass))
    out.result()
