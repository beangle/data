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

import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.metamodel.CollectionClassification
import org.hibernate.persister.collection.CollectionPersister

import scala.collection.mutable

/**
 * Mutable Set Type
 */
class SetType extends ScalaCollectionType {
  type MSet = mutable.Set[Object]

  override def instantiate(session: SharedSessionContractImplementor, persister: CollectionPersister): PersistentCollection[_] = {
    new ScalaPersistentSet(session)
  }

  override def wrap(session: SharedSessionContractImplementor, collection: Object): PersistentCollection[_] = {
    new ScalaPersistentSet(session, collection.asInstanceOf[MSet])
  }

  override def contains(collection: Object, entity: Object): Boolean = {
    collection.asInstanceOf[MSet].contains(entity)
  }

  override def instantiate(anticipatedSize: Int): Object = {
    new mutable.HashSet[Object]
  }

  override def getCollectionClass: Class[_] = classOf[MSet]

  override def getClassification: CollectionClassification = CollectionClassification.SET
}
