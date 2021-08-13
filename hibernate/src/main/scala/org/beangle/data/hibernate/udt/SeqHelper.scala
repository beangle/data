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

package org.beangle.data.hibernate.udt

import java.{util => ju}

import org.hibernate.engine.internal.ForeignKeys
import org.hibernate.engine.spi.{SharedSessionContractImplementor, Status, TypedValue}
import org.hibernate.internal.util.collections.IdentitySet

import scala.jdk.javaapi.CollectionConverters.asJava

private[udt] object SeqHelper {

  def getOrphans(oldElements: Iterable[_], currentElements: Iterable[_], entityName: String, session: SharedSessionContractImplementor): ju.Collection[Any] = {
    // short-circuit(s)
    if (currentElements.isEmpty) return asJava(oldElements.toSeq)
    if (oldElements.isEmpty) return ju.Collections.emptyList()

    val entityPersister = session.getFactory.getMetamodel.entityPersister(entityName)
    val idType = entityPersister.getIdentifierType

    // create the collection holding the Orphans
    val res = new ju.ArrayList[Any]

    // collect EntityIdentifier(s) of the *current* elements - add them into a HashSet for fast access
    val currentIds = new ju.HashSet[Any]
    val currentSaving = new IdentitySet()
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
}
