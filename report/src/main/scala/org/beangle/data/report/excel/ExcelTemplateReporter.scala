/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.report.excel

import java.io.OutputStream
import java.net.URL
import net.sf.jxls.transformer.XLSTransformer
import org.beangle.data.report.Reporter
import org.beangle.data.report.ReportContext

/**
 * ExcelTemplateReporter class.
 *
 * @author chaostone
 */
class ExcelTemplateReporter(template: URL) extends Reporter {

  protected var transformer = new XLSTransformer()

  override def generate(context: ReportContext, os: OutputStream): Unit = {
    import scala.collection.JavaConversions.mapAsJavaMap
    val workbook = transformer.transformXLS(template.openStream(), context.datas)
    workbook.write(os)
  }

}