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

import java.io.{Serializable => JSerializable}
import java.sql.ResultSet
import java.{util => ju}

import scala.jdk.javaapi.CollectionConverters.asJava
import scala.collection.mutable.{ListBuffer, HashMap => MHashMap, HashSet => MHashSet, Set => MSet}
import org.hibernate.`type`.Type
import org.hibernate.collection.internal.AbstractPersistentCollection
import org.hibernate.collection.internal.AbstractPersistentCollection.DelayedOperation
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.loader.CollectionAliases
import org.hibernate.persister.collection.CollectionPersister

import scala.collection.mutable

class PersistentSet(session: SharedSessionContractImplementor)
  extends AbstractPersistentCollection(session) with MSet[Object] {

  protected var tempList: mutable.Buffer[Object] = _

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

  override def getOrphans(snapshot: JSerializable, entityName: String): ju.Collection[_] = {
    SeqHelper.getOrphans(snapshot.asInstanceOf[MHashMap[Object, Object]].keys, set, entityName, getSession)
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

  def beforeInitialize(persister: CollectionPersister, anticipatedSize: Int): Unit = {
    this.set = persister.getCollectionType.instantiate(anticipatedSize).asInstanceOf[MHashSet[Object]]
  }

  override def initializeFromCache(persister: CollectionPersister, disassembled: JSerializable,
                                   owner: Object): Unit = {
    val array = disassembled.asInstanceOf[Array[JSerializable]]
    val size = array.length
    beforeInitialize(persister, size)
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
    if (exists == null) set.contains(elem) else exists.booleanValue
  }

  override def iterator: Iterator[Object] = {
    read()
    set.iterator
  }

  override def addOne(elem: Object): this.type = {
    val exists = if (isOperationQueueEnabled()) readElementExistence(elem) else null
    if (exists == null) {
      initialize(true)
      if (set.add(elem)) dirty()
    } else if (!exists) {
      queueOperation(new Add(elem))
    }
    this
  }

  override def subtractOne(elem: Object): this.type = {
    val exists = if (isOperationQueueEnabled()) readElementExistence(elem) else null
    if (exists == null) {
      initialize(true)
      if (this.set.remove(elem)) dirty()
    } else if (exists) {
      queueOperation(new Remove(elem))
    }
    this
  }

  override def clear(): Unit = {
    if (isClearQueueEnabled()) {
      queueOperation(new Clear())
    } else {
      initialize(true)
      if (!set.isEmpty) {
        set.clear()
        dirty()
      }
    }
  }

  override def toString: String = {
    read();
    set.toString
  }

  override def readFrom(rs: ResultSet, persister: CollectionPersister, descriptor: CollectionAliases,
                        owner: Object): Object = {
    val element = persister.readElement(rs, owner, descriptor.getSuffixedElementAliases, getSession)
    if (null != element) tempList += element
    element
  }

  override def beginRead(): Unit = {
    super.beginRead()
    tempList = new ListBuffer[Object]
  }

  override def endRead(): Boolean = {
    this.set ++= tempList
    tempList = null
    setInitialized()
    true
  }

  override def entries(persister: CollectionPersister): ju.Iterator[_] = {
    asJava(set.iterator)
  }

  override def disassemble(persister: CollectionPersister): JSerializable = {
    this.set.map(ele => persister.getElementType.disassemble(ele, getSession, null))
      .toArray[JSerializable]
  }

  override def getDeletes(persister: CollectionPersister, indexIsFormula: Boolean): ju.Iterator[_] = {
    val elementType = persister.getElementType
    val sn = getSnapshot().asInstanceOf[MHashMap[Object, Object]]
    val deletes = new ListBuffer[Object]
    deletes ++= sn.keys.filter(!set.contains(_))
    deletes ++= set.filter { ele => sn.contains(ele) && elementType.isDirty(ele, sn(ele), getSession) }
    asJava(deletes.iterator)
  }

  override def needsInserting(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[MHashMap[Object, Object]]
    !sn.get(entry).exists(ele => !elemType.isDirty(ele, entry, getSession))
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

  final class Add(value: Object) extends AbstractValueDelayedOperation(value, null) {
    override def operate(): Unit = {
      set.add(getAddedInstance())
    }
  }

  final class Remove(orphan: Object) extends AbstractValueDelayedOperation(null, orphan) {
    override def operate(): Unit = {
      set.remove(orphan)
    }
  }

  final class Clear extends DelayedOperation {
    override def operate(): Unit = {
      set.clear()
    }

    override def getAddedInstance(): Object = null

    override def getOrphan(): Object = {
      throw new UnsupportedOperationException("queued clear cannot be used with orphan delete")
    }
  }

}
