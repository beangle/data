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

package org.beangle.data.transfer.exporter

import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging

import scala.collection.mutable.ArrayBuffer

class SimpleEntityExporter extends SimpleItemExporter with Logging {

  var attrs: Array[String] = _

  protected override def beforeExport(): Boolean = {
    this.attrs = context.attrs
    this.titles = context.titles
    super.beforeExport()
  }

  /**
   * 转换单个实体
   */
  override def exportItem(): Unit = {
    if (null == attrs || attrs.length == 0) {
      super.exportItem()
    } else {
      val values = new Array[Any](attrs.length)
      values.indices foreach { i =>
        try {
          values(i) = context.getPropertyValue(current.asInstanceOf[AnyRef], attrs(i))
        } catch {
          case e: Exception => logger.error("occur in get property :" + attrs(i), e)
        }
      }
      writer.write(values)
    }
  }

}
