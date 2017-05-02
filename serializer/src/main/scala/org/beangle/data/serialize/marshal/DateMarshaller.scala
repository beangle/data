/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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

import org.beangle.commons.lang.time.DateFormats
import org.beangle.data.serialize.io.StreamWriter

class DateMarshaller(val format: DateFormat = DateFormats.UTC) extends Marshaller[juDate] {
  override def marshal(source: juDate, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class SqlDateMarshaller(val format: SimpleDateFormat = new SimpleDateFormat("YYYY-MM-dd")) extends Marshaller[Date] {
  override def marshal(source: Date, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class CalendarMarshaller(val format: DateFormat = DateFormats.UTC) extends Marshaller[Calendar] {
  override def marshal(source: Calendar, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source.getTime))
  }
}

class TimestampMarshaller(val format: DateFormat = DateFormats.UTC) extends Marshaller[Timestamp] {
  override def marshal(source: Timestamp, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}

class TimeMarshaller(val format: SimpleDateFormat = new SimpleDateFormat("HH:mm:ss")) extends Marshaller[Time] {
  override def marshal(source: Time, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(format.format(source))
  }
}
