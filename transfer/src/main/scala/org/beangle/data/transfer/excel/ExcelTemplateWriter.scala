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

package org.beangle.data.transfer.excel

import java.io.OutputStream
import java.net.URL

import org.beangle.data.transfer.Format
import org.beangle.data.transfer.exporter.ExportContext
import org.beangle.data.transfer.io.Writer
import org.jxls.util.JxlsHelper
import scala.jdk.javaapi.CollectionConverters.asJava
class ExcelTemplateWriter(val template: URL, val context: ExportContext, val outputStream: OutputStream)
  extends Writer {

  /**
    * write.
    */
  def write(): Unit = {
    val ctx = new org.jxls.common.Context()
    context.datas foreach {
      case (k, v) =>
        val nv = v match {
          case i: Iterable[_] => asJava(i)
          case _ => v
        }
        ctx.putVar(k, nv)
    }
    JxlsHelper.getInstance().processTemplate(template.openStream(), outputStream, ctx)
  }

  override def format: Format = {
    Format.Xls
  }

  override def close(): Unit = {

  }
}
