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

package org.beangle.data.orm.cfg

import org.beangle.commons.lang.Strings.{isEmpty, isNotEmpty}
import org.beangle.data.orm.NamingPolicy

class MappingProfile {
  var packageName: String = _
  var naming: NamingPolicy = _
  var _schema: Option[String] = None
  var _prefix: Option[String] = None
  var parent: Option[MappingProfile] = None
  var abbrs: Map[String, String] = Map.empty

  def schema: Option[String] = {
    if (_schema.nonEmpty) _schema
    else parent.flatMap(_.schema)
  }

  def prefix: String = {
    _prefix match {
      case Some(p) => p
      case None => parent.map(_.prefix).getOrElse("")
    }
  }

  val _annotations = new collection.mutable.ListBuffer[AnnotationModule]

  def annotations: collection.Seq[AnnotationModule] = {
    if (_annotations.isEmpty && parent.nonEmpty) parent.get._annotations
    else _annotations
  }

  def getSchema(clazz: Class[?]): Option[String] = {
    val anno = this.annotations find { ann =>
      clazz.getAnnotations exists { annon =>
        if (ann.clazz.isAssignableFrom(annon.getClass)) {
          if (isNotEmpty(ann.value)) {
            try {
              val method = annon.getClass.getMethod("value")
              String.valueOf(method.invoke(annon)) == ann.value
            } catch {
              case _: Throwable =>
                Console.err.print("Annotation value needed:", ann.value, annon.getClass)
                false
            }
          } else true
        } else false
      }
    }
    anno match {
      case None => this.schema
      case Some(a) => if (isNotEmpty(a.schema)) Some(a.schema) else this.schema
    }
  }

  def getPrefix(clazz: Class[_]): String = {
    var prefix = this.prefix
    val anno = this.annotations find { ann =>
      clazz.getAnnotations exists { annon =>
        if (ann.clazz.isAssignableFrom(annon.getClass)) {
          if (isNotEmpty(ann.value)) {
            try {
              val method = annon.getClass.getMethod("value")
              String.valueOf(method.invoke(annon)) == ann.value
            } catch {
              case _: Exception =>
                Console.err.print("Annotation value needed:", ann.value, annon.getClass)
                false
            }
          } else true
        } else false
      }
    }
    anno foreach (an => if (isNotEmpty(an.prefix)) prefix = an.prefix)
    if (isEmpty(prefix)) "" else prefix
  }

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("[package:").append(packageName).append(", schema:").append(_schema.getOrElse(""))
    sb.append(", prefix:").append(_prefix.getOrElse("")).append(']')
    sb.toString()
  }
}

class AnnotationModule(val clazz: Class[_], val value: String) {
  var schema: String = _
  var prefix: String = _
}
