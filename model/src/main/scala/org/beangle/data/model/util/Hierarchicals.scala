/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.model.util

import org.beangle.commons.bean.Properties
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.{Objects, Strings}
import org.beangle.data.model.pojo.Hierarchical

import scala.collection.mutable

object Hierarchicals {

  /**
    * 得到给定节点的所有家族结点，包括自身
    * @param root 指定根节点
    * @return 包含自身的家族节点集合
    */
  def getFamily[T <: Hierarchical[T]](root: T): Set[T] = {
    val nodes = new mutable.HashSet[T]
    nodes += root
    loadChildren(root, nodes)
    nodes.toSet
  }

  /** 加载子节点到指定集合
    * @param node   节点
    * @param result 结果集
    */
  private def loadChildren[T <: Hierarchical[T]](node: T, result: mutable.Set[T]): Unit = {
    if (null == node.children) return
    node.children foreach { one =>
      result.add(one)
      loadChildren(one, result)
    }
  }

  /** 按照上下关系排序
    * @param datas a { @link java.util.List} object.
    * @return a { @link java.util.Map} object.
    */
  def sort[T <: Hierarchical[T]](datas: mutable.Seq[T]): collection.Map[T, String] = {
    sort(datas, "id")
  }

  /** 按照上下关系和指定属性排序
    * @param datas    a { @link java.util.List} object.
    * @param property a String object.
    * @return a { @link java.util.Map} object.
    */
  def sort[T <: Hierarchical[T]](datas: mutable.Seq[T], property: String): collection.Map[T, String] = {
    val sortedMap = tag(datas, property)
    datas.sortWith((f1, f2) => sortedMap(f1).compareTo(sortedMap(f2)) <= 0)
    sortedMap
  }

  /** Tag
    * @param datas    a { @link java.util.List} object.
    * @param property a String object.
    * @return a { @link java.util.Map} object.
    */
  def tag[T <: Hierarchical[T]](datas: collection.Seq[T], property: String): Map[T, String] = {
    val sortedMap = new mutable.HashMap[T, String]
    for (de <- datas) {
      var myId = String.valueOf(Properties.get[Any](de, property)) + "_"
      de.parent foreach { p =>
        if (sortedMap.contains(p)) {
          myId = sortedMap.getOrElse(p, "") + myId
          if (!myId.endsWith("_")) myId += "_"
        }
      }
      updatedTagFor(myId, de, sortedMap)
      sortedMap.put(de, myId)
    }
    for (de <- datas) {
      val tag = sortedMap(de)
      if (tag.endsWith("_")) {
        sortedMap.put(de, tag.substring(0, tag.length() - 1))
      }
    }
    sortedMap.toMap
  }

  private def updatedTagFor[T <: Hierarchical[T]](prefix: String, root: T, sortedMap: mutable.Map[T, String]): Unit = {
    for (child <- root.children) {
      if (sortedMap.contains(child)) {
        sortedMap.put(child, prefix + sortedMap.get(child))
        updatedTagFor(prefix, child, sortedMap)
      }
    }
  }

  /** getRoots.
    */
  def getRoots[T <: Hierarchical[T]](nodes: Seq[T]): collection.Seq[T] = {
    val roots = new mutable.ListBuffer[T]
    for (m <- nodes)
      if (m.parent.isEmpty || !nodes.contains(m.parent.get)) roots += m
    roots
  }

  /** Get the path from current node to root. First element is current and last is root.
    * @param node current node
    */
  def getPath[T <: Hierarchical[T]](node: T): collection.Seq[T] = {
    var path = List.empty[T]
    var curNode = node
    while (null != curNode && !path.contains(curNode)) {
      path = curNode :: path
      curNode = curNode.parent.getOrElse(null.asInstanceOf[T])
    }
    path
  }

  /** addParent
    */
  def addParent[T <: Hierarchical[T]](nodes: mutable.Set[T]): Unit = {
    addParent(nodes, null.asInstanceOf[T])
  }

  /** addParent
    */
  def addParent[T <: Hierarchical[T]](nodes: mutable.Set[T], toRoot: T): Unit = {
    val parents = new mutable.HashSet[T]
    for (n <- nodes) {
      var node = n
      while (node.parent.isDefined && !parents.contains(node.parent.get)
        && !Objects.equals(node.parent.get, toRoot)) {
        parents.add(node.parent.get)
        node = node.parent.get
      }
    }
    nodes ++= parents
  }

  /** 将节点移动到给定位置
    *
    * implementation:
    * 由于级联保存的原因，不要更改原有上级节点和目标上级节点的children属性
    */
  def move[T <: Hierarchical[T]](node: T, parentNode: T, index: Int): Iterable[T] = {
    if (node.parent == Option(parentNode)) {
      if (node.lastindex != index) shiftCode(node, parentNode.children, index)
      else Seq.empty
    } else {
      node.parent = Option(parentNode)
      shiftCode(node, parentNode.children, index)
    }
  }

  def move[T <: Hierarchical[T]](node: T, sibling: mutable.Buffer[T], index: Int): Iterable[T] = {
    if (node.parent == null) {
      if (node.lastindex != index) shiftCode(node, sibling, index)
      else Seq.empty
    } else {
      node.parent foreach { p => p.children -= node }
      node.parent = None
      shiftCode(node, sibling, index)
    }
  }

  private def shiftCode[T <: Hierarchical[T]](node: T, sibling: mutable.Buffer[T], idx: Int): Iterable[T] = {
    var index = idx - 1
    val sorted = sibling.sorted
    sorted -= node
    if (index > sorted.size) index = sorted.size
    sorted.insert(index, node)
    val nolength = String.valueOf(sorted.size).length
    val nodes = Collections.newSet[T]
    (1 to sorted.size) foreach { seqno =>
      val one = sorted(seqno - 1)
      generateCode(one, Strings.leftPad(String.valueOf(seqno), nolength, '0'), nodes)
    }
    if (null != node.children) {
      node.children foreach (m => generateCode(m, null, nodes))
    }
    nodes
  }

  private def generateCode[T <: Hierarchical[T]](node: T, indexno: String, nodes: collection.mutable.Set[T]): Unit = {
    nodes.add(node)
    if (null != indexno) genIndexno(node, indexno) else genIndexno(node)
    if (null != node.children) {
      node.children foreach (m => generateCode(m, null, nodes))
    }
  }

  private def genIndexno[T <: Hierarchical[T]](node: T, indexno: String): Unit = {
    node.parent match {
      case Some(p) => node.indexno = Strings.concat(p.indexno, ".", indexno)
      case None => node.indexno = indexno
    }
  }

  private def genIndexno[T <: Hierarchical[T]](node: T): Unit = {
    node.parent foreach { p =>
      val seqlen = String.valueOf(p.children.size).length
      val levelSeqNo = Strings.leftPad(String.valueOf(node.lastindex), seqlen, '0')
      node.indexno = Strings.concat(p.indexno, ".", levelSeqNo)
    }
  }
}
