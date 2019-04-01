package org.beangle.data.transfer.excel
import org.beangle.data.transfer.exporter.{ Exporter, ExportContext }
import org.beangle.data.transfer.io.Writer

class ExcelTemplateExporter extends Exporter {

  def export(context: ExportContext, writer: Writer): Unit = {
    val templateWriter = writer.asInstanceOf[ExcelTemplateWriter]
    templateWriter.write()
  }
}