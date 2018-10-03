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
package org.beangle.data.transfer.excel

import java.io.OutputStream
import java.net.URL

import org.beangle.data.transfer.Format
import org.beangle.data.transfer.exporter.Context
import org.beangle.data.transfer.io.Writer
import org.jxls.util.JxlsHelper

class ExcelTemplateWriter(val template: URL, os: OutputStream) extends Writer {

  var context: Context = _

  this.outputStream = os

  /**
   * write.
   */
  def write() {
    val ctx = new org.jxls.common.Context()
    context.datas foreach {
      case (k, v) =>
        ctx.putVar(k, v)
    }
    JxlsHelper.getInstance().processTemplate(template.openStream(), outputStream, ctx)
  }

  override def format: Format.Value = {
    Format.Xls
  }

  override def close() {

  }
}
