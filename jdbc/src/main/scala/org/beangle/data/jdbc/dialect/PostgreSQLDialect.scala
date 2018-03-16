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
package org.beangle.data.jdbc.dialect

import java.sql.Types._
import org.beangle.data.jdbc.meta.Engines

class PostgreSQLDialect extends AbstractDialect(Engines.PostgreSQL, "[8.4)") {

  override def sequenceGrammar = {
    val ss = new SequenceGrammar()
    ss.querySequenceSql = "select sequence_name,start_value,increment increment_by,cycle_option cycle_flag" +
      " from information_schema.sequences where sequence_schema=':schema'"
    ss.nextValSql = "select nextval (':name')"
    ss.selectNextValSql = "nextval (':name')"
    ss
  }

  override def limitGrammar = new LimitGrammarBean("{} limit ?", "{} limit ? offset ?", true)

  override def tableGrammar = {
    val bean = new TableGrammarBean()
    bean.dropSql = "drop table {} cascade"
    bean
  }

  override def defaultSchema: String = {
    "public"
  }

  override def supportsCommentOn = true
}
