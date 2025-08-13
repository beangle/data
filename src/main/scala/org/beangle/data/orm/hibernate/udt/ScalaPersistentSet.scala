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

import org.hibernate.`type`.Type
import org.hibernate.collection.spi.AbstractPersistentCollection
import org.hibernate.collection.spi.AbstractPersistentCollection.DelayedOperation
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.metamodel.mapping.PluralAttributeMapping
import org.hibernate.persister.collection.CollectionPersister

import java.io.Serializable as JSerializable
import java.util as ju
import scala.collection.mutable
import scala.collection.mutable.{HashMap as MHashMap, HashSet as MHashSet, Set as MSet}
import scala.jdk.javaapi.CollectionConverters.asJava

class ScalaPersistentSet(session: SharedSessionContractImplementor)
  extends AbstractPersistentCollection[Object](session), MSet[Object] {

  protected var set: MSet[Object] = _

  def this(session: SharedSessionContractImplementor, set: MSet[Object]) = {
    this(session)
    this.set = set
    if (null != set) {
      setInitialized()
      setDirectlyAccessible(true)
    }
  }

  override def getSnapshot(persister: CollectionPersister): JSerializable = {
    val cloned = new MHashMap[Object, Object]
    set foreach { ele =>
      val copied = persister.getElementType.deepCopy(ele, persister.getFactory)
      cloned.put(copied, copied)
    }
    cloned
  }

  override def getOrphans(snapshot: JSerializable, entityName: String): ju.Collection[Object] = {
    SeqHelper.getOrphans(snapshot.asInstanceOf[MHashMap[Object, Object]].keys, set, entityName, getSession)
  }

  override def initializeEmptyCollection(persister: CollectionPersister): Unit = {
    this.set = persister.getCollectionType.instantiate(0).asInstanceOf[mutable.Set[Object]]
    endRead()
  }

  override def equalsSnapshot(persister: CollectionPersister): Boolean = {
    val elementType = persister.getElementType
    val sn = getSnapshot().asInstanceOf[MHashMap[Object, Object]]
    if (sn.size != this.set.size) {
      false
    } else {
      !this.set.exists { e =>
        sn.get(e) match {
          case None => true
          case Some(v) => elementType.isDirty(v, e, getSession)
        }
      }
    }
  }

  override def isSnapshotEmpty(snapshot: JSerializable): Boolean = {
    snapshot.asInstanceOf[MHashMap[_, _]].isEmpty
  }

  override def initializeFromCache(persister: CollectionPersister, disassembled: Object, owner: Object): Unit = {
    val array = disassembled.asInstanceOf[Array[JSerializable]]
    this.set = persister.getCollectionType.instantiate(array.length).asInstanceOf[MHashSet[Object]]
    array foreach { ele =>
      val newone = persister.getElementType.assemble(ele, getSession, owner)
      if (null != newone) this.set += newone
    }
  }

  override def isCollectionEmpty: Boolean = {
    set.isEmpty
  }

  override def size: Int = {
    if (readSize()) getCachedSize else set.size
  }

  override def isEmpty: Boolean = {
    if (readSize()) getCachedSize == 0 else this.set.isEmpty
  }

  override def contains(elem: Object): Boolean = {
    val exists = readElementExistence(elem)
    if (exists == null) set.contains(elem) else exists
  }

  override def iterator: Iterator[Object] = {
    read()
    set.iterator
  }

  override def addOne(elem: Object): this.type = {
    val exists = if (isOperationQueueEnabled) readElementExistence(elem) else null
    if (exists == null) {
      initialize(true)
      if (set.add(elem)) dirty()
    } else if (!exists) {
      queueOperation(new SimpleAdd(elem))
    }
    this
  }

  override def subtractOne(elem: Object): this.type = {
    val exists = if (isPutQueueEnabled) readElementExistence(elem) else null
    if (exists == null) {
      initialize(true)
      if (this.set.remove(elem)) {
        this.elementRemoved = true
        dirty()
      }
    } else if (exists) {
      this.elementRemoved = true
      queueOperation(new SimpleRemove(elem))
    }
    this
  }

  override def clear(): Unit = {
    if (isClearQueueEnabled) {
      queueOperation(new Clear())
    } else {
      initialize(true)
      if (set.nonEmpty) {
        set.clear()
        dirty()
      }
    }
  }

  override def toString: String = {
    read()
    set.toString
  }

  override def injectLoadedState(attributeMapping: PluralAttributeMapping, loadingStateList: ju.List[_]): Unit = {
    val collectionDescriptor = attributeMapping.getCollectionDescriptor
    val size = if null == loadingStateList then 0 else loadingStateList.size
    this.set = collectionDescriptor.getCollectionSemantics
      .instantiateRaw(size, collectionDescriptor).asInstanceOf[mutable.Set[Object]]
    if null != loadingStateList then
      import scala.jdk.javaapi.CollectionConverters.asScala
      this.set.addAll(asScala(loadingStateList))
  }

  override def entries(persister: CollectionPersister): ju.Iterator[_] = {
    asJava(set.iterator)
  }

  override def disassemble(persister: CollectionPersister): Object = {
    this.set.map(ele => persister.getElementType.disassemble(ele, getSession, null)).toArray[JSerializable]
  }

  // used by hibernate 7
  def hasDeletes(persister: CollectionPersister): Boolean = {
    val elementType = persister.getElementType
    val sn = getSnapshot().asInstanceOf[MHashMap[Object, Object]]
    var itr = sn.keySet.iterator
    while (itr.hasNext) {
      if (!set.contains(itr.next)) {
        return true
      }
    }
    itr = set.iterator
    while (itr.hasNext) {
      val test = itr.next
      val oldValue = sn.get(test).orNull
      if (oldValue != null && elementType.isDirty(test, oldValue, getSession)) {
        // the element has changed
        return true
      }
    }
    false
  }

  override def getDeletes(persister: CollectionPersister, indexIsFormula: Boolean): ju.Iterator[_] = {
    val elementType = persister.getElementType
    val sn = getSnapshot().asInstanceOf[MHashMap[Object, Object]]
    val deletes = new mutable.ArrayBuffer[Object]
    deletes ++= sn.keys.filter(!set.contains(_))
    deletes ++= set.filter { ele => sn.contains(ele) && elementType.isDirty(ele, sn(ele), getSession) }
    asJava(deletes.iterator)
  }

  override def needsInserting(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[MHashMap[Object, Object]]
    sn.get(entry).forall(ele => elemType.isDirty(ele, entry, getSession))
  }

  override def needsUpdating(entry: Object, i: Int, elemType: Type): Boolean = {
    false
  }

  override def isRowUpdatePossible: Boolean = {
    false
  }

  override def getIndex(entry: Object, i: Int, persister: CollectionPersister): Object = {
    throw new UnsupportedOperationException("Sets don't have indexes");
  }

  override def getElement(entry: Object): Object = {
    entry
  }

  override def getSnapshotElement(entry: Object, i: Int): Object = {
    throw new UnsupportedOperationException("Sets don't support updating by element")
  }

  override def equals(other: Any): Boolean = {
    read()
    set.equals(other)
  }

  override def hashCode(): Int = {
    read()
    set.hashCode()
  }

  override def entryExists(entry: Object, i: Int): Boolean = {
    null != entry
  }

  override def isWrapper(collection: Object): Boolean = {
    set eq collection
  }

  final class SimpleAdd(value: Object) extends SeqHelper.Delayed(value, null, session, getOwner) {
    override def operate(): Unit = {
      set.add(getAddedInstance())
    }
  }

  final class SimpleRemove(orphan: Object) extends SeqHelper.Delayed(null, orphan, session, getOwner) {
    override def operate(): Unit = {
      set.remove(orphan)
    }
  }

  final class Clear extends DelayedOperation[Object] {
    override def operate(): Unit = {
      set.clear()
    }

    override def getAddedInstance: Object = null

    override def getOrphan: Object = {
      throw new UnsupportedOperationException("queued clear cannot be used with orphan delete")
    }
  }

}
