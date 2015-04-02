/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
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
package org.beangle.data.jdbc.meta

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.commons.lang.Strings
/**
 * JDBC table metadata
 *
 * @author chaostone
 */
class Table(var name: String) extends Comparable[Table] with Cloneable {
  var dialect: Dialect = _
  var schema: String = null
  var primaryKey: PrimaryKey = null
  var comment: String = null
  val columns = new ListBuffer[Column]
  val uniqueKeys = new ListBuffer[UniqueKey]
  val foreignKeys = new ListBuffer[ForeignKey]
  val indexes = new ListBuffer[Index]

  def this(schema: String, name: String) {
    this(name)
    this.schema = schema
  }

  def columnNames: List[String] = {
    columns.result.map(_.name)
  }

  def identifier = Table.qualify(schema, name)

  def identifier(givenSchema: String) = {
    if (null == givenSchema || givenSchema.isEmpty()) name
    else Table.qualify(givenSchema, name)
  }

  def getOrCreateUniqueKey(keyName: String) = {
    uniqueKeys.find(f => f.name == keyName) match {
      case Some(uk) => uk
      case None =>
        val uk = new UniqueKey(keyName)
        uk.table = this
        uniqueKeys += uk
        uk
    }
  }

  def insertSql: String = {
    val sb = new StringBuilder("insert into ")
    sb ++= identifier(schema)
    sb += '('
    sb ++= columnNames.mkString(",")
    sb ++= ") values("
    sb ++= ("?," * columns.size)
    sb.setCharAt(sb.length() - 1, ')')
    sb.mkString
  }

  /**
   * @param dialect
   * @return
   */
  def createSql(dialect: Dialect): String = {
    val grammar = dialect.tableGrammar
    val buf = new StringBuilder(grammar.createString).append(' ').append(identifier(schema)).append(" (")
    val iter: Iterator[Column] = columns.iterator
    val l = columns.toList
    while (iter.hasNext) {
      val col: Column = iter.next()
      buf.append(col.name).append(' ')
      var typeName = col.typeName
      if (Strings.isEmpty(typeName) || this.dialect != dialect) {
        typeName = dialect.translate(col.typeCode, col.size, col.scale)._2
      }
      buf.append(typeName)

      val defaultValue: String = col.defaultValue
      if (defaultValue != null) buf.append(" default ").append(defaultValue)

      if (col.nullable) {
        buf.append(grammar.nullColumnString)
      } else {
        buf.append(" not null")
      }
      val useUniqueConstraint = col.unique && (!col.nullable || grammar.supportsNullUnique)
      if (useUniqueConstraint) {
        if (grammar.supportsUnique) {
          buf.append(" unique")
        } else {
          getOrCreateUniqueKey(col.name + '_').addColumn(col.name)
        }
      }

      if (col.hasCheckConstraint && grammar.supportsColumnCheck) {
        buf.append(" check (").append(col.checkConstraint).append(")")
      }
      var columnComment = col.comment
      if (columnComment != null) buf.append(grammar.getColumnComment(columnComment))

      if (iter.hasNext) buf.append(", ")

    }
    if (hasPrimaryKey && primaryKey.enabled) {
      buf.append(", ").append(primaryKey.sqlConstraintString)
    }
    buf.append(')')
    if (null != comment && !comment.isEmpty()) buf.append(grammar.getComment(comment))

    buf.toString()
  }

  def querySql: String = {
    val sb: StringBuilder = new StringBuilder()
    sb.append("select ")
    for (columnName <- this.columnNames) {
      sb.append(columnName).append(',')
    }
    sb.deleteCharAt(sb.length() - 1)
    sb.append(" from ").append(identifier(schema))
    sb.toString()
  }

  def attach(dialect: Dialect): this.type = {
    this.dialect = dialect
    columns foreach { col =>
      val rs = dialect.translate(col.typeCode, col.size, col.scale)
      col.typeCode = rs._1
      col.typeName = rs._2
    }
    this
  }

  def clone(dialect: Dialect): Table = {
    this.clone().attach(dialect)
  }

  override def clone(): Table = {
    val tb: Table = new Table(schema, name)
    tb.comment = comment
    for (col <- columns)
      tb.add(col.clone())
    if (null != primaryKey) {
      tb.primaryKey = primaryKey.clone()
      tb.primaryKey.table = tb
    }
    for (fk <- foreignKeys) tb.add(fk.clone())
    for (uk <- uniqueKeys) tb.add(uk.clone())
    for (idx <- indexes) tb.add(idx.clone())
    tb
  }

  def lowerCase(): Unit = {
    this.schema = schema.toLowerCase()
    this.name = name.toLowerCase()
    for (col <- columns) col.lowerCase
    if (null != primaryKey) primaryKey.lowerCase
    for (fk <- foreignKeys) fk.lowerCase;
    for (uk <- uniqueKeys) uk.lowerCase;
    for (idx <- indexes) idx.lowerCase;
  }

  def compareTo(o: Table): Int = {
    this.identifier.compareTo(o.identifier)
  }

  private def hasPrimaryKey = null != primaryKey

  override def toString = Table.qualify(schema, name)

  def column(columnName: String): Column = {
    columns.find(f => f.name.equals(columnName)).get
  }
  def getColumn(columnName: String): Option[Column] = {
    columns.find(f => f.name.equals(columnName))
  }

  def getForeignKey(keyName: String): Option[ForeignKey] = {
    foreignKeys.find(f => f.name.equals(keyName))
  }

  def add(key: ForeignKey): ForeignKey = {
    key.table = this
    foreignKeys += key
    key
  }

  def add(key: UniqueKey): UniqueKey = {
    key.table = this
    this.uniqueKeys += key
    key
  }

  def add(column: Column): Boolean = {
    if (!columns.exists(_.name == column.name)) {
      columns += column
      true
    } else false
  }

  def add(index: Index): Index = {
    index.table = this
    indexes += index
    index
  }

  def getIndex(indexName: String): Option[Index] = {
    indexes.find(f => f.name.equals(indexName))
  }
}

object Table {
  def qualify(schema: String, name: String): String = {
    val qualifiedName: StringBuilder = new StringBuilder()
    if (null != schema)
      qualifiedName.append(schema).append('.')
    qualifiedName.append(name).toString()
  }
  def apply(schema: String, name: String): String = qualify(schema, name)
}
object TableRef {
  def apply(name: String, schema: String): TableRef = {
    new TableRef(name, schema)
  }
}
class TableRef(var name: String, var schema: String) extends Cloneable {
  def identifier = Table.qualify(schema, name)
  def identifier(givenSchema: String) = {
    if (null == givenSchema || givenSchema.isEmpty()) name
    else Table.qualify(givenSchema, name)
  }
}
