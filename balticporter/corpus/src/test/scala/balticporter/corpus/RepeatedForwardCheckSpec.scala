package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.MethodBodyTransform
import balticporter.verify.RepeatedForwardCheck

/** A repeated parameter forwarded BARE into another repeated `Object` slot compiles and passes one element — the whole sequence. The check reads Scala text the port ships, resolves the callee only
  * among the declarations it holds, and flags nothing it cannot prove.
  */
class RepeatedForwardCheckSpec extends PortSuite:

  /** the shape a consumer's body substitution took once the parameter became repeated: every date format rendered `ArraySeq(...)` */
  private val filters =
    """package f
      |class Filter {
      |  def get(index: Int, params: Object*): Object = params(index)
      |  def pick(xs: String*): String = xs.head
      |  def whole(xs: Object*): Int = xs.size
      |  def whole(x: Array[Int]): Int = x.length
      |}
      |class Date extends Filter {
      |  def apply(value: Object, params: Object*): Object = super.get(0, params)
      |  def spread(value: Object, params: Object*): Object = super.get(0, params*)
      |  def typed(names: String*): String = pick(names*)
      |  def array(value: Object, arr: Array[String]): Object = super.get(0, arr)
      |  def prims(value: Object, arr: Array[Int]): Object = super.get(0, arr)
      |  def unknown(value: Object, params: Object*): Object = java.lang.String.format("%s", params)
      |  def ambiguous(arr: Array[Int]): Int = whole(arr)
      |  def shadowed(params: Object*): Object = { val params = List(1); super.get(0, params) }
      |}
      |""".stripMargin

  private lazy val found = RepeatedForwardCheck.check(List("f/Filter.scala" -> filters))

  test("a repeated parameter handed bare to a repeated Object slot is flagged, and names the fix") {
    val rows = found.filter(_.issue == RepeatedForwardCheck.Issue.UnspreadRepeated)
    assertEquals(clue(rows).map(f => (f.enclosing, f.param, f.callee)), List(("apply", "params", "get")))
    assert(rows.head.detail.contains("`params*`"), rows.head.detail)
    assertEquals(rows.head.line, 9)
  }

  test("a reference array at the repeated tail is flagged; a primitive one is java's one element") {
    assertEquals(clue(found).filter(_.issue == RepeatedForwardCheck.Issue.UnspreadArray).map(_.enclosing), List("array"))
  }

  test("a spread, an unresolvable callee, an ambiguous overload and a rebound name are not flagged") {
    assertEquals(clue(found).map(_.enclosing).sorted, List("apply", "array"))
  }

  test("a body substitution in a port is read too — the text the engine splices") {
    val java =
      """package f;
        |class Filter { Object get(int index, Object... params) { return params[index]; } }
        |class Date extends Filter { Object apply(Object value, Object... params) { return null; } }
        |""".stripMargin
    val p    = port(java, new MethodBodyTransform(Map("f.Date#apply" -> "super.get(0, params)")))
    val rows = RepeatedForwardCheck.check(p.after.units.map(u => "U.scala" -> p.emitter.emitUnit(u)))
    assertEquals(clue(rows).map(r => (r.enclosing, r.param)), List(("apply", "params")), p.out)
  }
