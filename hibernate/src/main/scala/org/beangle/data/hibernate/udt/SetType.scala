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

import scala.collection.JavaConverters
import scala.collection.mutable

import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.UserCollectionType
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.collection.spi.PersistentCollection

/**
 * Mutable Set Type
 */
class SetType extends UserCollectionType {
  type MSet = mutable.Set[Object]

  override def instantiate(session: SharedSessionContractImplementor, persister: CollectionPersister): PersistentCollection = {
    new PersistentSet(session)
  }

  override def wrap(session: SharedSessionContractImplementor, collection: Object): PersistentCollection = {
    new PersistentSet(session, collection.asInstanceOf[MSet])
  }

  override def getElementsIterator(collection: Object) = {
    JavaConverters.asJavaIterator(collection.asInstanceOf[MSet].iterator)
  }

  override def contains(collection: Object, entity: Object) = {
    collection.asInstanceOf[MSet].contains(entity)
  }

  override def indexOf(collection: Object, entity: Object): Object = null

  override def replaceElements(original: Object, target: Object, persister: CollectionPersister,
                               owner: Object, copyCache: ju.Map[_, _], session: SharedSessionContractImplementor) = {
    val targetSeq = target.asInstanceOf[MSet]
    targetSeq.clear()
    targetSeq ++= original.asInstanceOf[Seq[Object]]
  }

  override def instantiate(anticipatedSize: Int): Object = {
    new mutable.HashSet[Object]
  }
}
