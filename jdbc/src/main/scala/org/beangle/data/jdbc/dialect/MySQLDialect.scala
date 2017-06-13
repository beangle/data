/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
package org.beangle.data.jdbc.dialect

import java.sql.Types._
import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.meta.Engines

class MySQLDialect extends AbstractDialect(Engines.MySQL, "[5.0,)") {

  override def limitGrammar = new LimitGrammarBean("{} limit ?", "{} limit ? offset ?", true)

  override def foreignKeySql(constraintName: String, foreignKey: Iterable[String],
                             referencedTable: String, primaryKey: Iterable[String]) = {
    val cols = Strings.join(foreignKey, ", ")
    new StringBuffer(30).append(" add index ").append(constraintName).append(" (").append(cols)
      .append("), add constraInt ").append(constraintName).append(" foreign key (").append(cols)
      .append(") references ").append(referencedTable).append(" (")
      .append(Strings.join(primaryKey, ", ")).append(')').toString()
  }

  override def tableGrammar = {
    val bean = new TableGrammarBean()
    bean.columnComent = " comment '{}'"
    bean.tableComment = " comment '{}'"
    bean
  }

  override def sequenceGrammar = null
}
