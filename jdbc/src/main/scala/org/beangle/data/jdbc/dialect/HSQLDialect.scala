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

import org.beangle.data.jdbc.meta.Engines

class HSQLDialect extends AbstractDialect(Engines.HSQL, "[2.0.0,)") {

  override def sequenceGrammar = {
    val ss = new SequenceGrammar()
    ss.querySequenceSql = "select sequence_name,next_value,increment from information_schema.sequences where sequence_schema=':schema'"
    ss.nextValSql = "call next value for :name"
    ss.selectNextValSql = "next value for :name"
    ss.createSql = "create sequence :name start with :start increment by :increment"
    ss.dropSql = "drop sequence if exists :name"
    ss
  }

  override def limitGrammar: LimitGrammar = {
    new LimitGrammarBean("{} limit ?", "{} offset ? limit ?", false)
  }

  override def tableGrammar: TableGrammar = {
    val bean = new TableGrammarBean()
    bean.columnComent = " comment '{}'"
    bean
  }

  override def defaultSchema: String = {
    "PUBLIC"
  }

  override def supportsCommentOn = true

}
