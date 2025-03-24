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

package org.beangle.data.stat

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.annotation.beta
import org.beangle.data.model.Entity

object Matrix {

  case class Row(keys: Seq[Any], counters: Array[Double])

  case class Column(name: String, title: String, values: Map[Any, String]) {
    def keys: Iterable[Any] = {
      values.keys
    }

    def get(key: Any): Option[String] = values.get(key)
  }
}

/** 数据统计矩阵
 *
 * @param columns
 * @param datas
 */
@beta
class Matrix(val columns: Seq[Matrix.Column], val datas: collection.Seq[Matrix.Row]) {

  def getColumn(name: String): Matrix.Column = {
    columns.find(_.name == name).head
  }

  def groupBy(columnNames: String): Matrix = {
    val names = Strings.split(columnNames)
    val newColumns = Collections.newBuffer[Matrix.Column]
    names foreach { n => newColumns.addAll(columns.find(x => x.name == n)) }
    groupBy(newColumns.toSeq)
  }

  def groupBy(newColumns: Seq[Matrix.Column]): Matrix = {
    val indices = newColumns.map(x => columns.indexOf(x))
    val newRows = Collections.newBuffer[Matrix.Row]
    datas.groupBy(d => indices.map(x => d.keys(x))) foreach { d =>
      val counters = d._2.map(_.counters)
      val rs = new Array[Double](datas.head.counters.length)
      for (i <- counters.indices; j <- counters(i).indices) {
        rs(j) += counters(i)(j)
      }
      newRows.addOne(Matrix.Row(d._1, rs))
    }
    new Matrix(newColumns, newRows)
  }

  def split(columnName: String): Map[Any, Matrix] = {
    val dimension = getColumn(columnName)
    val dIdx = columns.indexOf(dimension)
    datas.groupBy(d => d.keys(dIdx)).map { d =>
      (d._1, new Matrix(this.columns, d._2))
    }
  }

  def getCounter(keys: AnyRef*): Option[Any] = {
    val convertedKeys = keys.map {
      case e: Entity[_] => e.id
      case x => x
    }
    datas.find(x => x.keys == convertedKeys).map(_.counters)
  }

  def sum: Array[Double] = {
    val rs = new Array[Double](datas.head.counters.length)
    for (i <- datas.indices; j <- datas(i).counters.indices) {
      rs(j) += datas(i).counters(j)
    }
    rs
  }
}
