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

import org.hibernate.`type`.{BasicType, CollectionType, Type}
import org.hibernate.collection.spi.AbstractPersistentCollection
import org.hibernate.engine.internal.ForeignKeys
import org.hibernate.engine.spi.{SharedSessionContractImplementor, Status, TypedValue}
import org.hibernate.internal.util.collections.IdentitySet
import org.hibernate.persister.collection.CollectionPersister

import java.util as ju
import java.util.UUID
import scala.annotation.nowarn
import scala.jdk.javaapi.CollectionConverters.asJava

private[udt] object PersistentHelper {

  @inline
  @nowarn
  def getElementType(persister: CollectionPersister): Type = {
    persister.getElementType
  }

  @inline
  @nowarn
  def getCollectionType(persister: CollectionPersister): CollectionType = {
    persister.getCollectionType
  }

  @inline
  @nowarn
  def getIndexType(persister: CollectionPersister): Type = {
    persister.getIndexType
  }

  def getOrphans(oldElements: Iterable[Object], currentElements: Iterable[Object], entityName: String, session: SharedSessionContractImplementor): ju.Collection[Object] = {
    // short-circuit(s)
    if (currentElements.isEmpty) return asJava(oldElements.toSeq)
    if (oldElements.isEmpty) return ju.Collections.emptyList()

    val idType = session.getFactory.getMappingMetamodel.getEntityDescriptor(entityName).getIdentifierType
    val useIdDirect = mayUseIdDirect(idType)
    // create the collection holding the Orphans
    val res = new ju.ArrayList[Object]

    // collect EntityIdentifier(s) of the *current* elements - add them into a HashSet for fast access
    val currentIds = new ju.HashSet[Object]
    val currentSaving = new IdentitySet[Object]
    val persistenceContext = session.getPersistenceContextInternal
    currentElements foreach { current =>
      if (current != null && ForeignKeys.isNotTransient(entityName, current, null, session)) {
        val ee = persistenceContext.getEntry(current)
        if (ee != null && ee.getStatus == Status.SAVING) {
          currentSaving.add(current)
        } else {
          val currentId = ForeignKeys.getEntityIdentifierIfNotUnsaved(entityName, current, session)
          currentIds.add(if useIdDirect then currentId else new TypedValue(idType, currentId))
        }
      }
    }

    // iterate over the *old* list
    oldElements foreach { old =>
      if (!currentSaving.contains(old)) {
        val oldId = ForeignKeys.getEntityIdentifierIfNotUnsaved(entityName, old, session)
        if (oldId != null && !currentIds.contains(if useIdDirect then oldId else new TypedValue(idType, oldId))) {
          res.add(old)
        }
      }
    }

    res
  }

  private def mayUseIdDirect(idType: Type): Boolean = {
    idType match {
      case basicType: BasicType[_] =>
        val javaType = basicType.getJavaType
        (javaType eq classOf[String]) || (javaType eq classOf[Integer]) || (javaType eq classOf[Long]) || (javaType eq classOf[UUID])
      case _ => false
    }
  }

  /**
   * Rewrite of AbstractPersistentCollection.AbstractValueDelayedOperation
   * For scala cannot access java protected constructor(since 3.3.5),so just rewrite it
   *
   * @param addedValue
   * @param orphan
   */
  abstract class Delayed[E](private var addedValue: E, private val orphan: E,
                            session: SharedSessionContractImplementor,
                            owner: Object)
    extends AbstractPersistentCollection.ValueDelayedOperation[E] {

    override def replace(persister: CollectionPersister, copyCache: java.util.Map[AnyRef, AnyRef]): Unit = {
      if (addedValue != null) addedValue = getReplacement(PersistentHelper.getElementType(persister), addedValue, copyCache)
    }

    private def getReplacement(`type`: org.hibernate.`type`.Type, current: Any, copyCache: java.util.Map[AnyRef, AnyRef]): E = {
      `type`.replace(current, null, session, owner, copyCache).asInstanceOf[E]
    }

    override final def getAddedInstance: E = addedValue

    override final def getOrphan: E = orphan
  }

}
