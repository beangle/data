/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.model.meta

import org.beangle.commons.bean.Properties
import org.beangle.commons.collection.Collections
import org.beangle.data.model.Entity

object Domain {

  final class SimpleProperty(val name: String, val clazz: Class[_], val optional: Boolean) extends SingularProperty {
    val propertyType = new BasicType(clazz)
  }
}

trait Domain {

  def getEntity(clazz: Class[_]): Option[EntityType] = {
    getEntity(clazz.getName)
  }

  def getEntity(name: String): Option[EntityType]

  def entities: Map[String, EntityType]

  def newInstance[T <: Entity[_]](entityClass: Class[T]): Option[T] = {
    getEntity(entityClass) match {
      case Some(t) => Some(t.newInstance().asInstanceOf[T])
      case _ => None
    }
  }

  def newInstance[T <: Entity[ID], ID](entityClass: Class[T], id: ID): Option[T] = {
    getEntity(entityClass) match {
      case Some(t) =>
        val obj = t.newInstance()
        Properties.set(obj, t.id.name, id)
        Some(obj.asInstanceOf[T])
      case _ => None
    }
  }

}

object ImmutableDomain {
  private def buildEntityMap(entities: Iterable[EntityType]): Map[String, EntityType] = {
    val builder = new collection.mutable.HashMap[String, EntityType]
    for (entity <- entities) {
      builder.put(entity.entityName, entity)
      builder.put(entity.clazz.getName, entity)
    }
    builder.toMap
  }

  def apply(entities: Iterable[EntityType]): Domain = {
    new ImmutableDomain(buildEntityMap(entities))
  }

  def empty: Domain = {
    new ImmutableDomain(Map.empty[String, EntityType])
  }
}

class ImmutableDomain(val entities: Map[String, EntityType]) extends Domain {
  override def getEntity(name: String): Option[EntityType] = {
    entities.get(name)
  }
}
