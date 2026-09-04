package balticporter.verify

import balticporter.core.*

/** Every non-private member of the original Java unit must appear in the emitted Scala skeleton.
  * Constructor shapes are exempt (the funnel restructures them); private members are not required. */
object ApiParity:

  final case class Expectation(path: String, name: String, arity: Option[Int]):
    override def toString: String = s"$path: $name${arity.map("/" + _).getOrElse("")}"

  def expectations(unit: BUnit): List[Expectation] =
    unit.types.flatMap(t => ofType(t, ""))

  private def visible(m: Mods): Boolean = m.vis != Vis.Private

  private def ofType(t: BTypeDecl, path: String): List[Expectation] =
    val self = Expectation(path, t.name, None)
    val here = s"$path/${t.name}"
    val companion = s"$path/${t.name}$$"
    val instance =
      t.methods.filter(m => visible(m.mods)).map(m => Expectation(here, m.name, Some(m.params.length))) ++
        t.fields.filter(f => visible(f.mods)).map(f => Expectation(here, f.name, None)) ++
        t.enumCases.map(c => Expectation(here, c.name, None))
    val statics =
      t.staticMethods.filter(m => visible(m.mods)).map(m => Expectation(companion, m.name, Some(m.params.length))) ++
        t.staticFields.filter(f => visible(f.mods)).map(f => Expectation(companion, f.name, None))
    val nested = t.nested.flatMap(n => ofType(n, companion))
    self :: instance ++ statics ++ nested

  /** Members required but absent from the emitted skeleton. */
  def check(unit: BUnit, emitted: List[SkeletonDiff.Member]): List[Expectation] =
    expectations(unit).filterNot { e =>
      emitted.exists { m =>
        m.path == e.path && m.name == e.name &&
        e.arity.forall(a => m.kind != "def" || m.arity == a)
      }
    }
