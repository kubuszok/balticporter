/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeCollectingVisitor.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 * upstream-commit: cold-port
 */
package com.vladsch.flexmark.util.ast

class NodeCollectingVisitor(_classes: java.util.Set[Class[? <: Any]]) {
  private val subClassMap: java.util.HashMap[Class[? <: Any], java.util.List[Class[? <: Any]]] = new java.util.HashMap()

  private val included: java.util.HashSet[Class[? <: Any]] = new java.util.HashSet()

  private val excluded: java.util.HashSet[Class[? <: Any]] = new java.util.HashSet()

  private val nodes: com.vladsch.flexmark.util.collection.ClassificationBag[Class[? <: Any], Node] = new com.vladsch.flexmark.util.collection.ClassificationBag(NodeCollectingVisitor.NODE_CLASSIFIER)

  private val classes: Array[Class[? <: Any]] = _classes.toArray(NodeCollectingVisitor.EMPTY_CLASSES)

  this.included.addAll(_classes)
  locally {
    val clazz$it = _classes.iterator()
    while (clazz$it.hasNext()) {
      val clazz: Class[? <: Any] = clazz$it.next()
      val classList = new java.util.ArrayList[Class[? <: Any]](1)
      classList.add(clazz)
      this.subClassMap.put(clazz, classList)
    }
  }

  def collect(node: Node): Unit = {
    visit(node)
  }

  def getSubClassingBag(): com.vladsch.flexmark.util.collection.SubClassingBag[Node] = {
    new com.vladsch.flexmark.util.collection.SubClassingBag[Node](this.nodes, this.subClassMap)
  }

  private def visit(node: Node): Unit = {
    val nodeClass: Class[? <: Any] = node.getClass()
    if (this.included.contains(nodeClass)) {
      this.nodes.add(node)
    } else {
      if ((!this.excluded.contains(nodeClass))) {
        // see if implements one of the original classes passed in
        locally {
          val clazz$arr = this.classes
          var clazz$i: Int = 0
          while ((clazz$i < clazz$arr.length)) {
            val clazz = clazz$arr(clazz$i)
            if (clazz.isInstance(node)) {
              // this class is included
              this.included.add(nodeClass)
              var classList: java.util.List[Class[? <: Any]] = this.subClassMap.get(clazz)
              if ((classList == null)) {
                classList = new java.util.ArrayList(2)
                classList.add(clazz)
                classList.add(nodeClass)
                this.subClassMap.put(clazz, classList)
              } else {
                classList.add(nodeClass)
              }
              this.nodes.add(node)
              visitChildren(node)
              return
            }
            clazz$i += 1
          }
        }
        // not of interest, exclude for next occurrence
        this.excluded.add(nodeClass)
      }
    }
    visitChildren(node)
  }

  private def visitChildren(parent: Node): Unit = {
    var node: Node = parent.getFirstChild()
    while ((node != null)) {
      // A subclass of this visitor might modify the node, resulting in getNext returning a different node or no
      // node after visiting it. So get the next node before visiting.
      val next: Node = node.getNext()
      visit(node)
      node = next
    }
  }

}

object NodeCollectingVisitor {
  val NODE_CLASSIFIER: java.util.function.Function[Node, Class[? <: Any]] = ((recv$: Node) => recv$.getClass())

  private val EMPTY_CLASSES: Array[Class[? <: Any]] = new Array[Class[? <: Any]](0)

}
