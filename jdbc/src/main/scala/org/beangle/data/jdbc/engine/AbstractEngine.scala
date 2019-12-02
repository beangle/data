/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.engine

import org.beangle.data.jdbc.meta.{MetadataLoadSql, SqlType}

abstract class AbstractEngine(val name: String,val version: Version) extends Engine {
  protected[engine] var typeNames: TypeNames = _

  private var typeMappingBuilder = new TypeNames.Builder()

  var metadataLoadSql = new MetadataLoadSql

  var keywords: Set[String] = Set.empty[String]

  def registerKeywords(words: String*): Unit = {
    keywords ++= words.toList
  }

  override def quoteChars: (Char, Char) = {
    ('\"', '\"')
  }

  protected def registerTypes(tuples: (Int, String)*): Unit = {
    tuples foreach { tuple =>
      typeMappingBuilder.put(tuple._1, tuple._2)
    }
    typeNames = typeMappingBuilder.build()
  }

  /** 按照该类型的容量进行登记
    *
    * @param tuples 类型映射
    */
  protected def registerTypes2(tuples: (Int, Int, String)*): Unit = {
    tuples foreach { tuple =>
      typeMappingBuilder.put(tuple._1, tuple._2, tuple._3)
    }
    typeNames = typeMappingBuilder.build()
  }

  def toType(typeName: String): SqlType = {
    typeNames.toType(typeName)
  }

  override final def toType(sqlCode: Int): SqlType = {
    toType(sqlCode, 0, 0)
  }

  override final def toType(sqlCode: Int, precision: Int): SqlType = {
    toType(sqlCode, precision, 0)
  }

  override def toType(sqlCode: Int, precision: Int, scale: Int): SqlType = {
    typeNames.toType(sqlCode, precision, scale)
  }

  def storeCase: StoreCase.Value = {
    StoreCase.Mixed
  }
}
