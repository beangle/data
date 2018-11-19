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
package org.beangle.data.transfer.exporter

import org.beangle.commons.lang.Strings

class SimpleItemExporter extends AbstractItemExporter {
  /** 导出属性对应的标题 */
  protected var titles: Array[String] = _

  protected override def beforeExport(): Boolean = {
    if (null == titles) {
      context.get("titles", classOf[Object]) foreach { t =>
        t match {
          case s: String        => titles = Strings.split(s, ",")
          case a: Array[String] => titles = a
        }
      }
    }
    if (null == titles || titles.length == 0) return false
    writer.writeTitle(null, titles)
    true
  }

}
