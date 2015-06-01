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

