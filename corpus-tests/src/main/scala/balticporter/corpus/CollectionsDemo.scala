package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.CollectionsTransform

/** Demonstrates the java→scala collections transform end-to-end: parse Java that uses
  * `java.util` collections, emit it, run the `CollectionsTransform` phase, and emit again.
  *
  *   corpus-tests/runMain balticporter.corpus.CollectionsDemo
  */
object CollectionsDemo:

  private val src =
    """package demo;
      |import java.util.*;
      |class Bag {
      |  private List<String> items = new ArrayList<String>();
      |  private Map<String, Integer> counts = new HashMap<String, Integer>();
      |  private Set<String> seen = new HashSet<String>();
      |  void add(String s) {
      |    items.add(s);
      |    counts.put(s, size());
      |    seen.add(s);
      |  }
      |  String first() { return items.get(0); }
      |  Integer count(String s) { return counts.get(s); }
      |  void bump(String s) { counts.put(s, counts.getOrDefault(s, 0) + 1); }
      |  boolean known(String s) { return counts.containsKey(s) && seen.contains(s); }
      |  void drop(String s) { seen.remove(s); counts.remove(s); }
      |  void merge(List<String> more) { items.addAll(more); }
      |  int size() { return items.size(); }
      |  boolean empty() { return items.isEmpty(); }
      |  void each() { for (String s : items) { System.out.println(s); } }
      |}
      |""".stripMargin

  def main(args: Array[String]): Unit =
    val before = SpoonTir.fromSource(src)
    println("// ===== BEFORE =====\n")
    println(new TirEmitter(before).emit)

    val after = Pipeline.run(before, List(new CollectionsTransform))
    println("\n// ===== AFTER (java collections -> scala) =====\n")
    println(new TirEmitter(after).emit)
