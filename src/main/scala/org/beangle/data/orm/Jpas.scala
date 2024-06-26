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

import org.beangle.commons.bean.ProxyResolver
import org.beangle.commons.lang.Strings
import org.beangle.data.model.{Component, Entity}

import java.lang.annotation.Annotation

object Jpas {

  var proxyResolver: ProxyResolver = ProxyResolver.Null

  val JpaEntityAnn = Class.forName("jakarta.persistence.Entity").asInstanceOf[Class[Annotation]]

  val JpaComponentAnn = Class.forName("jakarta.persistence.Embeddable").asInstanceOf[Class[Annotation]]

  private[this] val NameMethodOnEntity = JpaEntityAnn.getMethod("name")

  def findEntityName(clazz: Class[_]): String = {
    val annotation = clazz.getAnnotation(JpaEntityAnn)
    if (null != annotation) {
      val name = NameMethodOnEntity.invoke(annotation).asInstanceOf[String]
      if (Strings.isNotBlank(name)) name else clazz.getName
    } else {
      clazz.getName
    }
  }

  def isSeq(clazz: Class[_]): Boolean = {
    classOf[collection.mutable.Seq[_]].isAssignableFrom(clazz) || classOf[java.util.List[_]].isAssignableFrom(clazz)
  }

  def isSet(clazz: Class[_]): Boolean = {
    classOf[collection.mutable.Set[_]].isAssignableFrom(clazz) || classOf[java.util.Set[_]].isAssignableFrom(clazz)
  }

  def isMap(clazz: Class[_]): Boolean = {
    classOf[collection.mutable.Map[_, _]].isAssignableFrom(clazz) || classOf[java.util.Map[_, _]].isAssignableFrom(clazz)
  }

  def isEntity(clazz: Class[_]): Boolean = {
    classOf[Entity[_]].isAssignableFrom(clazz) || null != clazz.getAnnotation(JpaEntityAnn)
  }

  def isComponent(clazz: Class[_]): Boolean = {
    classOf[Component].isAssignableFrom(clazz) ||
      null != clazz.getAnnotation(classOf[org.beangle.commons.bean.component]) ||
      null != clazz.getAnnotation(JpaComponentAnn)
  }

  def entityClass(entity: AnyRef): Class[_] = {
    proxyResolver.targetClass(entity)
  }
}
