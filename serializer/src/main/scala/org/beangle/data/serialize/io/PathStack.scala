/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.serialize.io

import org.beangle.commons.lang.primitive.MutableInt

class PathElement(val name: String, val clazz: Class[_]) {}
class PathStack(initialCapacity: Int = 16) {
  //point to empty slot
  private var pointer: Int = 0
  private var capacity: Int = initialCapacity
  private var elements = new Array[PathElement](initialCapacity)
  private var indexMapStack = new Array[collection.mutable.HashMap[String, MutableInt]](initialCapacity)

  def push(name: String, clazz: Class[_]) {
    if (pointer + 1 >= capacity) resizeStacks(capacity * 2)
    elements(pointer) = new PathElement(name, clazz)

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

  def pop(): PathElement = {
    indexMapStack(pointer) = null
    elements(pointer) = null
    pointer -= 1
    elements(pointer)
  }

  def peek(): PathElement = {
    elements(pointer - 1)
  }

  def isFirstInLevel: Boolean = {
    var count = 0
    indexMapStack(pointer - 1) foreach {
      case (c, v) =>
        count += v.value
    }
    count == 1
  }

  def get(idx: Int): String = {
    val nodeName = elements(idx).name
    val integer = indexMapStack(idx)(nodeName)
    if (integer.value > 1) {
      val chunk = new StringBuffer(nodeName.length + 6)
      chunk.append(nodeName).append('[').append(integer.value).append(']')
      chunk.toString
    } else {
      nodeName
    }
  }

  def size: Int = {
    pointer
  }

  private def resizeStacks(newCapacity: Int): Unit = {
    val newElements = new Array[PathElement](newCapacity)
    val newIndexMapStack = new Array[collection.mutable.HashMap[String, MutableInt]](newCapacity)
    val min = Math.min(capacity, newCapacity)
    System.arraycopy(elements, 0, newElements, 0, min)
    System.arraycopy(indexMapStack, 0, newIndexMapStack, 0, min)
    elements = newElements
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