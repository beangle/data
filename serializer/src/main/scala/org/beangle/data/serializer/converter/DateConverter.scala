package org.beangle.data.serializer.converter

import java.sql.{ Date, Timestamp }
import java.text.SimpleDateFormat
import java.util.{ Calendar, Date => juDate }
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext
import java.sql.Time

object DateFormat {
  val Date = new SimpleDateFormat("YYYY-MM-dd")
  val Datetime = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss")
  val Time = new SimpleDateFormat("HH:mm:ss")
}

class DateConverter(val format: SimpleDateFormat = DateFormat.Datetime) extends Converter[juDate] {
  override def marshal(source: juDate, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class SqlDateConverter(val format: SimpleDateFormat = DateFormat.Date) extends Converter[Date] {
  override def marshal(source: Date, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class CalendarConverter(val format: SimpleDateFormat = DateFormat.Datetime) extends Converter[Calendar] {
  override def marshal(source: Calendar, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source.getTime))
  }
}

class TimestampConverter(val format: SimpleDateFormat = DateFormat.Datetime) extends Converter[Timestamp] {
  override def marshal(source: Timestamp, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class TimeConverter(val format: SimpleDateFormat = DateFormat.Time) extends Converter[Time] {
  override def marshal(source: Time, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}