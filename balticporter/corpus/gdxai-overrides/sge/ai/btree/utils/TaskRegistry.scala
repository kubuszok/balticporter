/*
 * Ported from gdx-ai — https://github.com/libgdx/gdx-ai
 * Original source: com/badlogic/gdx/ai/btree/utils/BehaviorTreeParser.java (the reflective half)
 * Original authors: davebaol
 * Licensed under the Apache License, Version 2.0
 *
 * INJECTED SCALA — the reflection-free stand-in for the THREE things
 * `BehaviorTreeParser.DefaultBehaviorTreeReader` asked the JVM at run time:
 *
 *   java                                                    here
 *   ClassReflection.newInstance(ClassReflection.forName(s))  TaskRegistry.newTask(s)
 *   ClassReflection.getAnnotation(c, TaskConstraint.class)   TaskRegistry.metaOf(c)
 *   + getFields + getDeclaredAnnotation(TaskAttribute.class)
 *   ClassReflection.getField(c, name)                        TaskRegistry.fieldOf(c, name)
 *
 * The libGDX base drops `com.badlogic.gdx.utils.reflect` outright — runtime reflection is the one
 * thing Scala.js and Scala Native cannot do — so those three are the whole of gdx-ai's remaining
 * wall. Five method BODIES in the reader are substituted onto this table
 * (`MethodBodyTransform`, see `GdxAiPolicy`); everything else in that 780-line file, the enum
 * `Statement` and its four constant bodies included, stays mechanically translated.
 *
 * THE SHAPE IS THE REFERENCE HAND PORT'S. `../sge/sge-extension/ai/src/main/scala/sge/ai/btree/
 * utils/BehaviorTreeParser.scala` answers the same wall with a nested `TaskRegistry` of
 * `alias -> () => Task[?]` factories plus a `TaskMeta`/`AttrInfo` pair carrying SETTER CLOSURES in
 * place of `Field.set` — because a closure knows the field's type where it is written, and that is
 * precisely what `castValue` had to ask the JVM for. What differs is what the two ports are: the
 * hand port REPLACES the parser and keys its registry on aliases, while this one keeps the
 * translated parser and therefore has to key on exactly what the translated parser passes — the
 * fully-qualified class NAME `DEFAULT_IMPORTS` resolved (`c.getName()` over the port's own emitted
 * classes) and the runtime `Class` of the task being configured.
 *
 * ==What this is NOT, stated rather than discovered later==
 * Java's mechanism is OPEN: any class on the classpath carrying `@TaskConstraint`/`@TaskAttribute`
 * works, with no registration at all. A table is CLOSED, so a task type nobody registered is a
 * refusal — a `ReflectionException` wrapped exactly as java wrapped its own `ClassNotFoundException`
 * — and that refusal is LOUDER than java and never quieter (CLAUDE.md §1). The eighteen built-ins
 * that `DEFAULT_IMPORTS` names are pre-registered here, and [[TaskRegistry.register]] plus
 * [[TaskRegistry.constrain]] / [[TaskRegistry.attribute]] are what a consumer's own task class
 * needs. Java's `@Inherited` half IS reproduced — [[TaskRegistry.metaOf]] walks the superclass
 * chain, so a consumer's `class MyTask extends LeafTask` inherits `(0, 0)` from the registration of
 * `LeafTask` exactly as java inherited it from the annotation, and only the ATTRIBUTES a new class
 * declares itself have to be declared.
 */
package sge.ai.btree.utils

/** The reflection-free task table `DefaultBehaviorTreeReader` resolves against.
  *
  * Mutable and process-global, because the thing it replaces is too: java's answer came from the
  * classpath, which is one per JVM. Register before parsing.
  */
object TaskRegistry:

  /** one `@TaskAttribute` slot, as `findMetadata` needs to see it.
    *
    * @param name      the attribute's name in the `.btree` text — `@TaskAttribute(name = …)`, or the
    *                  field's name where the annotation does not say
    * @param fieldName the JAVA field's name, which is what `AttrInfo.fieldName` carries and what
    *                  `getField` is later asked for
    */
  final class Attr private[utils] (val name: String, val fieldName: String, val required: Boolean,
                                   val field: TaskField)

  /** one task class's `@TaskConstraint` plus every `@TaskAttribute` in its hierarchy — what java
    * built out of `getAnnotation` and `getFields`. */
  final class Meta private[utils] (val minChildren: Int, val maxChildren: Int,
                                   val attributes: Array[Attr])

  private val factories  = scala.collection.mutable.LinkedHashMap.empty[String, () => sge.ai.btree.Task[java.lang.Object]]
  private val constraints = scala.collection.mutable.LinkedHashMap.empty[String, (Int, Int)]
  private val attributes  = scala.collection.mutable.LinkedHashMap.empty[String, List[Attr]]

  // -------------------------------------------------------------------------------------------
  // what the substituted bodies call
  // -------------------------------------------------------------------------------------------

  /** java's `ClassReflection.newInstance(ClassReflection.forName(className))`.
    *
    * @throws sge.utils.reflect.ReflectionException
    *   for a name nothing registered — the SAME exception java's `forName` threw for a class that
    *   is not on the classpath, so `openTask`'s own `catch` still turns it into
    *   `GdxRuntimeException("Cannot parse behavior tree!!!", e)` with java's wording.
    */
  def newTask(className: String): sge.ai.btree.Task[java.lang.Object] =
    factories.get(className) match
      case Some(f) => f()
      case None    =>
        throw new sge.utils.reflect.ReflectionException(
          s"no task registered for '$className' — this port resolves task names through " +
            "sge.ai.btree.utils.TaskRegistry rather than through runtime reflection, which the " +
            "libGDX base drops. Register it with TaskRegistry.register(\"" + className + "\", () => …)")

  /** java's `getAnnotation(clazz, TaskConstraint.class)` + `getFields` + `getDeclaredAnnotation(
    * TaskAttribute.class)`, over the registration table.
    *
    * The superclass walk is java's `@Inherited` and java's `Class#getFields`, in one pass: the
    * NEAREST registered constraint wins (that is what `@Inherited` means), while attributes
    * ACCUMULATE down the whole chain (that is what `getFields` returned), nearest declaration
    * winning on a name collision.
    *
    * `null` where no class in the chain has a constraint — java's own answer when no
    * `@TaskConstraint` is found, which `createStackedTask` turns into
    * `"@TaskConstraint annotation not found in '…' class hierarchy"`.
    */
  def metaOf(clazz: java.lang.Class[?]): Meta =
    var c: java.lang.Class[?] = clazz
    var min                   = 0
    var max                   = 0
    var found                 = false
    val attrs                 = scala.collection.mutable.LinkedHashMap.empty[String, Attr]
    while c != null do
      val n = c.getName
      if !found then
        constraints.get(n).foreach { mm => min = mm._1; max = mm._2; found = true }
      attributes.get(n).foreach(_.foreach(a => if !attrs.contains(a.name) then attrs.put(a.name, a)))
      c = c.getSuperclass.asInstanceOf[java.lang.Class[?]]
    if found then new Meta(min, max, attrs.values.toArray) else null

  /** java's `ClassReflection.getField(clazz, fieldName)`, restricted to the slots this port knows
    * how to coerce and store. `null` where there is none — `getField`'s substituted body turns that
    * into the `GdxRuntimeException` java raised for a `ReflectionException`. */
  def fieldOf(clazz: java.lang.Class[?], fieldName: String): TaskField =
    var c: java.lang.Class[?]  = clazz
    var out: TaskField = null
    while c != null && out == null do
      attributes.get(c.getName).foreach(_.foreach(a => if out == null && a.fieldName == fieldName then out = a.field))
      c = c.getSuperclass.asInstanceOf[java.lang.Class[?]]
    out

  // -------------------------------------------------------------------------------------------
  // registration — what a consumer's own task class needs, and what java needed nothing for
  // -------------------------------------------------------------------------------------------

  /** the `@TaskConstraint` of a task class, by its runtime class name. Inherited by every subclass
    * with no constraint of its own, exactly as java's `@Inherited` annotation was. */
  def constrain(className: String, minChildren: Int, maxChildren: Int): Unit =
    constraints.put(className, (minChildren, maxChildren))

  /** the factory `ClassReflection.newInstance` stood for. Registered under EVERY name a `.btree`
    * file may spell the class as — see [[registerBuiltins]] on why there are two per built-in. */
  def register(className: String, factory: () => sge.ai.btree.Task[java.lang.Object]): Unit =
    factories.put(className, factory)

  /** one `@TaskAttribute` field of a task class. */
  def attribute(className: String, name: String, fieldName: String, required: Boolean,
                typeName: String,
                cast: (java.lang.Object, DistributionAdapters) => java.lang.Object,
                set: (java.lang.Object, java.lang.Object) => Unit): Unit =
    val a = new Attr(name, fieldName, required, new TaskField(fieldName, typeName, cast, set))
    attributes.put(className, attributes.getOrElse(className, Nil).filterNot(_.name == name) :+ a)

  // -------------------------------------------------------------------------------------------
  // the coercions — java's `castValue`, one branch per field TYPE rather than one `switch`
  // -------------------------------------------------------------------------------------------

  /** `castValue`'s `String` branch (`BehaviorTreeParser.java:437`). */
  def asString(value: java.lang.Object): java.lang.Object = value match
    case s: java.lang.String => s
    case _                   => null

  /** `castValue`'s `Boolean` branch (`:433`). */
  def asBoolean(value: java.lang.Object): java.lang.Object = value match
    case b: java.lang.Boolean => b
    case _                    => null

  /** `castValue`'s two `Distribution` branches (`:428` for a number, `:442` for a string), which
    * are one function of the target distribution type. The `"constant," + n` spelling is java's
    * own. */
  def asDistribution[T <: sge.ai.utils.random.Distribution](
      value: java.lang.Object, tpe: java.lang.Class[T], adapters: DistributionAdapters,
  ): java.lang.Object = value match
    case n: java.lang.Number => adapters.toDistribution("constant," + n, tpe)
    case s: java.lang.String => adapters.toDistribution(s, tpe)
    case _                   => null

  /** `castValue`'s enum branch (`:446`) — the constant whose `name()` equals the string, ignoring
    * case, and `null` where none does. The constants are handed in rather than read off the class,
    * because `Class#getEnumConstants` is reflection and the emitted enum is a sealed hierarchy the
    * JVM does not know is one. */
  def asEnum[T <: java.lang.Object](value: java.lang.Object, constants: Array[T],
                                    nameOf: T => String): java.lang.Object = value match
    case s: java.lang.String =>
      var i:   Int              = 0
      var out: java.lang.Object = null
      while i < constants.length && out == null do
        if nameOf(constants(i)).equalsIgnoreCase(s) then out = constants(i)
        i += 1
      out
    case _ => null

  // -------------------------------------------------------------------------------------------
  // the built-ins
  // -------------------------------------------------------------------------------------------

  /** Register everything `DEFAULT_IMPORTS` names, plus the six `@TaskConstraint` sites the whole
    * `btree` hierarchy has.
    *
    * TWO NAMES PER FACTORY. `DEFAULT_IMPORTS` is built at run time from `c.getName()` over the
    * port's OWN emitted classes, so a built-in arrives here as `sge.ai.btree.…`. A `.btree` asset
    * written against upstream gdx-ai names the class it knew — `com.badlogic.gdx.ai.btree.…` — and
    * java resolved that through `forName`. The port renamed the class and cannot rename the asset,
    * so both spellings resolve; the alternative is a library that cannot read its own upstream's
    * data files.
    */
  private def registerBuiltins(): Unit =
    // ---- the six @TaskConstraint sites, by their emitted class names ----
    // Task            @TaskConstraint                                  -> defaults (0, MAX_VALUE)
    constrain("sge.ai.btree.Task", 0, java.lang.Integer.MAX_VALUE)
    // BranchTask      @TaskConstraint(minChildren = 1)
    constrain("sge.ai.btree.BranchTask", 1, java.lang.Integer.MAX_VALUE)
    // Decorator       @TaskConstraint(minChildren = 1, maxChildren = 1)
    constrain("sge.ai.btree.Decorator", 1, 1)
    // LeafTask        @TaskConstraint(minChildren = 0, maxChildren = 0)
    constrain("sge.ai.btree.LeafTask", 0, 0)
    // Include         @TaskConstraint(minChildren = 0, maxChildren = 0)
    constrain("sge.ai.btree.decorator.Include", 0, 0)
    // Random          @TaskConstraint(minChildren = 0, maxChildren = 1)
    constrain("sge.ai.btree.decorator.Random", 0, 1)

    // ---- the eighteen DEFAULT_IMPORTS classes ----
    both("branch.Selector", () => new sge.ai.btree.branch.Selector[java.lang.Object]())
    both("branch.Sequence", () => new sge.ai.btree.branch.Sequence[java.lang.Object]())
    both("branch.RandomSelector", () => new sge.ai.btree.branch.RandomSelector[java.lang.Object]())
    both("branch.RandomSequence", () => new sge.ai.btree.branch.RandomSequence[java.lang.Object]())
    both("branch.DynamicGuardSelector", () => new sge.ai.btree.branch.DynamicGuardSelector[java.lang.Object]())
    both("branch.Parallel", () => new sge.ai.btree.branch.Parallel[java.lang.Object]())
    both("decorator.AlwaysFail", () => new sge.ai.btree.decorator.AlwaysFail[java.lang.Object]())
    both("decorator.AlwaysSucceed", () => new sge.ai.btree.decorator.AlwaysSucceed[java.lang.Object]())
    both("decorator.Invert", () => new sge.ai.btree.decorator.Invert[java.lang.Object]())
    both("decorator.UntilFail", () => new sge.ai.btree.decorator.UntilFail[java.lang.Object]())
    both("decorator.UntilSuccess", () => new sge.ai.btree.decorator.UntilSuccess[java.lang.Object]())
    both("decorator.SemaphoreGuard", () => new sge.ai.btree.decorator.SemaphoreGuard[java.lang.Object]())
    both("decorator.Repeat", () => new sge.ai.btree.decorator.Repeat[java.lang.Object]())
    both("decorator.Include", () => new sge.ai.btree.decorator.Include[java.lang.Object]())
    // THE ONE FACTORY THAT IS NOT A BARE `new`, and it is not a choice. Java's `Random()` delegates
    // — `this(ConstantFloatDistribution.ZERO_POINT_FIVE)` — and the port's emitted `Random` has no
    // nilary constructor to carry that: `ENGINE-LIMITS.md` C11 refuses to emit one, because scala's
    // implicit nilary primary runs NOTHING and `def this()` beside it is `E120`. The emitted class
    // therefore builds an object java could not build, with `success` unset, and the porter note on
    // that member says in as many words that a port needing the behaviour writes it by hand. This
    // is that hand-written constructor, at the one place in the port that calls it.
    both("decorator.Random", () => {
      val t = new sge.ai.btree.decorator.Random[java.lang.Object]()
      t.success$shadow = sge.ai.utils.random.ConstantFloatDistribution.ZERO_POINT_FIVE
      t
    })
    both("leaf.Success", () => new sge.ai.btree.leaf.Success[java.lang.Object]())
    both("leaf.Failure", () => new sge.ai.btree.leaf.Failure[java.lang.Object]())
    both("leaf.Wait", () => new sge.ai.btree.leaf.Wait[java.lang.Object]())

    // ---- the eight @TaskAttribute fields ----
    // Wait: @TaskAttribute(required = true) public FloatDistribution seconds;
    attribute("sge.ai.btree.leaf.Wait", "seconds", "seconds", true, "FloatDistribution",
      (v, a) => asDistribution(v, classOf[sge.ai.utils.random.FloatDistribution], a),
      (t, v) => t.asInstanceOf[sge.ai.btree.leaf.Wait[java.lang.Object]].seconds =
        v.asInstanceOf[sge.ai.utils.random.FloatDistribution])
    // Include: @TaskAttribute(required = true) public String subtree;
    attribute("sge.ai.btree.decorator.Include", "subtree", "subtree", true, "String",
      (v, _) => asString(v),
      (t, v) => t.asInstanceOf[sge.ai.btree.decorator.Include[java.lang.Object]].subtree =
        v.asInstanceOf[java.lang.String])
    // Include: @TaskAttribute public boolean lazy;  — `lazy` is a scala keyword, so the emitted
    // member is back-quoted; the ATTRIBUTE and the java field are still spelled `lazy`, which is
    // what the `.btree` text and `AttrInfo.fieldName` both carry.
    attribute("sge.ai.btree.decorator.Include", "lazy", "lazy", false, "boolean",
      (v, _) => asBoolean(v),
      (t, v) => t.asInstanceOf[sge.ai.btree.decorator.Include[java.lang.Object]].`lazy` =
        v.asInstanceOf[java.lang.Boolean].booleanValue())
    // Repeat: @TaskAttribute public IntegerDistribution times;
    attribute("sge.ai.btree.decorator.Repeat", "times", "times", false, "IntegerDistribution",
      (v, a) => asDistribution(v, classOf[sge.ai.utils.random.IntegerDistribution], a),
      (t, v) => t.asInstanceOf[sge.ai.btree.decorator.Repeat[java.lang.Object]].times =
        v.asInstanceOf[sge.ai.utils.random.IntegerDistribution])
    // Random: @TaskAttribute public FloatDistribution success;  — the field SHADOWS an inherited
    // one, so §4.55 renamed the emitted member to `success$shadow`. The java field is still
    // `success`, which is the name the `.btree` text uses and the name `getField` is handed.
    attribute("sge.ai.btree.decorator.Random", "success", "success", false, "FloatDistribution",
      (v, a) => asDistribution(v, classOf[sge.ai.utils.random.FloatDistribution], a),
      (t, v) => t.asInstanceOf[sge.ai.btree.decorator.Random[java.lang.Object]].success$shadow =
        v.asInstanceOf[sge.ai.utils.random.FloatDistribution])
    // SemaphoreGuard: @TaskAttribute(required = true) public String name;
    attribute("sge.ai.btree.decorator.SemaphoreGuard", "name", "name", true, "String",
      (v, _) => asString(v),
      (t, v) => t.asInstanceOf[sge.ai.btree.decorator.SemaphoreGuard[java.lang.Object]].name =
        v.asInstanceOf[java.lang.String])
    // Parallel: @TaskAttribute public Policy policy;
    attribute("sge.ai.btree.branch.Parallel", "policy", "policy", false, "Policy",
      (v, _) => asEnum(v, sge.ai.btree.branch.Parallel.Policy.values(), (p: sge.ai.btree.branch.Parallel.Policy) => p.name()),
      (t, v) => t.asInstanceOf[sge.ai.btree.branch.Parallel[java.lang.Object]].policy =
        v.asInstanceOf[sge.ai.btree.branch.Parallel.Policy])
    // Parallel: @TaskAttribute public Orchestrator orchestrator;
    attribute("sge.ai.btree.branch.Parallel", "orchestrator", "orchestrator", false, "Orchestrator",
      (v, _) => asEnum(v, sge.ai.btree.branch.Parallel.Orchestrator.values(), (o: sge.ai.btree.branch.Parallel.Orchestrator) => o.name()),
      (t, v) => t.asInstanceOf[sge.ai.btree.branch.Parallel[java.lang.Object]].orchestrator =
        v.asInstanceOf[sge.ai.btree.branch.Parallel.Orchestrator])

  /** register one built-in under BOTH the port's emitted FQCN and gdx-ai's upstream one — see
    * [[registerBuiltins]]. */
  private def both(suffix: String, factory: () => sge.ai.btree.Task[java.lang.Object]): Unit =
    register("sge.ai.btree." + suffix, factory)
    register("com.badlogic.gdx.ai.btree." + suffix, factory)

  registerBuiltins()
