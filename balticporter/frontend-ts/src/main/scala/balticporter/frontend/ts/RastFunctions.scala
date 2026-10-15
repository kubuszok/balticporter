package balticporter.frontend.ts

/** The function bodies one exported TS/JS file declares: function declarations, class methods and `const f = (…) => …` values, each with the class declaring it, so a translator can read `this` as
  * that class.
  */
object RastFunctions:

  /** The class a method's `this` is, for a body no class declares. */
  val NoClass = "_free_"

  /** One function body: `owner` is the declaring class's name, or [[NoClass]]. A braceless arrow's expression is wrapped as `{ return e }`. `outer` is every name the functions lexically enclosing
    * this one bind (their parameters, variables and nested functions), empty for a function at module or class level.
    */
  final case class Function(owner: String, name: String, params: List[String], body: RastNode, outer: Set[String] = Set.empty)

  private val functionKinds = Set(
    "FunctionDeclaration",
    "MethodDeclaration",
    "ArrowFunction",
    "FunctionExpression",
    "Constructor",
    "GetAccessor",
    "SetAccessor"
  )

  private def paramsOf(fn: RastNode): List[String] =
    fn.children.filter(_.kind == "Parameter").flatMap(_.children.find(_.kind == "Identifier").flatMap(_.text))

  /** The names a function's own scope binds: its parameters, and every variable and nested function declared in its body outside any nested function. */
  def bindingsOf(fn: RastNode): Set[String] =
    val out = Set.newBuilder[String]
    out ++= paramsOf(fn)
    def names(pattern: RastNode): Unit = pattern.kind match
      case "Identifier"                                   => pattern.text.foreach(out += _)
      case "ObjectBindingPattern" | "ArrayBindingPattern" => pattern.children.foreach(names)
      // a property name and a bound name are both identifiers here: over-approximated, a name read as bound only sends its lookup to the enclosing scope
      case "BindingElement" => pattern.children.filter(c => c.kind == "Identifier" || c.kind.endsWith("BindingPattern")).foreach(names)
      case _                => ()
    def walk(n: RastNode): Unit =
      n.kind match
        case "VariableDeclaration"                      => n.children.headOption.foreach(names)
        case "FunctionDeclaration" | "ClassDeclaration" => n.children.find(_.kind == "Identifier").flatMap(_.text).foreach(out += _)
        case _                                          => ()
      if !functionKinds(n.kind) && n.kind != "ClassDeclaration" then n.children.foreach(walk)
    fn.children.find(_.kind == "Block").foreach(_.children.foreach(walk))
    out.result()

  /** Every function body in `file`, in source order. */
  def of(file: RastFile): List[Function] =
    val out = List.newBuilder[Function]

    def walk(node: RastNode, cls: String, outer: Set[String]): Unit =
      node.kind match
        case "FunctionDeclaration" | "MethodDeclaration" =>
          val name  = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
          val owner = if node.kind == "MethodDeclaration" then cls else NoClass
          if name.nonEmpty then node.children.find(_.kind == "Block").foreach(body => out += Function(owner, name, paramsOf(node), body, outer))
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
            body.foreach(b => out += Function(NoClass, name, paramsOf(fn), b, outer))
        case _ => ()
      val inner = if node.kind == "ClassDeclaration" then node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse(cls) else cls
      // a function's bindings are visible to everything nested in it, a class it declares included
      val innerOuter = if functionKinds(node.kind) then outer ++ bindingsOf(node) else outer
      node.children.foreach(walk(_, inner, innerOuter))

    file.nodes.foreach(walk(_, NoClass, Set.empty))
    out.result()
