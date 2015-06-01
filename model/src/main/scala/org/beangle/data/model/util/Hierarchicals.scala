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
package org.beangle.data.model.util

import scala.collection.mutable

import org.beangle.commons.bean.Properties
import org.beangle.commons.lang.Objects
import org.beangle.data.model.Hierarchical
object Hierarchicals {

  /**
   * 得到给定节点的所有家族结点，包括自身
   *
   * @param root 指定根节点
   * @return 包含自身的家族节点集合
   * @param [T] a T object.
   */
  def getFamily[T <: Hierarchical[T]](root: T): Set[T] = {
    val nodes = new mutable.HashSet[T]
    nodes += root
    loadChildren(root, nodes)
    nodes.toSet
  }

  /**
   * 加载字节点
   *
   * @param node
   * @param children
   */
  private def loadChildren[T <: Hierarchical[T]](node: T, children: mutable.Set[T]): Unit = {
    if (null == node.children) return
    node.children foreach { one =>
      children.add(one)
      loadChildren(one, children)
    }
  }

  /**
   * 按照上下关系排序
   *
   * @param datas a {@link java.util.List} object.
   * @param [T] a T object.
   * @return a {@link java.util.Map} object.
   */
  def sort[T <: Hierarchical[T]](datas: mutable.Seq[T]): collection.Map[T, String] = {
    sort(datas, "id")
  }

  /**
   * 按照上下关系和指定属性排序
   *
   * @param datas a {@link java.util.List} object.
   * @param property a {@link java.lang.String} object.
   * @param [T] a T object.
   * @return a {@link java.util.Map} object.
   */
  def sort[T <: Hierarchical[T]](datas: mutable.Seq[T], property: String): collection.Map[T, String] = {
    val sortedMap = tag(datas, property)
    datas.sortWith((f1, f2) => sortedMap(f1).compareTo(sortedMap(f2)) <= 0)
    sortedMap
  }

  /**
   * [p]
   * tag.
   * [/p]
   *
   * @param datas a {@link java.util.List} object.
   * @param property a {@link java.lang.String} object.
   * @param [T] a T object.
   * @return a {@link java.util.Map} object.
   */
  def tag[T <: Hierarchical[T]](datas: Seq[T], property: String): Map[T, String] = {
    val sortedMap = new mutable.HashMap[T, String]
    for (de <- datas) {
      var myId = String.valueOf(Properties.get[Any](de, property)) + "_"
      if (null != de.parent && sortedMap.contains(de.parent)) {
        myId = String.valueOf(sortedMap.get(de.parent) + myId)
        if (!myId.endsWith("_")) myId += "_"
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

  private def updatedTagFor[T <: Hierarchical[T]](prefix: String, root: T, sortedMap: mutable.Map[T, String]) {
    for (child <- root.children) {
      if (sortedMap.contains(child)) {
        sortedMap.put(child, prefix + sortedMap.get(child))
        updatedTagFor(prefix, child, sortedMap)
      }
    }
  }

  /**
   * getRoots.
   *
   */
  def getRoots[T <: Hierarchical[T]](nodes: Seq[T]): Seq[T] = {
    val roots = new mutable.ListBuffer[T]
    for (m <- nodes)
      if (null == m.parent || !nodes.contains(m.parent)) roots += m
    roots
  }

  /**
   * Get the path from current node to root. First element is current and last is root.
   *
   * @param node current node
   */
  def getPath[T <: Hierarchical[T]](node: T): Seq[T] = {
    var path = List.empty[T]
    var curNode = node
    while (null != curNode && !path.contains(curNode)) {
      path = curNode :: path
      curNode = curNode.parent
    }
    path
  }

  /**
   * addParent.
   * @param [T] a T object.
   */
  def addParent[T <: Hierarchical[T]](nodes: mutable.Set[T]) {
    addParent(nodes, null.asInstanceOf[T])
  }

  /**
   * addParent.
   *
   * @param toRoot a T object.
   * @param [T] a T object.
   */
  def addParent[T <: Hierarchical[T]](nodes: mutable.Set[T], toRoot: T): Unit = {
    val parents = new mutable.HashSet[T]
    for (n <- nodes) {
      var node = n
      while (null != node.parent && !parents.contains(node.parent)
        && !Objects.equals(node.parent, toRoot)) {
        parents.add(node.parent)
        node = node.parent
      }
    }
    nodes ++= parents
  }
}