package org.beangle.data.serialize.marshal

import java.sql.{ Date, Time, Timestamp }
import java.text.{ DateFormat, SimpleDateFormat }
import java.util.{ Calendar, Date => juDate }

import org.beangle.commons.lang.time.UTCFormat
import org.beangle.data.serialize.io.StreamWriter

object DateFormats {
  val Date = new SimpleDateFormat("YYYY-MM-dd")
  val Datetime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  Datetime.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
  val Time = new SimpleDateFormat("HH:mm:ss")
}

class DateMarshaller(val format: DateFormat = UTCFormat) extends Marshaller[juDate] {
  override def marshal(source: juDate, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class SqlDateMarshaller(val format: SimpleDateFormat = DateFormats.Date) extends Marshaller[Date] {
  override def marshal(source: Date, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class CalendarMarshaller(val format: DateFormat = UTCFormat) extends Marshaller[Calendar] {
  override def marshal(source: Calendar, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source.getTime))
  }
}

class TimestampMarshaller(val format: DateFormat = UTCFormat) extends Marshaller[Timestamp] {
  override def marshal(source: Timestamp, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class TimeMarshaller(val format: SimpleDateFormat = DateFormats.Time) extends Marshaller[Time] {
  override def marshal(source: Time, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

