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

import org.beangle.commons.collection.Collections
import org.beangle.data.transfer.Format

class ExportContext {
  val datas:collection.mutable.Map[String,Any] = Collections.newMap[String, Any]

  var format: Format.Value = _

  var extractor: PropertyExtractor = new DefaultPropertyExtractor()

  def get[T](key: String, clazz: Class[T]): Option[T] = {
    datas.get(key).asInstanceOf[Option[T]]
  }

  def put(key: String, v: Any): Unit =  {
    datas.put(key, v)
  }
}
