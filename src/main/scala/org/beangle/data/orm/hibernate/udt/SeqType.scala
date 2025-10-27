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
 * Mutable Seq Type
 */
class SeqType extends ScalaCollectionType {

  override def instantiate(session: SharedSessionContractImplementor, persister: CollectionPersister): PersistentCollection[_] = {
    new ScalaPersistentSeq(session)
  }

  override def wrap(session: SharedSessionContractImplementor, collection: Object): PersistentCollection[_] = {
    new ScalaPersistentSeq(session, collection.asInstanceOf[mutable.Buffer[Object]])
  }

  override def contains(collection: Object, entity: Object): Boolean = {
    collection.asInstanceOf[mutable.Buffer[_]].contains(entity)
  }

  override def indexOf(collection: Object, entity: Object): Integer = {
    Integer.valueOf(collection.asInstanceOf[mutable.Buffer[Object]].indexOf(entity))
  }

  override def instantiate(anticipatedSize: Int): Object = {
    new mutable.ArrayBuffer(anticipatedSize)
  }

  override def getCollectionClass: Class[_] = classOf[mutable.Buffer[_]]

  override def getClassification: CollectionClassification = CollectionClassification.LIST
}
