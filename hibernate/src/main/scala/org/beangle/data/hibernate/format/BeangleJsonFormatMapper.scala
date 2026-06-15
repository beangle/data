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

package org.beangle.data.hibernate.format

import org.beangle.commons.json.{Json, JsonArray, JsonObject, JsonValue}
import org.hibernate.`type`.format.AbstractJsonFormatMapper

import java.lang.reflect.Type

/** Hibernate JSON {@link org.hibernate.type.format.FormatMapper} backed by beangle-commons json. */
class BeangleJsonFormatMapper extends AbstractJsonFormatMapper {

  override protected def fromString[T](charSequence: CharSequence, `type`: Type): T = {
    val json = Json.parse(charSequence.toString)
    `type` match {
      case t if t == classOf[JsonObject] => json.asInstanceOf[JsonObject].asInstanceOf[T]
      case t if t == classOf[JsonArray] => json.asInstanceOf[JsonArray].asInstanceOf[T]
      case t if t == classOf[JsonValue] => json.asInstanceOf[JsonValue].asInstanceOf[T]
      case t if t == classOf[Json] => json.asInstanceOf[T]
      case other => throw new IllegalArgumentException(s"Unsupported JSON type: $other")
    }
  }

  override protected def toString[T](value: T, `type`: Type): String = {
    value match {
      case j: Json => j.toJson
      case other => throw new IllegalArgumentException(s"Unsupported JSON value: ${other.getClass}")
    }
  }
}
