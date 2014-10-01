package org.beangle.data.serializer.converter

import java.sql.{Date, Timestamp}
import java.text.SimpleDateFormat
import java.util.{Calendar, Date => juDate}

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext

class DateConverter extends Converter[juDate] {
  var format = new SimpleDateFormat("YYYY-MM-dd")

  override def marshal(source: juDate, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class SqlDateConverter extends Converter[Date] {
  var format = new SimpleDateFormat("YYYY-MM-dd")

  override def marshal(source: Date, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class CalendarConverter extends Converter[Calendar] {
  var format = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss")

  override def marshal(source: Calendar, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source.getTime))
  }
}

class TimestampConverter extends Converter[Timestamp] {
  var format = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss")

  override def marshal(source: Timestamp, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}