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
package org.beangle.data.hibernate.udt

import java.{ util => ju }

import scala.collection.JavaConversions.asJavaIterator
import scala.collection.mutable.{ Buffer, ListBuffer }

import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.UserCollectionType

/**
 * Mutable Seq Type
 */
class SeqType extends UserCollectionType {

  override def instantiate(session: SessionImplementor, persister: CollectionPersister) = {
    new PersistentSeq(session)
  }

  override def wrap(session: SessionImplementor, collection: Object) = {
    new PersistentSeq(session, collection.asInstanceOf[Buffer[Object]])
  }

  override def getElementsIterator(collection: Object) = {
    asJavaIterator(collection.asInstanceOf[Buffer[_]].iterator)
  }

  override def contains(collection: Object, entity: Object) = {
    collection.asInstanceOf[Buffer[_]].contains(entity)
  }

  override def indexOf(collection: Object, entity: Object) = {
    Integer.valueOf(collection.asInstanceOf[Buffer[Object]].indexOf(entity))
  }

  override def replaceElements(original: Object, target: Object, persister: CollectionPersister, owner: Object, copyCache: ju.Map[_, _], session: SessionImplementor) = {
    val targetSeq = target.asInstanceOf[Buffer[Any]]
    targetSeq.clear()
    targetSeq ++= original.asInstanceOf[Seq[Any]]
  }

  override def instantiate(anticipatedSize: Int): Object = {
    new ListBuffer[Object]()
  }
}