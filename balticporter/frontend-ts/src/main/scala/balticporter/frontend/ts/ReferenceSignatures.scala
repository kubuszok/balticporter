package balticporter.frontend.ts

import scala.collection.mutable

/** Reads method signatures from hand-written Scala reference files to recover type information for source languages that lack static types (JavaScript, or Dart at `dynamic` sites).
  *
  * The reader is generic: it parses any Scala object's `def` lines into a signature lookup table. A consumer's builder supplies its own library-specific name mappings and hardcoded tables.
  */
object ReferenceSignatures:

  // a capitalised type name not qualified by a prefix
  private[ts] val aliasName = """(?<![\w.$])([A-Z][A-Za-z0-9_]*)(?![\w$])""".r

  /** A method parameter with its name and type annotation. */
  final case class ParamSig(name: String, tpe: String, hasDefault: Boolean = false)

  /** A complete method signature extracted from a reference file. */
  final case class MethodSig(
    methodName: String,
    params:     List[ParamSig],
    returnType: String
  )

  /** Type oracle: maps `(objectName, methodName)` to the reference signature.
    *
    * Lookups try exact match first, then case-insensitive on the method name.
    */
  final case class TypeOracle(
    methods: Map[(String, String), MethodSig]
  ):
    private lazy val byLower: Map[(String, String), MethodSig] =
      methods.map { case ((obj, name), sig) => ((obj, name.toLowerCase), sig) }

    private def lookup(objectName: String, methodName: String): Option[MethodSig] =
      methods.get((objectName, methodName)).orElse(byLower.get((objectName, methodName.toLowerCase)))

    def paramType(objectName: String, methodName: String, paramName: String): String =
      lookup(objectName, methodName) match
        case Some(sig) =>
          val lowerParam = paramName.toLowerCase
          sig.params.find(_.name == paramName).orElse(sig.params.find(_.name.toLowerCase == lowerParam)).map(_.tpe).getOrElse("Any")
        case None => "Any"

    def returnType(objectName: String, methodName: String): String =
      lookup(objectName, methodName).map(_.returnType).getOrElse("Any")

    def get(objectName: String, methodName: String): Option[MethodSig] =
      lookup(objectName, methodName)

  object TypeOracle:
    val empty: TypeOracle = TypeOracle(Map.empty)

    def fromEntries(entries: List[(String, MethodSig)]): TypeOracle =
      TypeOracle(entries.map { case (obj, sig) => (obj, sig.methodName) -> sig }.toMap)

  /** A member of an object, as the reference tree declares it: `owner` is the declaring object's path (`Outer.Inner`), `file` the name the caller gave the file declaring it. */
  final case class MemberRef(
    owner:     String,
    member:    String,
    file:      String,
    isPrivate: Boolean,
    kind:      ReferenceDeclarations.Kind = ReferenceDeclarations.Kind.Def,
    tpe:       Option[String] = None
  ):
    def qualified: String = s"$owner.$member"

    /** A def, val or var: what a bare identifier or a call in a translated body can name. */
    def isTerm: Boolean =
      import ReferenceDeclarations.Kind.*
      kind match
        case Def | AbstractDef | Val | Var => true
        case _                             => false

    /** True when code emitted inside `context` (an object path) can name this member: a private member only from its owner or from a template nested in it. */
    def accessibleFrom(context: Option[String]): Boolean =
      !isPrivate || context.exists(c => c == owner || c.startsWith(owner + "."))

  /** The members every object of the reference tree declares, keyed by the DECLARING object. Used to qualify unresolved callee identifiers: `makeSpan(...)` becomes `BuildCommon.makeSpan(...)`.
    * `objects` also lists objects that declare nothing; `unparsed` names the files the reader could not parse, which contribute nothing.
    */
  final case class CalleeIndex(
    members:  List[MemberRef],
    objects:  Set[String] = Set.empty,
    unparsed: List[String] = Nil
  ):
    private lazy val byMember: Map[String, List[MemberRef]] = members.filter(_.isTerm).groupBy(_.member)
    private lazy val byOwner:  Map[String, List[MemberRef]] = members.groupBy(_.owner)

    /** The declaring object of a simple name: Right on a unique owner, Left(reason) on ambiguity or absence. */
    def resolveMember(simpleName: String): Either[String, MemberRef] =
      byMember.getOrElse(simpleName, Nil).distinctBy(_.owner) match
        case Nil        => Left("callee-not-found")
        case one :: Nil => Right(one)
        case _          => Left("callee-ambiguous")

    /** [[resolveMember]] as the qualified text. */
    def resolve(simpleName: String): Either[String, String] = resolveMember(simpleName).map(_.qualified)

    /** What `owner` itself declares under `member` — the declaration, never a resolution. */
    def declared(owner: String, member: String): Option[MemberRef] =
      byOwner.getOrElse(owner, Nil).find(_.member == member)

    /** Every object path in this index. */
    lazy val knownObjects: Set[String] = objects ++ members.map(_.owner)

    /** The member names `objectName` declares. */
    def membersOf(objectName: String): Set[String] = byOwner.getOrElse(objectName, Nil).map(_.member).toSet

  object CalleeIndex:
    val empty: CalleeIndex = CalleeIndex(Nil)

    /** An index of public members from `member -> declaring objects`. */
    def apply(byName: Map[String, List[String]]): CalleeIndex =
      CalleeIndex(byName.toList.flatMap((m, owners) => owners.map(o => MemberRef(o, m, o, isPrivate = false))))

    /** The index of the structurally-read `declarations` of the file the caller named `file`: object members only, since a class or trait member is not reachable through its owner's name. */
    def of(file: String, declarations: List[ReferenceDeclarations.Declaration]): CalleeIndex =
      val reachable = declarations.filter(d => d.owner.nonEmpty && d.ownerIsObject)
      val objects   = declarations.collect { case d if d.kind == ReferenceDeclarations.Kind.Object && d.ownerIsObject => if d.owner.isEmpty then d.name else s"${d.owner}.${d.name}" }
      CalleeIndex(reachable.map(d => MemberRef(d.owner, d.name, file, d.isPrivate, d.kind, d.tpe)), objects.toSet)

  /** Index of class/object member names, built from the reference tree. Used to validate property access on typed parameters.
    */
  final case class MemberIndex(
    byType:    Map[String, Set[String]],
    vars:      Map[String, Set[String]] = Map.empty,
    types:     Map[String, Map[String, String]] = Map.empty,
    arity:     Map[String, Int] = Map.empty,
    results:   Map[String, Map[String, String]] = Map.empty,
    parents:   Map[String, List[String]] = Map.empty,
    constants: Map[(String, String), List[String]] = Map.empty,
    aliases:   Map[String, String] = Map.empty
  ):
    /** `t` with every type alias the reference declares replaced by what it names (`DomSpan` reads `Span[HtmlDomNode]`): the alias is the same type, and only its right-hand side declares members. An
      * alias to a union or a function type is replaced only where it is the whole type, since inside another type it would need parentheses.
      */
    def dealias(t: String): String =
      if aliases.isEmpty then t
      else
        var cur     = t
        var rounds  = 0
        var changed = true
        while changed && rounds < 8 do
          val whole = cur.trim
          val next  = ReferenceSignatures.aliasName.replaceAllIn(
            cur,
            m =>
              val body = aliases.get(m.group(1)).filter(b => whole == m.group(1) || !(b.contains('|') || b.contains("=>")))
              scala.util.matching.Regex.quoteReplacement(body.getOrElse(m.matched))
          )
          changed = next != cur
          cur = next
          rounds += 1
        cur

    /** The result type every class declaring a method of this name declares for it, when they all agree and none overloads it; a call of the name returns that whichever class receives it. */
    def uniformResult(method: String): Option[String] =
      val declaring = byType.collect { case (t, ms) if ms.contains(method) => t }.toList
      declaring.map(t => results.get(t).flatMap(_.get(method))).distinct match
        case List(Some(r)) => Some(r)
        case _             => None

    /** The classes declaring `member` as the string constant `value` (`def nodeType = "ordgroup"`). */
    def classesDeclaring(member: String, value: String): List[String] =
      constants.getOrElse((member, value), Nil)

    /** The type itself, then every class and trait it extends, nearest first: a member a parent declares is a member of the type. */
    def ancestry(typeName: String): List[String] =
      val seen = scala.collection.mutable.LinkedHashSet.empty[String]
      var todo = List(dealiasedBase(typeName))
      while todo.nonEmpty do
        val t = todo.head
        todo = todo.tail
        if seen.add(t) then todo = todo ++ parents.getOrElse(t, Nil)
      seen.toList

    // the nearest type of the ancestry whose own declarations answer
    private def nearest[A](typeName: String)(own: String => Option[A]): Option[A] =
      ancestry(typeName).iterator.map(own).collectFirst { case Some(a) => a }

    /** The declared result type of a method the class, or the nearest ancestor declaring the name, declares exactly once under that name (an overloaded name answers nothing). */
    def methodResult(typeName: String, method: String): Option[String] =
      ancestry(typeName).find(t => byType.get(t).exists(_.contains(method))).flatMap(t => results.get(t).flatMap(_.get(method)))

    /** A class name as a runtime type test spells it: one wildcard per type parameter the reference declares. */
    def testable(typeName: String): String =
      arity.get(typeName).filter(_ > 0).fold(typeName)(n => List.fill(n)("?").mkString(s"$typeName[", ", ", "]"))

    /** The declared type of a value-like member (a field, a constructor parameter, a parameterless def), when the reference spells one. */
    def memberType(typeName: String, memberName: String): Option[String] =
      ancestry(typeName).find(t => byType.get(t).exists(_.contains(memberName))).flatMap(t => types.get(t).flatMap(_.get(memberName)))

    /** True when the given type, or a class or trait it extends, declares the given member name. */
    def hasMember(typeName: String, memberName: String): Boolean =
      nearest(typeName)(t => byType.get(t).filter(_.contains(memberName))).isDefined

    /** True when the nearest type of the ancestry declaring the member declares it as a `var`, so a body may store into it. */
    def isMutable(typeName: String, memberName: String): Boolean =
      ancestry(typeName).find(t => byType.get(t).exists(_.contains(memberName))).exists(t => vars.get(t).exists(_.contains(memberName)))

    /** True when the type is known (appears in the index). */
    def knowsType(typeName: String): Boolean = byType.contains(typeName) || byType.contains(dealiasedBase(typeName))

    // the class an alias names, without its type arguments; any other name is itself
    private def dealiasedBase(typeName: String): String =
      if !aliases.contains(typeName) then typeName else dealias(typeName).takeWhile(c => c != '[' && c != ' ')

  object MemberIndex:
    val empty: MemberIndex = MemberIndex(Map.empty)

  /** Constructor parameter schema for a class, parsed from the reference tree. Used to construct typed objects from JS object literals.
    */
  final case class CtorParam(name: String, tpe: String, hasDefault: Boolean, isVar: Boolean = false)

  final case class ConstructorSchema(
    byType:      Map[String, List[CtorParam]],
    caseClasses: Set[String] = Set.empty
  ):
    def get(typeName: String): Option[List[CtorParam]] = byType.get(typeName)

    /** True for a class the reference declares as a `case class`, whose `copy` takes every constructor parameter by name. */
    def isCase(typeName: String): Boolean = caseClasses.contains(typeName)

  object ConstructorSchema:
    val empty: ConstructorSchema = ConstructorSchema(Map.empty)

  /** Index of enum member names by their declared value, parsed from enum definitions in the reference tree. Maps `(EnumTypeName, "stringValue")` to the Scala member name.
    */
  final case class EnumIndex(
    byValue: Map[(String, String), String]
  ):
    def resolve(typeName: String, value: String): Option[String] =
      byValue.get((typeName, value))

  object EnumIndex:
    val empty: EnumIndex = EnumIndex(Map.empty)

  /** Parse the reference Scala files, given as `(file name, source)`, and build the indices. The callee index files each member under the object that DECLARES it, read from the parse. */
  def buildIndices(sources: List[(String, String)]): (CalleeIndex, MemberIndex, ConstructorSchema, EnumIndex) =
    val calleeParts = mutable.ListBuffer.empty[CalleeIndex]
    val unparsed    = mutable.ListBuffer.empty[String]
    val members     = mutable.Map.empty[String, mutable.Set[String]]
    val vars        = mutable.Map.empty[String, mutable.Set[String]]
    val types       = mutable.Map.empty[String, mutable.Map[String, String]]
    val arity       = mutable.Map.empty[String, Int]
    val results     = mutable.Map.empty[String, mutable.Map[String, List[String]]]
    val ctors       = mutable.Map.empty[String, List[CtorParam]]
    val caseClasses = mutable.Set.empty[String]
    val aliases     = mutable.Map.empty[String, Set[String]]
    val enumVals    = mutable.Map.empty[(String, String), String]
    val parents     = mutable.Map.empty[String, List[String]]
    val constants   = mutable.Map.empty[(String, String), List[String]]

    for (objectName, source) <- sources do
      ReferenceDeclarations.read(objectName, source) match
        case Right(decls) =>
          calleeParts += CalleeIndex.of(objectName, decls)
          for d <- decls; body <- d.alias do aliases(d.name) = aliases.getOrElse(d.name, Set.empty) + body
          decls.filter(_.typeParams > 0).foreach(d => arity(d.name) = d.typeParams)
          decls.filter(_.parents.nonEmpty).foreach(d => parents(d.name) = (parents.getOrElse(d.name, Nil) ++ d.parents).distinct)
          // a class or trait that declares nothing of its own is still a known type: its members are its parents'
          decls.filter(d => d.kind == ReferenceDeclarations.Kind.Class || d.kind == ReferenceDeclarations.Kind.Trait).foreach(d => members.getOrElseUpdate(d.name, mutable.Set.empty))
          // A class or trait member is reached through an instance (`this.x`, `p.x`): indexed under the template's simple name.
          for d <- decls if d.owner.nonEmpty && !d.ownerIsObject && d.kind != ReferenceDeclarations.Kind.Constructor do
            members.getOrElseUpdate(d.owner.split('.').last, mutable.Set.empty) += d.name
            if d.kind == ReferenceDeclarations.Kind.Var then vars.getOrElseUpdate(d.owner.split('.').last, mutable.Set.empty) += d.name
            d.constant.foreach(c => constants((d.name, c)) = (constants.getOrElse((d.name, c), Nil) :+ d.owner.split('.').last).distinct)
            d.tpe.foreach(t => types.getOrElseUpdate(d.owner.split('.').last, mutable.Map.empty)(d.name) = t)
            if d.isMethod then
              val byName = results.getOrElseUpdate(d.owner.split('.').last, mutable.Map.empty)
              byName(d.name) = byName.getOrElse(d.name, Nil) :+ d.result.getOrElse("")
        case Left(error) => unparsed += error

      // Collect class constructors by joining multi-line declarations
      val classLines = joinClassDeclarations(source)
      for line <- classLines do
        parseCaseClassLine(line).foreach { case (className, params) =>
          ctors(className) = params
          if raw"\bcase\s+class\s+$className\b".r.findFirstIn(line).isDefined then caseClasses += className
          val memberSet = members.getOrElseUpdate(className, mutable.Set.empty)
          params.foreach(p => memberSet += p.name)
          params.filter(_.isVar).foreach(p => vars.getOrElseUpdate(className, mutable.Set.empty) += p.name)
          params.foreach(p => types.getOrElseUpdate(className, mutable.Map.empty)(p.name) = p.tpe)
        }

      // Collect enum definitions and their members
      parseEnumMembers(source, members, enumVals)

    (
      CalleeIndex(calleeParts.toList.flatMap(_.members), calleeParts.toList.flatMap(_.objects).toSet, unparsed.toList),
      MemberIndex(
        members.map { case (k, v) => k -> v.toSet }.toMap,
        vars.map { case (k, v) => k -> v.toSet }.toMap,
        types.map { case (k, v) => k -> v.toMap }.toMap,
        arity.toMap,
        results.map { case (k, v) => k -> v.collect { case (m, List(r)) if r.nonEmpty => m -> r }.toMap }.toMap,
        parents.toMap,
        constants.toMap,
        // an alias spelled two ways, or named like a class, names nothing certain
        aliases.collect { case (n, bodies) if bodies.size == 1 && !members.contains(n) => n -> bodies.head }.toMap
      ),
      ConstructorSchema(ctors.toMap, caseClasses.toSet),
      EnumIndex(enumVals.toMap)
    )

  /** Parse enum definitions and collect their members with string values. */
  private def parseEnumMembers(
    source:   String,
    members:  mutable.Map[String, mutable.Set[String]],
    enumVals: mutable.Map[(String, String), String]
  ): Unit =
    val enumStart = """^\s*enum\s+(\w+)""".r
    val caseLine  = """^\s+case\s+(\w+)\s+extends\s+\w+\("([^"]*)"\)""".r
    var currentEnum: String = null

    for line <- source.linesIterator do
      enumStart.findFirstMatchIn(line).foreach { m =>
        currentEnum = m.group(1)
        members.getOrElseUpdate(currentEnum, mutable.Set.empty)
      }
      if currentEnum != null then
        caseLine.findFirstMatchIn(line).foreach { m =>
          val memberName = m.group(1)
          val strValue   = m.group(2)
          members.getOrElseUpdate(currentEnum, mutable.Set.empty) += memberName
          enumVals((currentEnum, strValue)) = memberName
        }
        if line.trim == "}" then currentEnum = null

  /** Join multi-line class declarations so constructors with wrapped parameters appear on a single logical line.
    */
  private def joinClassDeclarations(source: String): List[String] =
    val result     = mutable.ListBuffer.empty[String]
    val classStart = """^\s*(?:final\s+)?(?:case\s+)?class\s+""".r
    var accum: StringBuilder = null
    var depth = 0

    for line <- source.linesIterator do
      if accum != null then
        accum.append(" ").append(line.trim)
        for ch <- line do
          ch match
            case '(' => depth += 1
            case ')' => depth -= 1
            case _   => ()
        if depth <= 0 then
          result += accum.toString
          accum = null
          depth = 0
      else if classStart.findFirstIn(line).isDefined then
        var d = 0
        for ch <- line do
          ch match
            case '(' => d += 1
            case ')' => d -= 1
            case _   => ()
        if d <= 0 then result += line
        else
          accum = new StringBuilder(line)
          depth = d
      else result += line

    if accum != null then result += accum.toString
    result.toList

  /** Parse a `final case class X(params)` or `class X(params)` line. */
  private def parseCaseClassLine(line: String): Option[(String, List[CtorParam])] =
    val pattern = """(?:final\s+)?(?:case\s+)?class\s+(\w+)(?:\[.*?\])?\s*\(""".r
    pattern.findFirstMatchIn(line).flatMap { m =>
      val name       = m.group(1)
      val afterParen = line.substring(m.end)
      // Extract the balanced content between the opening ( and its matching )
      var depth = 1
      var i     = 0
      while i < afterParen.length && depth > 0 do
        afterParen.charAt(i) match
          case '(' => depth += 1
          case ')' => depth -= 1
          case _   => ()
        i += 1
      if depth == 0 then
        val paramStr = afterParen.substring(0, i - 1)
        val params   = parseCtorParams(paramStr)
        if params.nonEmpty then Some((name, params)) else None
      else None
    }

  private def parseCtorParams(paramStr: String): List[CtorParam] =
    if paramStr.trim.isEmpty then return Nil
    val params  = mutable.ListBuffer.empty[CtorParam]
    val current = new StringBuilder
    var depth   = 0

    for ch <- paramStr do
      ch match
        case '(' | '[' | '{'   => depth += 1; current.append(ch)
        case ')' | ']' | '}'   => depth -= 1; current.append(ch)
        case ',' if depth == 0 =>
          parseCtorOneParam(current.toString.trim).foreach(params += _)
          current.clear()
        case _ => current.append(ch)

    if current.nonEmpty then parseCtorOneParam(current.toString.trim).foreach(params += _)
    params.toList

  private def parseCtorOneParam(param: String): Option[CtorParam] =
    val isVar   = param.trim.startsWith("var ")
    val trimmed = param.trim.stripPrefix("var ").stripPrefix("val ").trim
    if trimmed.startsWith("using ") || trimmed.startsWith("implicit ") then return None
    val colonIdx = trimmed.indexOf(':')
    if colonIdx < 0 then return None
    val name       = trimmed.substring(0, colonIdx).trim
    val rest       = trimmed.substring(colonIdx + 1).trim
    val hasDefault = rest.contains("=")
    val tpe        = stripDefault(rest)
    if name.nonEmpty && tpe.nonEmpty then Some(CtorParam(cleanParamName(name), tpe, hasDefault, isVar))
    else None

  /** Parse a Scala source file and extract all method signatures from the top-level object.
    *
    * Handles single-line and multi-line signatures. A multi-line signature is recognized when a `def` line has unbalanced parentheses.
    */
  def parseFile(objectName: String, source: String): List[(String, MethodSig)] =
    val results      = mutable.ListBuffer.empty[(String, MethodSig)]
    val logicalLines = joinMultiLineSignatures(source)
    val defPattern   =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*(?:\[.*?\])?\s*\((.*)\)\s*:\s*(.+?)\s*=\s*.*$""".r
    val defNoParamsPattern =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*:\s*(.+?)\s*=\s*.*$""".r
    val defMultiParamPattern =
      """^\s{2}(?:private\s+|protected\s+)?def\s+(\w+)\s*(?:\[.*?\])?\s*\((.*?)\)\s*\(.*\)\s*:\s*(.+?)\s*=\s*.*$""".r

    for line <- logicalLines do
      line match
        case defMultiParamPattern(name, paramStr, retType) =>
          results += ((objectName, MethodSig(name, parseParams(paramStr), retType.trim)))
        case defPattern(name, paramStr, retType) =>
          results += ((objectName, MethodSig(name, parseParams(paramStr), retType.trim)))
        case defNoParamsPattern(name, retType) =>
          results += ((objectName, MethodSig(name, Nil, retType.trim)))
        case _ => ()

    results.toList

  private def joinMultiLineSignatures(source: String): List[String] =
    val result   = mutable.ListBuffer.empty[String]
    val defStart = """^\s{2}(?:private\s+|protected\s+)?def\s+""".r
    var accumulator: StringBuilder = null
    var parenDepth = 0

    for line <- source.linesIterator do
      if accumulator != null then
        accumulator.append(" ")
        accumulator.append(line.trim)
        for ch <- line do
          ch match
            case '(' => parenDepth += 1
            case ')' => parenDepth -= 1
            case _   => ()
        if parenDepth <= 0 && (line.contains("=") || line.trim.endsWith("=")) then
          result += accumulator.toString
          accumulator = null
          parenDepth = 0
        else if parenDepth < 0 then
          result += accumulator.toString
          accumulator = null
          parenDepth = 0
      else if defStart.findFirstIn(line).isDefined then
        var depth = 0
        for ch <- line do
          ch match
            case '(' => depth += 1
            case ')' => depth -= 1
            case _   => ()
        if depth <= 0 then result += line
        else
          accumulator = new StringBuilder(line)
          parenDepth = depth
      else result += line

    if accumulator != null then result += accumulator.toString
    result.toList

  private def parseParams(paramStr: String): List[ParamSig] =
    if paramStr.trim.isEmpty then return Nil
    val params  = mutable.ListBuffer.empty[ParamSig]
    val current = new StringBuilder
    var depth   = 0

    for ch <- paramStr do
      ch match
        case '(' | '[' | '{' =>
          depth += 1
          current.append(ch)
        case ')' | ']' | '}' =>
          depth -= 1
          current.append(ch)
        case ',' if depth == 0 =>
          parseOneParam(current.toString.trim).foreach(params += _)
          current.clear()
        case _ =>
          current.append(ch)

    if current.nonEmpty then parseOneParam(current.toString.trim).foreach(params += _)
    params.toList

  private def parseOneParam(param: String): Option[ParamSig] =
    val trimmed = param.trim
    if trimmed.startsWith("using ") || trimmed.startsWith("implicit ") then return None
    val colonIdx =
      if trimmed.startsWith("`") then
        val endBacktick = trimmed.indexOf('`', 1)
        if endBacktick >= 0 then trimmed.indexOf(':', endBacktick + 1)
        else trimmed.indexOf(':')
      else trimmed.indexOf(':')
    if colonIdx < 0 then return None
    val name = trimmed.substring(0, colonIdx).trim
    val rest = trimmed.substring(colonIdx + 1).trim
    val tpe  = stripDefault(rest)
    if name.nonEmpty && tpe.nonEmpty then Some(ParamSig(cleanParamName(name), tpe, hasDefault = tpe != rest))
    else None

  private def stripDefault(s: String): String =
    var depth    = 0
    var equalsAt = -1
    var i        = 0
    while i < s.length && equalsAt < 0 do
      s.charAt(i) match
        case '(' | '[' | '{'   => depth += 1
        case ')' | ']' | '}'   => depth -= 1
        case '=' if depth == 0 => equalsAt = i
        case _                 => ()
      i += 1
    if equalsAt >= 0 then s.substring(0, equalsAt).trim else s.trim

  private def cleanParamName(name: String): String =
    if name.startsWith("`") && name.endsWith("`") then name.substring(1, name.length - 1)
    else name
