package balticporter.tir

import java.nio.file.Files

/** [[DerivedPolicy]] read/write round-trip and diff. */
class DerivedPolicySpec extends munit.FunSuite:

  import DerivedPolicy.*

  private val sampleRows = List(
    Row(Family.OpaqueSlot, "com.example.Foo#bar", "Pixels", "com.example.Pixels"),
    Row(Family.NullableMember, "com.example.Baz#qux", "Nullable[Texture]"),
    Row(Family.Parenless, "com.example.Widget#width", "def width: Int"),
    Row(Family.KeepName, "com.example.Sprite#getX", "def getX: Float"),
    Row(Family.Property, "com.example.Sprite#getY", "var y", "y"),
    Row(Family.PropertySetter, "com.example.Sprite#setY", "var y", "y"),
    Row(Family.KeepParens, "com.example.Pool#size", "def size(): Int"),
    Row(Family.ClassTagParam, "com.example.Array#newInstance", "def newInstance[T: ClassTag]"),
    Row(Family.Public, "com.example.Pool#<init>(int)", "public <init>"),
    Row(Family.TargetName, "com.example.List#add", "@targetName addElement", "addElement"),
    Row(Family.FieldName, "com.example.Cell#active:field", "var _active", "_active"),
    Row(Family.Rename, "com.example.Ref#getCount(String)", "def count", "count")
  )

  test("round-trip: write then read back equals the original rows") {
    val dp   = DerivedPolicy(sampleRows)
    val path = Files.createTempFile("derived-policy-", ".tsv")
    try {
      DerivedPolicy.write(path, dp)
      val back = DerivedPolicy.read(path)
      assertEquals(back.rows.toSet, dp.rows.toSet)
      assertEquals(back.rows.size, dp.rows.size)
    } finally Files.deleteIfExists(path)
  }

  test("read of absent file returns empty") {
    val dp = DerivedPolicy.read(java.nio.file.Path.of("/no/such/file.tsv"))
    assert(dp.isEmpty)
  }

  test("read skips comment lines and blank lines") {
    val path = Files.createTempFile("derived-policy-", ".tsv")
    try {
      Files.writeString(path, "#family\tupstream\treference\ttarget\n\nOpaqueSlot\tcom.ex.F#b\tPixels\tcom.ex.Pixels\n\n")
      val dp = DerivedPolicy.read(path)
      assertEquals(dp.rows.size, 1)
      assertEquals(dp.rows.head.family, Family.OpaqueSlot)
    } finally Files.deleteIfExists(path)
  }

  test("write produces a header line and a trailing newline") {
    val dp   = DerivedPolicy(List(Row(Family.Parenless, "com.a.B#c", "def c: Int")))
    val path = Files.createTempFile("derived-policy-", ".tsv")
    try {
      DerivedPolicy.write(path, dp)
      val text = Files.readString(path)
      assert(text.startsWith("#family\t"), s"expected header, got: ${text.take(20)}")
      assert(text.endsWith("\n"), "expected trailing newline")
    } finally Files.deleteIfExists(path)
  }

  test("diff: identical policies produce no differences") {
    val a = DerivedPolicy(sampleRows)
    val b = DerivedPolicy(sampleRows)
    assertEquals(DerivedPolicy.diff(a, b), Nil)
  }

  test("diff: extra row in file shows 'only in file'") {
    val extra = Row(Family.Parenless, "com.extra.Z#w", "def w: String")
    val a     = DerivedPolicy(sampleRows :+ extra)
    val b     = DerivedPolicy(sampleRows)
    val diffs = DerivedPolicy.diff(a, b)
    assert(diffs.exists(_._3 == "only in file"), s"expected 'only in file' in $diffs")
    assertEquals(diffs.size, 1)
  }

  test("diff: extra row in derived shows 'only in derived'") {
    val extra = Row(Family.Public, "com.extra.Z#<init>", "public <init>")
    val a     = DerivedPolicy(sampleRows)
    val b     = DerivedPolicy(sampleRows :+ extra)
    val diffs = DerivedPolicy.diff(a, b)
    assert(diffs.exists(_._3 == "only in derived"), s"expected 'only in derived' in $diffs")
    assertEquals(diffs.size, 1)
  }

  test("diff: changed reference text shows one row per side") {
    val original = Row(Family.NullableMember, "com.example.Baz#qux", "Nullable[Texture]")
    val changed  = Row(Family.NullableMember, "com.example.Baz#qux", "Nullable[String]")
    val a        = DerivedPolicy(List(original))
    val b        = DerivedPolicy(List(changed))
    val diffs    = DerivedPolicy.diff(a, b)
    assertEquals(diffs.size, 2)
    assert(diffs.exists(_._3 == "only in file"))
    assert(diffs.exists(_._3 == "only in derived"))
  }

  test("rows with empty target round-trip as empty target") {
    val dp   = DerivedPolicy(List(Row(Family.Parenless, "com.a.B#c", "def c: Int", "")))
    val path = Files.createTempFile("derived-policy-", ".tsv")
    try {
      DerivedPolicy.write(path, dp)
      val back = DerivedPolicy.read(path)
      assertEquals(back.rows.head.target, "")
    } finally Files.deleteIfExists(path)
  }
