package balticporter.frontend.ts
package dedicated

class RastReadSpec extends munit.FunSuite:

  private def file(literal: String): String =
    s"""{"version":1,"path":"a.ts","sha256":"x","nodes":[{"kind":"StringLiteral","kindCode":11,"pos":[0,1],"text":$literal,"value":$literal}],"symbols":{},"types":{}}"""

  test("a lone surrogate escape in a string is read as that surrogate") {
    val node = Rast.readFile(file("\"[\\ud800-\\udbff]\"")).nodes.head
    assertEquals(node.text, Some("[\ud800-\udbff]"))
    assertEquals(node.value, Some(RastValue.Str("[\ud800-\udbff]")))
  }

  test("escapes and multi-byte text decode as JSON says") {
    val node = Rast.readFile(file("\"a\\\"b\\\\c\\n\\u00e9 é \\ud83d\\ude00\"")).nodes.head
    assertEquals(node.text, Some("a\"b\\c\né é 😀"))
  }

  test("a surrogate or a control character is written as its escape in a Scala literal") {
    assertEquals(BodySyntax.escapeString("[\ud800-\udbff]\u0000"), "[\\ud800-\\udbff]\\u0000")
    assertEquals(BodySyntax.escapeString("é\"\n"), "é\\\"\\n")
  }
