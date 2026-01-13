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

package org.beangle.data.orm.hibernate.udt

import org.beangle.commons.json.{Json, JsonArray, JsonObject, JsonValue}
import org.hibernate.`type`.descriptor.WrapperOptions
import org.hibernate.`type`.descriptor.java.{AbstractClassJavaType, JavaType}

/** 转换String到Json
 *
 * @param jsonType
 */
class JsonType[T <: Json](jsonType: Class[T]) extends AbstractClassJavaType[T](jsonType) {

  override def unwrap[X](value: T, valueType: Class[X], options: WrapperOptions): X = {
    if (value eq null) null.asInstanceOf[X]
    else {
      if valueType == classOf[Json] then
        value.asInstanceOf[X]
      else if valueType == classOf[String] then
        value.asInstanceOf[Json].toJson.asInstanceOf[X]
      else
        throw unknownUnwrap(valueType)
    }
  }

  override def wrap[X](value: X, options: WrapperOptions): T = {
    value match {
      case null =>
        if (jsonType == classOf[JsonObject]) {
          Json.emptyObject.asInstanceOf[T]
        } else if (jsonType == classOf[JsonArray]) {
          Json.emptyArray.asInstanceOf[T]
        } else {
          JsonValue("").asInstanceOf[T]
        }
      case s: String => Json.parse(s).asInstanceOf[T]
      case _ => throw new RuntimeException(s"Cannot support convert from ${value.getClass} to Json")
    }
  }

  override def isWider(javaType: JavaType[_]): Boolean = {
    javaType.getJavaType.getTypeName == "java.lang.String"
  }
}
