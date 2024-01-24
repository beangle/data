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

package org.beangle.data.jdbc.meta

class View(s: Schema, n: Identifier) extends Relation(s, n) {

  override def clone(newschema: Schema): View = {
    val t = this.clone()
    val oldSchema = t.schema
    t.schema = newschema
    t.attach(t.engine)
    t
  }

  override def clone(): View = {
    val tb = new View(schema, name)
    tb.comment = comment
    tb.module = module
    for (col <- columns) tb.add(col.clone())
    tb
  }

  def toTable: Table = {
    val tb = new Table(schema, name)
    tb.comment = comment
    tb.module = module
    for (col <- columns) tb.add(col.clone())
    tb
  }

}
