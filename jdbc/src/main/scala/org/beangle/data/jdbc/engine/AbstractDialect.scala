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

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.meta._

trait AbstractDialect extends Dialect {
  self: Engine =>

  protected var options = new Options

  /** Table creation sql
   */
  override def createTable(table: Table): String = {
    val buf = new StringBuilder("create table").append(' ').append(table.qualifiedName).append(" (")
    val iter: Iterator[Column] = table.columns.iterator
    while (iter.hasNext) {
      val col: Column = iter.next()
      buf.append(col.name.toLiteral(this)).append(' ')
      buf.append(col.sqlType.name)

      col.defaultValue foreach { dv =>
        buf.append(" default ").append(dv)
      }
      if (!col.nullable) {
        buf.append(" not null")
      }
      val useUniqueConstraint = col.unique && (!col.nullable || options.create.table.supportsNullUnique)
      if (useUniqueConstraint) {
        if (options.create.table.supportsUnique) buf.append(" unique")
      }

      if (col.hasCheck && options.create.table.supportsColumnCheck) {
        buf.append(" check (").append(col.check.get).append(")")
      }
      if (!options.comment.supportsCommentOn) {
        col.comment foreach { c =>
          buf.append(s" comment '$c'")
        }
      }
      if (iter.hasNext) buf.append(", ")
    }
    buf.append(')')
    if (!options.comment.supportsCommentOn) {
      table.comment foreach { c =>
        buf.append(s" comment '$c'")
      }
    }
    buf.toString
  }

  /** Table removal sql
   */
  override def dropTable(table: String): String = {
    Strings.replace(options.drop.table.sql, "{name}", table)
  }

  override def commentOnColumn(table: Table, column: Column, comment: Option[String]): Option[String] = {
    if (options.comment.supportsCommentOn) {
      Some("comment on column " + table.qualifiedName + '.' + column.name.toLiteral(table.engine) + " is '" + comment.getOrElse("") + "'")
    } else {
      None
    }
  }

  override def commentOnTable(table: String, comment: Option[String]): Option[String] = {
    if (options.comment.supportsCommentOn) {
      Some("comment on table " + table + " is '" + comment.getOrElse("") + "'")
    } else {
      None
    }
  }

  override def commentsOnTable(table: Table, includeMissing: Boolean): List[String] = {
    if (options.comment.supportsCommentOn) {
      val comments = Collections.newBuffer[String]
      val tableName = table.qualifiedName
      if (includeMissing) {
        comments += ("comment on table " + tableName + " is '" + table.comment.getOrElse(s"${tableName}?") + "'")
        table.columns foreach { c =>
          comments += ("comment on column " + tableName + '.' + c.name + " is '" + c.comment.getOrElse(s"${c.name}?") + "'")
        }
      } else {
        table.comment foreach { c =>
          comments += ("comment on table " + tableName + " is '" + c + "'")
        }
        table.columns foreach { c =>
          c.comment foreach { cc =>
            comments += ("comment on column " + tableName + '.' + c.name + " is '" + cc + "'")
          }
        }
      }
      comments.toList
    } else {
      List.empty
    }
  }

  override def alterTableAddColumn(table: Table, col: Column): List[String] = {
    val buf = Collections.newBuffer[String]
    var sql = s"alter table ${table.qualifiedName} add column ${col.name} ${col.sqlType.name}"
    col.defaultValue foreach { v =>
      if (col.sqlType.isStringType) {
        sql += s" default '$v''"
      } else {
        sql += s" default $v"
      }
    }
    if (options.create.table.supportsColumnCheck) {
      col.check foreach { c =>
        sql += s" check $c"
      }
    }
    buf += sql
    if (!col.nullable) {
      buf += alterTableModifyColumnSetNotNull(table, col)
    }
    buf.toList
  }

  override def alterTableRenameColumn(table: Table, col: Column, newName: String): String = {
    var renameClause = options.alter.table.renameColumn
    renameClause = Strings.replace(renameClause, "{oldcolumn}", col.name.toLiteral(table.engine))
    renameClause = Strings.replace(renameClause, "{newcolumn}", newName)
    renameClause = Strings.replace(renameClause, "{type}", col.sqlType.name)
    s"alter table ${table.qualifiedName} $renameClause"
  }

  override def alterTableDropColumn(table: Table, col: Column): String = {
    var alterClause = options.alter.table.dropColumn
    alterClause = Strings.replace(alterClause, "{column}", col.name.toLiteral(table.engine))
    s"alter table ${table.qualifiedName} $alterClause"
  }

  override def alterTableModifyColumnSetNotNull(table: Table, col: Column): String = {
    var alterClause = options.alter.table.setNotNull
    alterClause = Strings.replace(alterClause, "{column}", col.name.toLiteral(table.engine))
    alterClause = Strings.replace(alterClause, "{type}", col.sqlType.name)
    s"alter table ${table.qualifiedName} $alterClause"
  }

  override def alterTableModifyColumnDropNotNull(table: Table, col: Column): String = {
    var alterClause = options.alter.table.dropNotNull
    alterClause = Strings.replace(alterClause, "{column}", col.name.toLiteral(table.engine))
    alterClause = Strings.replace(alterClause, "{type}", col.sqlType.name)
    s"alter table ${table.qualifiedName} $alterClause"
  }

  override def alterTableModifyColumnDefault(table: Table, col: Column, v: Option[String]): String = {
    var alterClause = v match {
      case Some(_) => options.alter.table.setDefault
      case None => options.alter.table.dropNotNull
    }
    alterClause = Strings.replace(alterClause, "{column}", col.name.toLiteral(table.engine))
    var value = v.getOrElse("null")
    if (col.sqlType.isStringType) value = s"'$value'"
    alterClause = Strings.replace(alterClause, "{value}", value)
    s"alter table ${table.qualifiedName} $alterClause"
  }

  override def alterTableModifyColumnType(table: Table, col: Column, sqlType: SqlType): String = {
    var alterClause = options.alter.table.changeType
    alterClause = Strings.replace(alterClause, "{column}", col.name.toLiteral(table.engine))
    alterClause = Strings.replace(alterClause, "{type}", sqlType.name)
    s"alter table ${table.qualifiedName} $alterClause"
  }

  override def alterTableAddForeignKey(fk: ForeignKey): String = {
    require(null != fk.name && null != fk.table && null != fk.referencedTable)
    require(fk.referencedColumns.nonEmpty, " reference columns is empty.")
    require(fk.columns.nonEmpty, s"${fk.name} column's size should greate than 0")

    val engine = fk.table.engine
    val referencedColumnNames = fk.referencedColumns.map(x => x.toLiteral(engine)).toList
    val result = "alter table " + fk.table.qualifiedName + foreignKeySql(fk.literalName, fk.columnNames,
      fk.referencedTable.qualifiedName, referencedColumnNames)

    if (fk.cascadeDelete && options.constraint.supportsCascadeDelete) result + " on delete cascade" else result
  }

  override def alterTableAddPrimaryKey(table: Table, pk: PrimaryKey): String = {
    var alterClause = options.alter.table.addPrimaryKey
    alterClause = Strings.replace(alterClause, "{name}", pk.name.toLiteral(table.engine))
    alterClause = Strings.replace(alterClause, "{column-list}", nameList(pk.columns, table.engine))
    s"alter table ${table.qualifiedName} $alterClause"
  }

  override def alterTableDropPrimaryKey(table: Table, pk: PrimaryKey): String = {
    this.alterTableDropConstraint(table, pk.name.toLiteral(table.engine))
  }

  override def alterTableDropConstraint(table: Table, name: String): String = {
    var alterClause = options.alter.table.dropConstraint
    alterClause = Strings.replace(alterClause, "{name}", name)
    s"alter table ${table.qualifiedName} $alterClause"
  }

  override def alterTableAddUnique(fk: UniqueKey): String = {
    require(null != fk.name && null != fk.table)
    require(fk.columns.nonEmpty, s"${fk.name} column's size should greate than 0")
    val engine = fk.table.engine
    "alter table " + fk.table.qualifiedName + " add constraint " + fk.literalName + " unique (" + nameList(fk.columns, engine) + ")"
  }

  override def limit(query: String, offset: Int, size: Int): (String, List[Int]) = {
    val hasOffset = offset > 0
    val limitOrMax = if (null == options.limit.offsetPattern) offset + size else size

    if (hasOffset) {
      val params = if (options.limit.bindInReverseOrder) List(limitOrMax, offset) else List(offset, limitOrMax)
      (Strings.replace(options.limit.offsetPattern, "{}", query), params)
    } else {
      (Strings.replace(options.limit.pattern, "{}", query), List(limitOrMax))
    }
  }

  override def createSequence(seq: Sequence): String = {
    if (!options.sequence.supports) return null
    var sql: String = options.sequence.createSql
    sql = sql.replace("{name}", seq.qualifiedName)
    sql = sql.replace("{start}", String.valueOf(seq.current + 1))
    sql = sql.replace("{increment}", String.valueOf(seq.increment))
    sql = sql.replace("{cache}", String.valueOf(seq.cache))
    sql = sql.replace("{cycle}", if (seq.cycle) "cycle" else "")
    sql
  }

  override def dropSequence(seq: Sequence): String = {
    if (!options.sequence.supports) return null
    options.sequence.dropSql.replace("{name}", seq.qualifiedName)
  }

  override def createIndex(i: Index): String = {
    val buf = new StringBuilder("create")
      .append(if (i.unique) " unique" else "")
      .append(" index ")
      .append(i.literalName)
      .append(" on ")
      .append(i.table.qualifiedName)
      .append(" (")
    val iter = i.columns.iterator
    while (iter.hasNext) {
      buf.append(iter.next())
      if (iter.hasNext) buf.append(", ")
    }
    buf.append(")")
    buf.toString
  }

  override def dropIndex(i: Index): String = {
    if (i.table.schema.name.value.length > 0) {
      "drop index " + i.table.schema.name.toString + "." + i.literalName
    } else {
      "drop index " + i.literalName
    }
  }

  override def insert(table: Table): String = {
    val sb = new StringBuilder("insert into ")
    sb ++= table.qualifiedName
    sb += '('
    sb ++= table.quotedColumnNames.mkString(",")
    sb ++= ") values("
    sb ++= ("?," * table.columns.size)
    sb.setCharAt(sb.length() - 1, ')')
    sb.mkString
  }

  override def query(table: Table): String = {
    val sb: StringBuilder = new StringBuilder()
    sb.append("select ")
    for (columnName <- table.quotedColumnNames) {
      sb.append(columnName).append(',')
    }
    sb.deleteCharAt(sb.length() - 1)
    sb.append(" from ").append(table.qualifiedName)
    sb.toString()
  }

  protected def foreignKeySql(constraintName: String, foreignKey: Iterable[String],
                              referencedTable: String, primaryKey: Iterable[String]): String = {
    val res: StringBuffer = new StringBuffer(30)
    res.append(" add constraint ").append(constraintName).append(" foreign key (")
      .append(Strings.join(foreignKey, ", ")).append(") references ").append(referencedTable)
    if (primaryKey.nonEmpty) {
      res.append(" (").append(Strings.join(primaryKey, ", ")).append(')')
    }
    res.toString
  }

  private def nameList(seq: Iterable[Identifier], engine: Engine): String = {
    seq.map(_.toLiteral(engine)).mkString(",")
  }

  override def supportSequence: Boolean = {
    null != options.sequence
  }
}
