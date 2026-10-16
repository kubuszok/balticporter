package balticporter.transform

import balticporter.tir.*

/** The methods that may run while a PROPER ANCESTOR's constructor is still running: reached from that ancestor's constructors, field initialisers and initialiser blocks through calls on `this` (or
  * `super`), each call dispatched to every override the program declares. Such a method reads a field of its own class before that field's initialiser ran (JLS 12.5), so it sees the JVM's default,
  * whatever initialiser the port wrote.
  */
object PreInitReads:

  /** method -> the fields among `fields` it reads while an ancestor of the field's class is being constructed. A method no construction reaches, or that reads none of them, is absent.
    */
  def of(p: Program, fields: Set[SymId]): Map[SymId, Set[SymId]] =
    if fields.isEmpty then return Map.empty
    given Program = p
    val graph     = OverrideGraph.build(p)

    /** the methods a term calls on the object under construction — an unqualified call, or one through `this` or `super`. */
    def selfCalls(t: Term): Set[SymId] =
      StandardTraversal.scanTerm(t, Set.empty[SymId]) { (acc, x) =>
        x match
          case Tree.Apply(Tree.Ident(m, _, _), _, _, _, _)                                => acc + m
          case Tree.Apply(Tree.Select(_: Tree.This | _: Tree.Super, m, _, _), _, _, _, _) => acc + m
          case _                                                                          => acc
      }

    def bodyOf(m: SymId): Option[Term] = p.definitionOf(m).collect { case d: Tree.DefDef => d.rhs }.flatten

    /** what runs while `cls` itself is constructed: its constructors, field initialisers and initialiser statements. */
    def seeds(cls: SymId): Set[SymId] =
      p.definitionOf(cls)
        .collect { case cd: Tree.ClassDef => cd }
        .toList
        .flatMap(_.body)
        .flatMap {
          case d: Tree.DefDef if p.symbolOf(d.symbol).exists(_.name == "<init>") => d.rhs.toList.flatMap(selfCalls)
          case v: Tree.ValDef                                                    => v.rhs.toList.flatMap(selfCalls)
          case t: Term                                                           => selfCalls(t).toList
          case _ => Nil
        }
        .toSet

    val reached = collection.mutable.Map.empty[SymId, Set[SymId]]
    def reach(cls: SymId): Set[SymId] = reached.getOrElseUpdate(
      cls, {
        val seen = collection.mutable.Set.empty[SymId]
        val work = collection.mutable.Queue.from(seeds(cls))
        while work.nonEmpty do
          val m = work.dequeue()
          if seen.add(m) then
            graph.overriders(m).foreach(work.enqueue)
            bodyOf(m).foreach(b => selfCalls(b).foreach(work.enqueue))
        seen.toSet
      }
    )

    def reads(m: SymId, fs: Set[SymId]): Set[SymId] =
      bodyOf(m).fold(Set.empty[SymId]) { b =>
        StandardTraversal.scanTerm(b, Set.empty[SymId]) { (acc, x) =>
          x match
            case Tree.Ident(f, _, _) if fs(f)                => acc + f
            case Tree.Select(_: Tree.This, f, _, _) if fs(f) => acc + f
            case _                                           => acc
        }
      }

    fields.toList
      .flatMap(f => p.symbolOf(f).map(s => s.owner -> f))
      .groupMap(_._1)(_._2)
      .toList
      .flatMap { (cls, fs) =>
        val below = (cls :: graph.descendantsOf(cls)).toSet
        val early = graph.ancestorsOf(cls).flatMap(reach).filter(m => p.symbolOf(m).exists(s => below(s.owner)))
        early.map(m => m -> reads(m, fs.toSet)).filter(_._2.nonEmpty)
      }
      .groupMapReduce(_._1)(_._2)(_ ++ _)
