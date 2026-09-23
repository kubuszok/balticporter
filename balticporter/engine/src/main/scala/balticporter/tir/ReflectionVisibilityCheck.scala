package balticporter.tir

/** Counts reflective instantiation sites where scalac emits a public constructor for a private nested class while javac emits a private one. Java's reflective access throws; Scala's succeeds
  * silently. Catalog `JS-C54`, counted rather than resolved — no faithful translation short of emitting a private constructor with a public factory. Three sub-lanes: `private-target` (class literal
  * resolvable to a private nested type), `unknown-target` (dynamic `Class<?>`, target not statically known), and all sites combined as the denominator.
  */
object ReflectionVisibilityCheck:

  val Name = "reflection-visibility"

  enum Issue:
    /** the target type is statically known and its constructor is private in java — the call succeeds in scala where it would fail in java. */
    case PrivateTarget

    /** the target is a dynamic `Class<?>` variable; whether the constructor is private cannot be determined statically. */
    case UnknownTarget

  object Issue:
    def classification(i: Issue): String = i match
      case PrivateTarget =>
        "engine (true of every Java program), COUNTED and deliberately NOT resolved (catalog `JS-C54`, JLS 8.8.9): " +
          "javac emits a private constructor for a private nested class, so reflective " +
          "instantiation via Class.getConstructor or Class.newInstance throws " +
          "NoSuchMethodException or IllegalAccessException. Scalac enforces nested-class privacy " +
          "at compile time only and emits the constructor public, so the same call succeeds — " +
          "a silent behavioural difference. No faithful translation exists short of emitting a " +
          "private constructor with a public factory, which is refused."
      case UnknownTarget =>
        "engine (true of every Java program), COUNTED (catalog `JS-C54`): the target of this " +
          "reflective instantiation is a dynamic Class<?> variable whose concrete type cannot be " +
          "determined statically. If the runtime type is a private nested class, the same " +
          "java-vs-scala constructor visibility difference applies."

  /** Instantiation sites: `Class.newInstance()`, `Constructor.newInstance(...)`, and `MethodHandles.Lookup.findConstructor(...)`. `getConstructor`/`getDeclaredConstructor` alone are NOT sites — they
    * return a `Constructor`, the instantiation happens at `Constructor.newInstance`.
    */
  private val InstantiationSites: Map[String, Set[String]] = Map(
    "newInstance" -> Set("java.lang.Class", "java.lang.reflect.Constructor"),
    "findConstructor" -> Set("java.lang.invoke.MethodHandles.Lookup")
  )

  final case class Finding(issue: Issue, owner: String, targetType: String, origin: Origin):
    def detail: String = issue match
      case Issue.PrivateTarget =>
        s"reflective instantiation of private nested type `$targetType` — java's constructor is " +
          "private (JLS 8.8.9), scala's is public; this call succeeds in the port where it would " +
          "throw in java"
      case Issue.UnknownTarget =>
        "reflective instantiation with a dynamic Class<?> target — if the runtime type is a " +
          "private nested class, java's constructor is private and scala's is public"

    def render: String = s"$issue $owner: target=$targetType  (${origin.javaPath}:${origin.line})"

    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** the decision recorded for a finding, for `decisions.tsv` and the porter note. */
  def decision(f: Finding, subject: SymId, subjectFqn: String): Decision =
    Decision(
      kind = Decision.Kind.CountedReflectionRisk,
      subject = subject,
      subjectFqn = subjectFqn,
      detail = Map("target" -> f.targetType, "issue" -> f.issue.toString),
      reason = Reason.Universal("reflection-visibility (JS-C54, JLS 8.8.9)"),
      origin = f.origin
    )

  /** Walk all units with `StandardTraversal`, identify reflective instantiation calls, classify each. The enclosing member is tracked through a product-reflection walk over each class def (the same
    * units `StandardTraversal.allClassDefs` would see).
    */
  def check(program: Program, units: List[Tree.ClassDef]): List[Finding] =
    given Program = program
    val out       = collection.mutable.ListBuffer.empty[Finding]
    units.foreach(u => walkClassDef(u, out))
    out.toList.sortBy(f => (f.issue.toString, f.origin.javaPath, f.origin.line))

  /** Recursive walk tracking enclosing member. */
  private def walkClassDef(cd: Tree.ClassDef, out: collection.mutable.ListBuffer[Finding])(using p: Program): Unit =
    val classFqn = p.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
    walkTree(cd, classFqn, out)

  private def walkTree(t: Any, enclosing: String, out: collection.mutable.ListBuffer[Finding])(using p: Program): Unit = t match
    case c: Tree.ClassDef =>
      val fqn = p.symbolOf(c.symbol).map(_.fullName).getOrElse(enclosing)
      c.body.foreach(walkTree(_, fqn, out))
      c.enumCases.foreach(walkTree(_, fqn, out))
    case d: Tree.DefDef =>
      val fqn = p.symbolOf(d.symbol).map(_.fullName).getOrElse(enclosing)
      d.rhs.foreach(walkTree(_, fqn, out))
      d.paramss.foreach(_.foreach(walkTree(_, fqn, out)))
    case v: Tree.ValDef =>
      val fqn = p.symbolOf(v.symbol).map(_.fullName).getOrElse(enclosing)
      v.rhs.foreach(walkTree(_, fqn, out))
    case a: Tree.Apply =>
      classifyApply(a, enclosing, out)
      a.fun match
        case s: Tree.Select => walkTree(s.qual, enclosing, out)
        case _ => walkTree(a.fun, enclosing, out)
      a.args.foreach(walkTree(_, enclosing, out))
    case x: Tree.Block =>
      x.stats.foreach(walkTree(_, enclosing, out))
      walkTree(x.expr, enclosing, out)
    case x: Tree.If =>
      walkTree(x.cond, enclosing, out)
      walkTree(x.thenp, enclosing, out)
      walkTree(x.elsep, enclosing, out)
    case x: Tree.Try =>
      walkTree(x.body, enclosing, out)
      x.catches.foreach(c => walkTree(c.body, enclosing, out))
      x.finalizer.foreach(walkTree(_, enclosing, out))
    case x: Tree.Return =>
      x.expr.foreach(walkTree(_, enclosing, out))
    case x: Tree.Select =>
      walkTree(x.qual, enclosing, out)
    case x: Tree.Assign =>
      walkTree(x.lhs, enclosing, out)
      walkTree(x.rhs, enclosing, out)
    case x: Tree.TypeApply =>
      walkTree(x.fun, enclosing, out)
    case x: Tree.Lambda =>
      walkTree(x.body, enclosing, out)
    case x: Tree.Typed =>
      walkTree(x.expr, enclosing, out)
    case x: Tree.New =>
      x.anon.foreach(a => a.body.foreach(walkTree(_, enclosing, out)))
    case xs: Iterable[?] =>
      xs.foreach(walkTree(_, enclosing, out))
    case Some(x) =>
      walkTree(x, enclosing, out)
    case p2: Product if p2.isInstanceOf[Term] || p2.isInstanceOf[Statement] =>
      p2.productIterator.foreach(walkTree(_, enclosing, out))
    case _ => ()

  private def classifyApply(a: Tree.Apply, enclosing: String, out: collection.mutable.ListBuffer[Finding])(using p: Program): Unit =
    val methodSym  = p.symbolOf(a.method)
    val methodName = methodSym.map(_.name).getOrElse("")
    InstantiationSites.get(methodName) match
      case Some(owners) =>
        val ownerFqn = methodSym.flatMap(s => p.symbolOf(s.owner)).map(_.fullName).getOrElse("")
        if owners.exists(o => ownerFqn == o || ownerFqn.startsWith(o)) then
          val targetOpt = extractTargetType(a, ownerFqn)
          targetOpt match
            case Some(targetFqn) =>
              p.symbols.all.find(_.fullName == targetFqn) match
                case Some(sym) if sym.flags.isPrivate =>
                  out += Finding(Issue.PrivateTarget, enclosing, targetFqn, a.origin)
                case Some(_) =>
                  () // public or non-private — no visibility divergence
                case None =>
                  out += Finding(Issue.UnknownTarget, enclosing, targetFqn, a.origin)
            case None =>
              out += Finding(Issue.UnknownTarget, enclosing, "?", a.origin)
      case None => ()

  /** Extract the target type from a reflective instantiation call. */
  private def extractTargetType(a: Tree.Apply, calleeOwner: String)(using p: Program): Option[String] =
    if calleeOwner.startsWith("java.lang.Class") then
      // Class.newInstance() — read the receiver for a class literal
      a.fun match
        case Tree.Select(qual, _, _, _) => extractClassLiteral(qual)
        case _                          => None
    else if calleeOwner.startsWith("java.lang.reflect.Constructor") then
      // Constructor.newInstance(...) — walk up to the getConstructor/getDeclaredConstructor call
      a.fun match
        case Tree.Select(qual, _, _, _) => extractClassLiteralFromConstructorChain(qual)
        case _                          => None
    else if calleeOwner.startsWith("java.lang.invoke.MethodHandles") then
      // findConstructor(cls, methodType) — first argument is the target class
      a.args.headOption.flatMap(extractClassLiteral)
    else None

  /** Walk a term to find a `getConstructor`/`getDeclaredConstructor` call and read the class literal from ITS receiver. */
  private def extractClassLiteralFromConstructorChain(t: Term)(using p: Program): Option[String] =
    t match
      case Tree.Apply(fun, _, method, _, _) =>
        val name = p.symbolOf(method).map(_.name).getOrElse("")
        if name == "getConstructor" || name == "getDeclaredConstructor" then
          fun match
            case Tree.Select(qual, _, _, _) => extractClassLiteral(qual)
            case _                          => None
        else None
      case _ => None

  /** Walk a term to find a class literal (`X.class` in java, rendered as `Tree.Literal(Constant.ClassOfC(tpe), ...)` in the TIR). */
  private def extractClassLiteral(t: Term)(using p: Program): Option[String] =
    t match
      case Tree.Literal(Constant.ClassOfC(tpe), _, _) =>
        headFqn(tpe)
      case Tree.Select(qual, _, _, _) =>
        extractClassLiteral(qual)
      case _ => None

  private def headFqn(t: TypeRepr)(using p: Program): Option[String] = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).map(_.fullName)
    case TypeRepr.AppliedType(tc, _) => headFqn(tc)
    case _                           => None

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue)
        .toList
        .sortBy((_, v) => -v.size)
        .map { (issue, vs) =>
          val head  = s"  ${vs.size} x $issue\n  ${Issue.classification(issue)}"
          val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
          (head :: sites).mkString("\n")
        }
        .mkString("\n")
