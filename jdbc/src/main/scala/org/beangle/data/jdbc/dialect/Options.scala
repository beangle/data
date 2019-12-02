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


object Options {

  class CreateOption {
    var table = new CreateTableOption
  }

  class AlterOption {
    var table = new AlterTableOption
  }

  class DropOption {
    var table = new DropTableOption
  }

  class AlterTableOption {
    var changeType="{}"
    var setNotNull:String=_
    var dropNotNull:String=_
    var setDefault:String=_
    var dropDefault:String=_
  }

  class CreateTableOption {
    var supportsNullUnique = false
    var supportsUnique = true
    var supportsColumnCheck = true
  }

  class CommentOption {
    var supportsCommentOn = true
  }

  class DropTableOption {
    var sql = "drop table {}"
  }

  class ConstraintOption {
    var supportsCascadeDelete = true
  }

  class LimitOption {
    var pattern: String = _
    var offsetPattern: String = _
    var bindInReverseOrder: Boolean = _
  }

  class SequenceOption {
    var supports = true
    var createSql: String = "create sequence :name start with :start increment by :increment :cycle"
    var dropSql: String = "drop sequence :name"
    var nextValSql: String = _
    var selectNextValSql: String = _
  }

}

class Options {

  val create = new Options.CreateOption

  val alter = new Options.AlterOption

  val drop = new Options.DropOption

  var comment = new Options.CommentOption

  var constraint = new Options.ConstraintOption

  var limit = new Options.LimitOption

  var sequence = new Options.SequenceOption

}
