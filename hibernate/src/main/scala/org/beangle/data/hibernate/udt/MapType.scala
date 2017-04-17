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
package org.beangle.data.hibernate.udt

import java.{ util => ju }

import scala.collection.{ JavaConverters, mutable }

import org.hibernate.engine.spi.{ SessionImplementor, SharedSessionContractImplementor }
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.UserCollectionType
import org.hibernate.collection.internal.PersistentMap
import org.hibernate.collection.spi.PersistentCollection

/**
 * Mutable Map Type
 */
class MapType extends UserCollectionType {
  type MMap = mutable.Map[Object, Object]

  override def instantiate(session: SharedSessionContractImplementor, persister: CollectionPersister): PersistentCollection = {
    //new PersistentMap(session)
    null.asInstanceOf[PersistentCollection]
  }

  override def wrap(session: SharedSessionContractImplementor, collection: Object): PersistentCollection = {
    //new PersistentMap(session, collection.asInstanceOf[MMap])
    null.asInstanceOf[PersistentCollection]
  }
  def getElementsIterator(collection: Object) = {
    JavaConverters.asJavaIterator(collection.asInstanceOf[MMap].iterator)
  }

  def contains(collection: Object, entity: Object) = {
    collection.asInstanceOf[MMap].contains(entity)
  }

  def indexOf(collection: Object, entity: Object): Object = null

  def replaceElements(original: Object, target: Object, persister: CollectionPersister,
                      owner: Object, copyCache: ju.Map[_, _], session: SharedSessionContractImplementor) = {
    val targetSeq = target.asInstanceOf[MMap]
    targetSeq.clear()
    targetSeq ++= original.asInstanceOf[MMap]
  }

  def instantiate(anticipatedSize: Int): Object = {
    new mutable.HashMap[Object, Object]
  }
}
