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

import org.hibernate.collection.spi.AbstractPersistentCollection
import org.hibernate.engine.internal.ForeignKeys
import org.hibernate.engine.spi.{SharedSessionContractImplementor, Status, TypedValue}
import org.hibernate.internal.util.collections.IdentitySet
import org.hibernate.persister.collection.CollectionPersister

import java.util as ju
import scala.jdk.javaapi.CollectionConverters.asJava

private[udt] object SeqHelper {

  def getOrphans(oldElements: Iterable[Object], currentElements: Iterable[Object], entityName: String, session: SharedSessionContractImplementor): ju.Collection[Object] = {
    // short-circuit(s)
    if (currentElements.isEmpty) return asJava(oldElements.toSeq)
    if (oldElements.isEmpty) return ju.Collections.emptyList()

    val entityPersister = session.getFactory.getMappingMetamodel.findEntityDescriptor(entityName)
    val idType = entityPersister.getIdentifierType

    // create the collection holding the Orphans
    val res = new ju.ArrayList[Object]

    // collect EntityIdentifier(s) of the *current* elements - add them into a HashSet for fast access
    val currentIds = new ju.HashSet[Object]
    val currentSaving = new IdentitySet[Object]
    currentElements foreach { current =>
      if (current != null && ForeignKeys.isNotTransient(entityName, current, null, session)) {
        val ee = session.getPersistenceContext.getEntry(current)
        if (ee != null && ee.getStatus == Status.SAVING) {
          currentSaving.add(current)
        } else {
          val currentId = ForeignKeys.getEntityIdentifierIfNotUnsaved(entityName, current, session)
          currentIds.add(new TypedValue(idType, currentId))
        }
      }
    }

    // iterate over the *old* list
    oldElements foreach { old =>
      if (!currentSaving.contains(old)) {
        val oldId = ForeignKeys.getEntityIdentifierIfNotUnsaved(entityName, old, session)
        if (!currentIds.contains(new TypedValue(idType, oldId))) {
          res.add(old)
        }
      }
    }

    res
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
      if (addedValue != null) addedValue = getReplacement(persister.getElementType, addedValue, copyCache)
    }

    private def getReplacement(`type`: org.hibernate.`type`.Type, current: Any, copyCache: java.util.Map[AnyRef, AnyRef]): E =
      `type`.replace(current, null, session, owner, copyCache).asInstanceOf[E]

    override final def getAddedInstance: E = addedValue

    override final def getOrphan: E = orphan
  }
}
