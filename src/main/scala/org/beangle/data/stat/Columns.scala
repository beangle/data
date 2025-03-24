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

import org.beangle.commons.bean.Properties
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.annotation.beta
import org.beangle.data.dao.{EntityDao, OqlBuilder}
import org.beangle.data.model.Entity

/** 统计数据的维度
 *
 * @param entityDao
 */
@beta
class Columns(entityDao: EntityDao) {

  var idx: Int = 0

  private val results = Collections.newBuffer[Matrix.Column]

  private def convertNull(x: Any): Any = {
    if x == null then "" else x
  }

  private def toString(x: Any): String = {
    if x == null then "" else x.toString
  }

  def add(name: String, title: String, datas: collection.Seq[Matrix.Row]): Unit = {
    val keys = datas.map(_.keys(idx)).toSet
    val values = keys.map(x => (convertNull(x), toString(x))).toMap
    val result = Matrix.Column(name, title, values)
    results.addOne(result)
    idx += 1
  }

  def add(name: String, title: String, datas: collection.Seq[Matrix.Row], values: Map[Any, String]): Unit = {
    val keys = datas.map(_.keys(idx)).toSet
    val selected = keys.map(x => (convertNull(x), values.getOrElse(x, "??"))).toMap
    val result = Matrix.Column(name, title, selected)
    results.addOne(result)
    idx += 1
  }

  def add[T <: Entity[_]](name: String, title: String, datas: collection.Seq[Matrix.Row],
                          entityClazz: Class[T], nameProperty: String = "name"): Unit = {
    val keys = datas.map(_.keys(idx)).toSet
    val goodKeys = keys.filter(x => x != null)
    val q = OqlBuilder.from[Entity[_]](entityClazz.getName, "t").where("t.id in (:ids)", goodKeys)
    val values = entityDao.search(q).map(x => (x.id, String.valueOf(Properties.get[Any](x, nameProperty)))).toMap
    val result =
      if (keys.size > goodKeys.size) {
        Matrix.Column(name, title, values.updated("", "")) //null key as empty string
      } else {
        Matrix.Column(name, title, values)
      }
    results.addOne(result)
    idx += 1
  }

  def build(): Seq[Matrix.Column] = {
    results.toSeq
  }
}
