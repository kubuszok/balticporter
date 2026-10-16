package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*

/** A constructor parameter's NAME is surface to a named argument: where the reference port spells the constructor so, the derivation reproduces it — a class-parameter `var` collapses the bean pair so
  * the constructor fills the property itself, and a parameter the reference names differently takes that name.
  */
class CtorParamReferenceSpec extends munit.FunSuite {

  private def derivedRun(javaSrc: String, file: String, reference: String, phases: List[Phase]): String =
    val dir = java.nio.file.Files.createTempDirectory("ctorparamref")
    java.nio.file.Files.writeString(dir.resolve("Ref.scala"), reference)
    val decls        = balticporter.verify.ApiParityCheck.parseSurface(List(dir)).toOption.get
    val program      = SpoonTir.fromSource(javaSrc, file)
    val derived      = balticporter.verify.ReferencePolicy.derive(program, decls, program.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    val scope        = RunScope.of(program.units.map(_.symbol).toSet, Map.empty, derivedPolicy = derived.policy.resolved(program))
    val (after, log) = Pipeline.runTraced(program, phases, new PolicyBinder(program, program.members, scope))
    new TirEmitter(after, notes = log).emit

  test("a bean pair the reference declares as a class-parameter `var` collapses, and the constructor fills that property") {
    val src =
      """package com.demo;
        |public class Buf {
        |  private Object[] items;
        |  private boolean resizable;
        |  public Buf () { this(16, true); }
        |  public Buf (int initialCapacity, boolean resizable) {
        |    this.items = new Object[initialCapacity];
        |    this.resizable = resizable;
        |  }
        |  public boolean isResizable () { return resizable; }
        |  public void setResizable (boolean resizable) { this.resizable = resizable; }
        |}
        |""".stripMargin
    val reference =
      """package com.demo
        |class Buf(initialCapacity: Int = 16, var resizable: Boolean = true)
        |""".stripMargin
    val out = derivedRun(src, "Buf.java", reference, List(new BeanPropertyTransform(derive = true)))
    assert(clue(out).contains("var resizable: scala.Boolean\n) {"), out)
    assert(!out.contains("resizable$p"), out)
    assert(!out.contains("resizable$field"), out)
    assert(!out.contains("def resizable"), out)
  }

  test("a bean pair the reference declares as a body `var` keeps its accessor pair") {
    val src =
      """package com.demo;
        |public class Conf {
        |  private int width;
        |  public Conf (int width) { this.width = width; }
        |  public int getWidth () { return width; }
        |  public void setWidth (int width) { this.width = width; }
        |}
        |""".stripMargin
    val reference =
      """package com.demo
        |class Conf(w: Int) { var width: Int = w }
        |""".stripMargin
    val out = derivedRun(src, "Conf.java", reference, List(new BeanPropertyTransform(derive = true)))
    assert(clue(out).contains("def width: scala.Int"), out)
  }

  test("a constructor parameter the reference names differently takes that name, so its clash needs no `$p`") {
    val src =
      """package com.demo;
        |public class Machine {
        |  protected Object globalState;
        |  public Machine (Object owner, Object globalState) {
        |    setGlobalState(globalState);
        |  }
        |  public void setGlobalState (Object state) { if (state != null) this.globalState = state; }
        |}
        |""".stripMargin
    val reference =
      """package com.demo
        |class Machine(owner: AnyRef, _globalState: AnyRef) {
        |  protected var globalState: AnyRef = null
        |  def setGlobalState(state: AnyRef): Unit = ()
        |}
        |""".stripMargin
    val phase = new MemberRenameTransform(derive = true)
    val out   = derivedRun(src, "Machine.java", reference, List(phase))
    assert(clue(out).contains("_globalState: java.lang.Object"), out)
    assert(!out.contains("globalState$p"), out)
    assert(out.contains("this.setGlobalState(_globalState)"), out)
    assert(phase.policyReport.findings.isEmpty, phase.policyReport.findings.mkString("\n"))
  }

  test("a reference constructor with defaults names only the java slots of its own type") {
    val src =
      """package com.demo;
        |public class Par {
        |  public String policy;
        |  public Par (Object[] tasks) { this("seq", tasks); }
        |  public Par (String policy, Object[] tasks) { this.policy = policy; }
        |}
        |""".stripMargin
    val reference =
      """package com.demo
        |class Par(var policy: String = "seq", children: Array[AnyRef] = Array.empty) { def this(children: Array[AnyRef]) = this("seq", children) }
        |""".stripMargin
    val out = derivedRun(src, "Par.java", reference, List(new MemberRenameTransform(derive = true)))
    assert(clue(out).contains("def this(children: scala.Array[java.lang.Object])"), out)
    assert(!out.contains("def this(policy: scala.Array"), out)
  }

  test("reference parameter names that swap java's are read per slot, and a keyword name is never taken") {
    val src =
      """package com.demo;
        |public class Pair {
        |  public int sum;
        |  public Pair (int a, int b, int c) { sum = a - b + c; }
        |}
        |""".stripMargin
    val reference =
      """package com.demo
        |class Pair(b: Int, a: Int, `type`: Int) { var sum: Int = b - a + `type` }
        |""".stripMargin
    val phase = new MemberRenameTransform(derive = true)
    val out   = derivedRun(src, "Pair.java", reference, List(phase))
    assert(clue(out).contains("(b: scala.Int, a: scala.Int, c: scala.Int)"), out)
    assert(out.contains("this.sum = (b - a) + c"), out)
  }
}
