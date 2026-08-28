package balticporter.frontend.spoon

import balticporter.tir.*

/** Proves that an anonymous class's field DECLARATION and its REFERENCES share ONE SymId.
  *
  * ROOT CAUSE (measured, PROGRESS.md §13.15): `anonClass` creates the symbol with key
  * `@{enclosing.raw}#<anon>N`, while `fieldSym` resolves the owner via `minter.external(ownerQ,
  * …)` where `ownerQ` is Spoon's `getQualifiedName` (`SplitPane$1`). Without the alias the two
  * keys yield two SymIds for one class, and a `ValDef` written inside the anonymous class body
  * is not found by `isWritten` — conservatively emitting `var` where `val` was correct.
  *
  * FIX: `Minter.alias(qname, id)` registered in `anonClass` after the symbol is defined. */
class AnonClassSymIdSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class W {
      |  void go() {
      |    new Runnable() {
      |      int x = 0;
      |      public void run() { x = 1; }
      |    }.run();
      |  }
      |}
      |""".stripMargin

  private val program = SpoonTir.fromSource(src)

  /** find the first `Tree.AnonClass` in the program. */
  private def findAnon: Tree.AnonClass =
    given Program = program
    var found: Option[Tree.AnonClass] = None
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        StandardTraversal.allAnonClasses(cd).foreach { (ac, _) =>
          if found.isEmpty then found = Some(ac)
        }
      }
    }
    found.getOrElse(fail("no anonymous class found"))

  test("anonymous class field declaration and assignment share one SymId") {
    val anon = findAnon
    // the field `x` is declared as a ValDef in the anonymous class body
    val fieldDecl = anon.body.collectFirst { case v: Tree.ValDef => v }
      .getOrElse(fail("no ValDef in anonymous class body"))

    // the assignment `x = 1` is inside the `run()` method body
    val assigns = collection.mutable.ListBuffer.empty[SymId]
    given Program = program
    anon.body.foreach {
      case d: Tree.DefDef =>
        d.rhs.foreach { rhs =>
          StandardTraversal.scanTerm(rhs, ()) {
            case (_, Tree.Assign(lhs, _, _, _, _)) =>
              lhs match
                case Tree.Ident(s, _, _) => assigns += s
                case Tree.Select(_, s, _, _) => assigns += s
                case _ => ()
            case _ => ()
          }
        }
      case _ => ()
    }

    assert(assigns.nonEmpty, "expected at least one assignment in the anonymous class body")
    assertEquals(
      assigns.head,
      fieldDecl.symbol,
      s"the Assign LHS SymId (${assigns.head}) must equal the ValDef symbol (${fieldDecl.symbol})"
    )
  }

  test("anonymous class method's own symbol is owned by the anonymous class") {
    val anon = findAnon
    // the `run()` method is declared in the anonymous class body
    val runMethod = anon.body.collectFirst { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "run") => d }
      .getOrElse(fail("no run() method in anonymous class body"))

    // its owner should be the anonymous class symbol
    val methodSym = program.symbolOf(runMethod.symbol).getOrElse(fail("no symbol for run()"))
    assertEquals(
      methodSym.owner,
      anon.symbol,
      s"run()'s owner (${methodSym.owner}) must be the anonymous class symbol (${anon.symbol})"
    )
  }
