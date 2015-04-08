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
package org.beangle.data.jpa.hibernate.udt

import java.{ util => ju }

import scala.collection.JavaConversions.asJavaIterator
import scala.collection.mutable

import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.UserCollectionType

/**
 * Mutable Map Type
 */
class MapType extends UserCollectionType {
  type MMap = mutable.Map[Object, Object]

  def instantiate(session: SessionImplementor, persister: CollectionPersister) = {
    new PersistentMap(session)
  }

  def wrap(session: SessionImplementor, collection: Object) = {
    new PersistentMap(session, collection.asInstanceOf[MMap])
  }
  def getElementsIterator(collection: Object) = {
    asJavaIterator(collection.asInstanceOf[MMap].iterator)
  }

  def contains(collection: Object, entity: Object) = {
    collection.asInstanceOf[MMap].contains(entity)
  }

  def indexOf(collection: Object, entity: Object): Object = null

  def replaceElements(original: Object, target: Object, persister: CollectionPersister, owner: Object, copyCache: ju.Map[_, _], session: SessionImplementor) = {
    val targetSeq = target.asInstanceOf[MMap]
    targetSeq.clear()
    targetSeq ++= original.asInstanceOf[MMap]
  }

  def instantiate(anticipatedSize: Int): Object = {
    new mutable.HashMap[Object, Object]
  }
}