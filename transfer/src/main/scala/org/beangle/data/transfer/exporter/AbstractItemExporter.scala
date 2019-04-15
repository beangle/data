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

import org.beangle.commons.logging.Logging
import org.beangle.data.transfer.io.{ ItemWriter, Writer }

abstract class AbstractItemExporter extends Exporter with Logging {
  var current: Any = _
  var context: ExportContext = _
  var writer: ItemWriter = _

  override def export(context: ExportContext, writer: Writer) {
    this.context = context
    this.writer = writer.asInstanceOf[ItemWriter]
    var index = -1
    var iter: Iterator[Any] = null
    val items = context.datas.get("items").orNull.asInstanceOf[Iterable[Any]]
    if (null != items) {
      iter = items.iterator
    }
    if (null != iter && !beforeExport()) return
    while (iter.hasNext) {
      index += 1
      current = iter.next()
      exportItem()
    }
    writer.close()
  }

  protected def beforeExport(): Boolean = {
    true
  }

  def exportItem() {
    if (null == current) return
    writer.write(current)
  }

}
