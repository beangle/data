package org.beangle.data.serializer.io

import org.beangle.commons.lang.primitive.MutableInt

class PathStack(initialCapacity: Int = 16) {
  //point to empty slot
  private var pointer: Int = 0
  private var capacity: Int = initialCapacity
  private var pathStack = new Array[String](initialCapacity)
  private var indexMapStack = new Array[collection.mutable.HashMap[String, MutableInt]](initialCapacity)

  def push(name: String) {
    if (pointer + 1 >= capacity) resizeStacks(capacity * 2)
    pathStack(pointer) = name

    var indexMap = indexMapStack(pointer)
    if (indexMap == null) {
      indexMap = new collection.mutable.HashMap[String, MutableInt]
      indexMapStack(pointer) = indexMap
    }
    indexMap.get(name) match {
      case Some(count) => count.increment()
      case None => indexMap.put(name, new MutableInt(1))
    }
    pointer += 1
  }

  def pop():String= {
    indexMapStack(pointer) = null
    pathStack(pointer) = null
    pointer -= 1
    pathStack(pointer)
  }

  def peek(): String = {
    get(pointer - 1)
  }

  def get(idx: Int): String = {
    val integer = indexMapStack(idx)(pathStack(idx))
    if (integer.value > 1) {
      val chunk = new StringBuffer(pathStack(idx).length() + 6)
      chunk.append(pathStack(idx)).append('[').append(integer.value).append(']')
      chunk.toString
    } else {
      pathStack(idx)
    }
  }

  def size: Int = {
    pointer
  }

  private def resizeStacks(newCapacity: Int): Unit = {
    val newPathStack = new Array[String](newCapacity)
    val newIndexMapStack = new Array[collection.mutable.HashMap[String, MutableInt]](newCapacity)
    val min = Math.min(capacity, newCapacity)
    System.arraycopy(pathStack, 0, newPathStack, 0, min)
    System.arraycopy(indexMapStack, 0, newIndexMapStack, 0, min)
    pathStack = newPathStack
    indexMapStack = newIndexMapStack
    capacity = newCapacity
  }

  /**
   * Current Path in stream.
   */
  def currentPath(): Path = {
    val chunks = new Array[String](pointer)
    (0 until pointer) foreach { i =>
      chunks(i) = get(i)
    }
    new Path(chunks)
  }
}