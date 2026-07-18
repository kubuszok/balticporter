/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark-util-ast/src/main/java/com/vladsch/flexmark/util/ast/NodeRepository.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 * upstream-commit: cold-port
 */
package com.vladsch.flexmark.util.ast

abstract class NodeRepository[T](_keepType: KeepType) extends java.util.Map[String, T] {
  protected[ast] val nodeList: java.util.ArrayList[T] = new java.util.ArrayList[T]()

  protected[ast] val nodeMap: java.util.Map[String, T] = new java.util.HashMap[String, T]()

  protected[ast] val keepType: KeepType = (if ((_keepType == null)) KeepType.LOCKED else _keepType)

  def getDataKey(): com.vladsch.flexmark.util.data.DataKey[? <: NodeRepository[T]]

  def getKeepDataKey(): com.vladsch.flexmark.util.data.DataKey[KeepType]

  // function implementing extraction of referenced elements by given node or its children
  def getReferencedElements(parent: Node): java.util.Set[T]

  protected[ast] final def visitNodes(parent: Node, runnable: java.util.function.Consumer[Node], classes: Class[? <: Node]*): Unit = {
    val visitor: NodeVisitor = new NodeVisitor()
    locally {
      val clazz$arr = classes
      var clazz$i: Int = 0
      while ((clazz$i < clazz$arr.length)) {
        val clazz = clazz$arr(clazz$i)
        visitor.addHandler(new VisitHandler(clazz.asInstanceOf[Class[com.vladsch.flexmark.util.ast.Node]], runnable.accept))
        clazz$i += 1
      }
    }
    visitor.visit(parent)
  }

  def normalizeKey(key: CharSequence): String = {
    key.toString()
  }

  def getFromRaw(rawKey: CharSequence): T = {
    this.nodeMap.get(normalizeKey(rawKey))
  }

  def putRawKey(key: CharSequence, t: T): T = {
    put(normalizeKey(key), t)
  }

  def getValues(): java.util.Collection[T] = {
    this.nodeMap.values()
  }

  override def put(s: String, t: T): T = {
    this.nodeList.add(t)
    if ((this.keepType == KeepType.LOCKED)) {
      throw new IllegalStateException("Not allowed to modify LOCKED repository")
    }
    if ((this.keepType != KeepType.LAST)) {
      val another: T = this.nodeMap.get(s)
      if ((another != null)) {
        if ((this.keepType == KeepType.FAIL)) {
          throw new IllegalStateException(("Duplicate key " + s))
        }
        return another
      }
    }
    this.nodeMap.put(s, t)
  }

  override def putAll(map: java.util.Map[? <: String, ? <: T]): Unit = {
    if ((this.keepType == KeepType.LOCKED)) {
      throw new IllegalStateException("Not allowed to modify LOCKED repository")
    }
    if ((this.keepType != KeepType.LAST)) {
      locally {
        val key$it = map.keySet().iterator()
        while (key$it.hasNext()) {
          val key: String = key$it.next()
          this.nodeMap.put(key, map.get(key))
        }
      }
    } else {
      this.nodeMap.putAll(map)
    }
  }

  override def remove(o: Any): T = {
    if ((this.keepType == KeepType.LOCKED)) {
      throw new IllegalStateException("Not allowed to modify LOCKED repository")
    }
    this.nodeMap.remove(o)
  }

  override def clear(): Unit = {
    if ((this.keepType == KeepType.LOCKED)) {
      throw new IllegalStateException("Not allowed to modify LOCKED repository")
    }
    this.nodeMap.clear()
  }

  override def size(): Int = {
    this.nodeMap.size()
  }

  override def isEmpty(): Boolean = {
    this.nodeMap.isEmpty()
  }

  override def containsKey(o: Any): Boolean = {
    this.nodeMap.containsKey(o)
  }

  override def containsValue(o: Any): Boolean = {
    this.nodeMap.containsValue(o)
  }

  override def get(o: Any): T = {
    this.nodeMap.get(o)
  }

  override def keySet(): java.util.Set[String] = {
    this.nodeMap.keySet()
  }

  override def values(): java.util.List[T] = {
    this.nodeList
  }

  override def entrySet(): java.util.Set[java.util.Map.Entry[String, T]] = {
    this.nodeMap.entrySet()
  }

  override def equals(o: Any): Boolean = {
    this.nodeMap.equals(o)
  }

  override def hashCode(): Int = {
    this.nodeMap.hashCode()
  }

}

object NodeRepository {
  def transferReferences[T](destination: NodeRepository[T], included: NodeRepository[T], onlyIfUndefined: Boolean, referenceIdMap: java.util.Map[String, String]): Boolean = {
    // copy references but only if they are not defined in the original document
    var transferred: Boolean = false
    locally {
      val entry$it = included.entrySet().iterator()
      while (entry$it.hasNext()) {
        val entry: java.util.Map.Entry[String, T] = entry$it.next()
        val key: String = entry.getKey()
        // map as requested
        if ((referenceIdMap != null)) {
          referenceIdMap.getOrDefault(key, key)
        }
        if (((!onlyIfUndefined) || (!destination.containsKey(key)))) {
          destination.put(key, entry.getValue())
          transferred = true
        }
      }
    }
    transferred
  }

}
