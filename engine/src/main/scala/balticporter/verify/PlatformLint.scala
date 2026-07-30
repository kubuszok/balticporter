package balticporter.verify

import balticporter.core.*

/** Cross-platform readiness lint (Tier 2 data, DESIGN.md §3.7): flags usage of
  * APIs absent or divergent on Scala.js / Scala Native, producing the
  * substitution-disposition worklist (DESIGN.md §3.7) for a JS/Native port.
  */
object PlatformLint:

  final case class Hit(unit: String, category: String, detail: String)

  private val categories: List[(String, String => Boolean)] = List(
    "jackson (JVM-only: reflection-based databind)" -> (q => q.startsWith("com.fasterxml.jackson.")),
    "antlr (JVM-only runtime)" -> (q => q.startsWith("org.antlr.") || q.startsWith("liquid.parser.")),
    "strftime4j (JVM-only)" -> (q => q.startsWith("ua.co.k.")),
    "java.text (absent from JS/Native javalib)" -> (q => q.startsWith("java.text.")),
    "java.util.Locale (needs scala-java-locales)" -> (q => q == "java.util.Locale"),
    "java.time zones/formatters (needs scala-java-time on JS/Native)" -> (q =>
      q.startsWith("java.time.format.") || q == "java.time.ZoneId" || q == "java.time.ZonedDateTime"),
    "reflection (closed-world on Native, absent on JS)" -> (q =>
      q.startsWith("java.lang.reflect.") || q == "java.util.ServiceLoader" || q == "java.lang.Class"),
    "threads/concurrency (no executors on JS)" -> (q =>
      q.startsWith("java.util.concurrent.") || q == "java.lang.Thread" || q == "java.lang.ThreadLocal"),
    "java.util.regex (RE2 on Native: no lookaround/possessive)" -> (q => q.startsWith("java.util.regex.")),
    "filesystem (absent on JS)" -> (q => q.startsWith("java.nio.file.") || q == "java.io.File"),
  )

  def scan(units: List[BUnit], projectFqcns: Set[String]): List[Hit] =
    units.flatMap { u =>
      val foreign = collectAllQnames(u).filterNot(q => projectFqcns.contains(q.split('$').head))
      foreign.toList.flatMap { q =>
        categories.collectFirst {
          case (cat, pred) if pred(q) => Hit(u.sourcePath, cat, q)
        }
      }
    }

  private def collectAllQnames(u: BUnit): Set[String] =
    val acc = collection.mutable.Set[String]()
    def addT(t: BType): Unit = t match
      case BType.Ref(q, as) => acc += q; as.foreach(addT)
      case BType.Arr(e)     => addT(e)
      case BType.Wild(a, b) => a.foreach(addT); b.foreach(addT)
      case _                => ()
    val collect: BExpr => BExpr = { e =>
      e match
        case c: BExpr.Call    => c.ownerQ.foreach(acc += _)
        case n: BExpr.New     => addT(n.tpe)
        case BExpr.Typed(_, t)      => addT(t)
        case BExpr.Cast(t, _)       => addT(t)
        case BExpr.InstanceOf(_, t) => addT(t)
        case BExpr.ClassLit(t)      => addT(t)
        case BExpr.Ident(_, RefKind.StaticField(o)) => acc += o
        case _ => ()
      e
    }
    def walkType(t: BTypeDecl): Unit =
      t.superClass.foreach(addT)
      t.interfaces.foreach(addT)
      (t.fields ++ t.staticFields).foreach { f => addT(f.tpe); f.init.foreach(BirTransform.mapExpr(_)(collect)) }
      (t.methods ++ t.staticMethods).foreach { m =>
        addT(m.ret); m.params.foreach(p => addT(p.tpe)); BirTransform.mapMethod(m)(collect)
      }
      t.ctors.foreach(c => { c.params.foreach(p => addT(p.tpe)); BirTransform.mapCtor(c)(collect) })
      (t.staticInit ++ t.instanceInit).foreach(BirTransform.mapStmt(_)(collect))
      t.nested.foreach(walkType)
    u.types.foreach(walkType)
    acc.toSet
