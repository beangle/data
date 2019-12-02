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

import org.beangle.data.jdbc.engine.Engines

class HSQLDialect extends AbstractDialect(Engines.HSQL) {

  options.sequence.nextValSql = "call next value for :name"
  options.sequence.selectNextValSql = "next value for :name"
  options.sequence.createSql = "create sequence :name start with :start increment by :increment"
  options.sequence.dropSql = "drop sequence if exists :name"

  options.limit.pattern="{} limit ?"
  options.limit.offsetPattern="{} limit ? offset ?"
  options.limit.bindInReverseOrder=true

  options.comment.supportsCommentOn=true
}
