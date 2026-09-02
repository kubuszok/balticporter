package balticporter.emit

import java.nio.file.{Files, Path}

class InjectedSurfaceSpec extends munit.FunSuite:

  private def withTempDir(body: Path => Unit): Unit =
    val dir = Files.createTempDirectory("injected-surface-test")
    try body(dir)
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.deleteIfExists(_))

  test("parses trait with wildcard parameter type") {
    withTempDir { root =>
      val pkg = root.resolve("sge/utils")
      Files.createDirectories(pkg)
      Files.writeString(pkg.resolve("Pool.scala"),
        """package sge.utils
          |
          |trait Pool[A] {
          |  def freeAll(objects: DynamicArray[? <: A]): Unit
          |  def obtain(): A
          |  def getFree: Int
          |}
          |""".stripMargin)

      val surface = InjectedSurface.fromRoots(List(root))

      // freeAll has parens and the correct parameter type
      val freeAll = surface.lookup("sge.utils.Pool", "freeAll", 1)
      assert(freeAll.isDefined, "freeAll should be found")
      assertEquals(freeAll.get.paramTypes.flatten.map(_.rendered), List("DynamicArray[? <: A]"))
      assertEquals(freeAll.get.hasParens, true)

      // obtain has parens (nilary)
      val obtain = surface.lookup("sge.utils.Pool", "obtain", 0)
      assert(obtain.isDefined, "obtain should be found")
      assertEquals(obtain.get.hasParens, true)

      // getFree is parenless
      val getFree = surface.lookup("sge.utils.Pool", "getFree", 0)
      assert(getFree.isDefined, "getFree should be found")
      assertEquals(getFree.get.hasParens, false)

      // memberHasParens queries
      assertEquals(surface.memberHasParens("sge.utils.Pool", "freeAll"), Some(true))
      assertEquals(surface.memberHasParens("sge.utils.Pool", "getFree"), Some(false))
      assertEquals(surface.memberHasParens("sge.utils.Pool", "nonexistent"), None)
    }
  }

  test("parenless injected member") {
    withTempDir { root =>
      val pkg = root.resolve("sge/utils")
      Files.createDirectories(pkg)
      Files.writeString(pkg.resolve("ImmutableArray.scala"),
        """package sge.utils
          |
          |trait ImmutableArray[A] {
          |  def iterator: Iterator[A]
          |  def size: Int
          |}
          |""".stripMargin)

      val surface = InjectedSurface.fromRoots(List(root))

      assertEquals(surface.memberHasParens("sge.utils.ImmutableArray", "iterator"), Some(false))
      assertEquals(surface.memberHasParens("sge.utils.ImmutableArray", "size"), Some(false))
    }
  }

  test("empty roots produce empty surface") {
    val surface = InjectedSurface.fromRoots(Nil)
    assert(surface.isEmpty)
    assertEquals(surface.lookup("any", "any", 0), None)
    assertEquals(surface.memberHasParens("any", "any"), None)
  }

  test("nonexistent root is silently skipped") {
    val surface = InjectedSurface.fromRoots(List(Path.of("/nonexistent/path")))
    assert(surface.isEmpty)
  }

  test("type parameter substitution") {
    withTempDir { root =>
      val pkg = root.resolve("sge/utils")
      Files.createDirectories(pkg)
      Files.writeString(pkg.resolve("Pool.scala"),
        """package sge.utils
          |
          |trait Pool[A] {
          |  def freeAll(objects: DynamicArray[? <: A]): Unit
          |}
          |""".stripMargin)

      val surface = InjectedSurface.fromRoots(List(root))

      // Type params extracted
      assertEquals(surface.typeParams.get("sge.utils.Pool"), Some(List("A")))

      // Lookup with substitution: A -> T
      val freeAll = surface.lookup("sge.utils.Pool", "freeAll", 1, List("T"))
      assert(freeAll.isDefined, "freeAll should be found")
      assertEquals(freeAll.get.paramTypes.flatten.map(_.rendered), List("DynamicArray[? <: T]"))

      // Lookup without substitution
      val freeAllRaw = surface.lookup("sge.utils.Pool", "freeAll", 1)
      assert(freeAllRaw.isDefined)
      assertEquals(freeAllRaw.get.paramTypes.flatten.map(_.rendered), List("DynamicArray[? <: A]"))
    }
  }

  test("injected trait with foo(x: Buffer[? <: A]) -- spec from brief") {
    withTempDir { root =>
      val pkg = root.resolve("test/pkg")
      Files.createDirectories(pkg)
      Files.writeString(pkg.resolve("Parent.scala"),
        """package test.pkg
          |
          |trait Parent[A] {
          |  def foo(x: Buffer[? <: A]): Unit
          |  def bar: Int
          |}
          |""".stripMargin)

      val surface = InjectedSurface.fromRoots(List(root))

      // foo has parens, bar does not
      assertEquals(surface.memberHasParens("test.pkg.Parent", "foo"), Some(true))
      assertEquals(surface.memberHasParens("test.pkg.Parent", "bar"), Some(false))

      // Lookup with type param substitution
      val foo = surface.lookup("test.pkg.Parent", "foo", 1, List("String"))
      assert(foo.isDefined)
      assertEquals(foo.get.paramTypes.flatten.map(_.rendered), List("Buffer[? <: String]"))
    }
  }
