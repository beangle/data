package org.beangle.data.serializer.io

import org.beangle.commons.lang.Strings
object Path {
  val Dot = new Path(Array("."))
}
class Path(val chunks: Array[String]) {

  override def toString: String = {
    Strings.join(chunks, "/")
  }

  def explicit(): String = {
    val buffer = new StringBuffer()
    (0 until chunks.length) foreach { i =>
      if (i > 0) buffer.append('/')
      val chunk = chunks(i)
      buffer.append(chunk)
      val length = chunk.length()
      if (length > 0) {
        val c = chunk.charAt(length - 1)
        if (c != ']' && c != '.') {
          buffer.append("[1]")
        }
      }
    }
    buffer.toString()
  }

  override def equals(o: Any): Boolean = {
    if (this eq o.asInstanceOf[AnyRef]) return true
    o match {
      case other: Path =>
        if (chunks.length != other.chunks.length) return false
        var i = 0
        while (i < chunks.length) {
          if (!chunks(i).equals(other.chunks(i))) return false
          i += 1
        }
        true
      case _ => false
    }
  }

  override def hashCode(): Int = {
    var result = 543645643
    (0 until chunks.length) foreach { i =>
      result = 29 * result + chunks(i).hashCode()
    }
    result
  }

  def relativeTo(that: Path): Path = {
    val depthOfPathDivergence = getDepthOfPathDivergence(chunks, that.chunks)
    val result = new Array[String](chunks.length + that.chunks.length - 2 * depthOfPathDivergence)
    var count = 0

    var i = 0
    (0 until chunks.length) foreach { i =>
      count += 1
      result(count) = ".."
    }
    (depthOfPathDivergence until chunks.length) foreach { i =>
      count += 1
      result(count) = that.chunks(i)
    }

    if (count == 0) Path.Dot else new Path(result)
  }

  private def getDepthOfPathDivergence(path1: Array[String], path2: Array[String]): Int = {
    val minLength = Math.min(path1.length, path2.length)
    var i = 0
    while (i < minLength) {
      if (!path1(i).equals(path2(i))) return i
      i = i + 1
    }
    minLength
  }
  def isAncestor(child: Path): Boolean = {
    if (child == null || child.chunks.length < chunks.length) return false
    var i = 0
    while (i < chunks.length) {
      if (!(chunks(i) == child.chunks(i))) return false
      i = i + 1
    }
    true
  }
}