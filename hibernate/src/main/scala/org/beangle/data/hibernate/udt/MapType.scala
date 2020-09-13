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
package org.beangle.data.hibernate.udt

import java.{util => ju}

import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.UserCollectionType

import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters.asJava

/**
  * Mutable Map Type
  */
class MapType extends UserCollectionType {
  type MMap = mutable.Map[Object, Object]

  override def instantiate(session: SharedSessionContractImplementor, persister: CollectionPersister): PersistentCollection = {
    new PersistentMap(session)
  }

  override def wrap(session: SharedSessionContractImplementor, collection: Object): PersistentCollection = {
    new PersistentMap(session, collection.asInstanceOf[MMap])
  }

  def getElementsIterator(collection: Object): ju.Iterator[Object] = {
    asJava(collection.asInstanceOf[MMap]).values().iterator()
  }

  def contains(collection: Object, entity: Object): Boolean = {
    collection.asInstanceOf[MMap].contains(entity)
  }

  def indexOf(collection: Object, entity: Object): Object = null

  def replaceElements(original: Object, target: Object, persister: CollectionPersister,
                      owner: Object, copyCache: ju.Map[_, _], session: SharedSessionContractImplementor): Unit = {
    val targetSeq = target.asInstanceOf[MMap]
    targetSeq.clear()
    targetSeq ++= original.asInstanceOf[MMap]
  }

  def instantiate(anticipatedSize: Int): Object = {
    new mutable.HashMap[Object, Object]
  }
}
