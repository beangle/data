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

package org.beangle.data.orm

import jakarta.persistence.{Embeddable, Entity as JpaEntity}
import org.beangle.commons.bean.ProxyResolver
import org.beangle.commons.lang.Strings
import org.beangle.data.model.{Component, Entity}

import java.lang.annotation.Annotation

object Jpas {

  var proxyResolver: ProxyResolver = ProxyResolver.Null

  def findEntityName(clazz: Class[?]): String = {
    val annotation = clazz.getAnnotation(classOf[JpaEntity])
    if (null != annotation) {
      val name = annotation.name()
      if (Strings.isNotBlank(name)) name else clazz.getName
    } else {
      clazz.getName
    }
  }

  def isSeq(clazz: Class[?]): Boolean = {
    classOf[collection.mutable.Seq[?]].isAssignableFrom(clazz) || classOf[java.util.List[?]].isAssignableFrom(clazz)
  }

  def isSet(clazz: Class[?]): Boolean = {
    classOf[collection.mutable.Set[?]].isAssignableFrom(clazz) || classOf[java.util.Set[?]].isAssignableFrom(clazz)
  }

  def isMap(clazz: Class[?]): Boolean = {
    classOf[collection.mutable.Map[?, ?]].isAssignableFrom(clazz) || classOf[java.util.Map[?, ?]].isAssignableFrom(clazz)
  }

  def isEntity(clazz: Class[?]): Boolean = {
    classOf[Entity[?]].isAssignableFrom(clazz) || null != clazz.getAnnotation(classOf[JpaEntity])
  }

  def isComponent(clazz: Class[?]): Boolean = {
    classOf[Component].isAssignableFrom(clazz) ||
      null != clazz.getAnnotation(classOf[org.beangle.commons.bean.component]) ||
      null != clazz.getAnnotation(classOf[Embeddable])
  }

  def entityClass(entity: AnyRef): Class[?] = {
    proxyResolver.targetClass(entity)
  }
}
