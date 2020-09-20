/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.hibernate.udt

import java.io.{Serializable => JSerializable}
import java.sql.ResultSet
import java.{util => ju}

import org.hibernate.`type`.Type
import org.hibernate.collection.internal.AbstractPersistentCollection
import org.hibernate.collection.internal.AbstractPersistentCollection.{DelayedOperation, UNKNOWN}
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.loader.CollectionAliases
import org.hibernate.persister.collection.CollectionPersister

import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters.asJava

class PersistentSeq(session: SharedSessionContractImplementor)
  extends AbstractPersistentCollection(session) with mutable.Buffer[Object] {

  protected var list: mutable.Buffer[Object] = _

  def this(session: SharedSessionContractImplementor, list: mutable.Buffer[Object]) = {
    this(session)
    this.list = list
    if (null != list) {
      setInitialized()
      setDirectlyAccessible(true)
    }
  }

  override def getSnapshot(persister: CollectionPersister): JSerializable = {
    val clonedList = new mutable.ListBuffer[Object]
    list.foreach { ele => clonedList += persister.getElementType.deepCopy(ele, persister.getFactory) }
    clonedList
  }

  override def getOrphans(snapshot: JSerializable, entityName: String): ju.Collection[_] = {
    SeqHelper.getOrphans(snapshot.asInstanceOf[mutable.ListBuffer[_]], list, entityName, getSession)
  }

  override def equalsSnapshot(persister: CollectionPersister): Boolean = {
    val elementType = persister.getElementType
    val sn = getSnapshot().asInstanceOf[mutable.ListBuffer[_]]
    val itr = list.iterator
    (sn.size == list.size) && !sn.exists { ele => elementType.isDirty(itr.next(), ele, getSession) }
  }

  override def isSnapshotEmpty(snapshot: JSerializable): Boolean = {
    snapshot.asInstanceOf[collection.Seq[_]].isEmpty
  }

  override def beforeInitialize(persister: CollectionPersister, anticipatedSize: Int): Unit = {
    this.list = new mutable.ListBuffer[Object]
  }

  override def isWrapper(collection: Object): Boolean = {
    list eq collection
  }

  override def length: Int = {
    if (readSize()) getCachedSize else list.size
  }

  override def isEmpty: Boolean = {
    if (readSize()) getCachedSize == 0 else list.isEmpty
  }

  override def contains[A1 >: Object](elem: A1): Boolean = {
    val exists = readElementExistence(elem)
    if (exists == null) list.contains(elem) else exists.booleanValue
  }

  override def iterator: Iterator[Object] = {
    read()
    list.iterator
  }

  override def addOne(ele: Object): this.type = {
    if (!isOperationQueueEnabled) {
      write()
      list += ele
    } else {
      queueOperation(new Add(ele))
    }
    this
  }

  override def prepend(ele: Object): this.type = {
    if (!isOperationQueueEnabled) {
      write()
      ele +=: list
    } else {
      queueOperation(new Add(ele))
    }
    this
  }

  override def subtractOne(ele: Object): this.type = {
    val exists = if (isPutQueueEnabled) readElementExistence(ele) else null
    if (exists == null) {
      initialize(true)
      val osize = list.size
      list -= ele
      if (list.size != osize) {
        dirty()
      }
    } else if (exists) {
      queueOperation(new SimpleRemove(ele))
    }
    this
  }

  override def clear(): Unit = {
    if (isClearQueueEnabled) {
      queueOperation(new Clear())
    } else {
      initialize(true)
      if (list.nonEmpty) {
        list.clear()
        dirty()
      }
    }
  }

  override def apply(index: Int): Object = {
    val result = readElementByIndex(index)
    if (result eq UNKNOWN) list(index) else result
  }

  override def patchInPlace(from: Int, patch: collection.IterableOnce[Object], replaced: Int): this.type = {
    remove(from, replaced)
    list.insertAll(from, patch)
    this
  }

  override def update(n: Int, elem: Object): Unit = {
    val old = if (isPutQueueEnabled) readElementByIndex(n) else UNKNOWN
    if (old eq UNKNOWN) {
      write()
      list.update(n, elem)
    } else {
      queueOperation(new Set(n, elem, old))
    }
  }

  override def remove(idx: Int, count: Int): Unit = {
    (0 until count) foreach (_ => remove(idx))
  }

  override def remove(n: Int): Object = {
    val old = if (isPutQueueEnabled) readElementByIndex(n) else UNKNOWN
    if (old eq UNKNOWN) {
      write()
      list.remove(n)
    } else {
      queueOperation(new Remove(n, old))
      old
    }
  }

  override def insert(idx: Int, elem: Object): Unit = {
    if (null != elem) {
      write()
      list.insert(idx, elem)
    }
  }

  override def insertAll(n: Int, elems: IterableOnce[Object]): Unit = {
    if (elems.iterator.nonEmpty) {
      write()
      list.insertAll(n, elems)
    }
  }

  override def indexWhere(p: Object => Boolean, from: Int): Int = {
    read()
    list.indexWhere(p, from)
  }

  override def lastIndexWhere(p: Object => Boolean, from: Int): Int = {
    read()
    list.lastIndexWhere(p, from)
  }

  override def isCollectionEmpty: Boolean = {
    list.isEmpty
  }

  override def toString: String = {
    read()
    list.toString()
  }

  override def readFrom(rs: ResultSet, persister: CollectionPersister,
                        descriptor: CollectionAliases, owner: Object): Object = {
    val element = persister.readElement(rs, owner, descriptor.getSuffixedElementAliases, getSession)
    if (null == descriptor.getSuffixedIndexAliases) {
      list += element
    } else {
      val index = persister.readIndex(rs, descriptor.getSuffixedIndexAliases, getSession)
        .asInstanceOf[Integer].intValue
      //pad with nulls from the current last element up to the new index
      Range(list.size, index + 1) foreach (i => list.insert(i, null))
      list(index) = element
    }
    element
  }

  override def entries(persister: CollectionPersister): ju.Iterator[_] = {
    asJava(list.iterator)
  }

  override def initializeFromCache(persister: CollectionPersister, disassembled: JSerializable,
                                   owner: Object): Unit = {
    val array = disassembled.asInstanceOf[Array[JSerializable]]
    val size = array.length
    beforeInitialize(persister, size)
    array foreach { ele => list += persister.getElementType.assemble(ele, getSession, owner) }
  }

  override def disassemble(persister: CollectionPersister): JSerializable = {
    list.map(ele => persister.getElementType.disassemble(ele, getSession, null)).toArray
  }

  override def getDeletes(persister: CollectionPersister, indexIsFormula: Boolean): ju.Iterator[_] = {
    val deletes = new ju.ArrayList[Object]()
    val sn = getSnapshot().asInstanceOf[mutable.ListBuffer[Object]]
    val end =
      if (sn.size > list.size) {
        Range(list.size, sn.size) foreach { i => deletes.add(if (indexIsFormula) sn(i) else Integer.valueOf(i)) }
        list.size
      } else {
        sn.size
      }

    Range(0, end) foreach { i =>
      val snapshotItem = sn(i)
      if (list(i) == null && snapshotItem != null) {
        deletes.add(if (indexIsFormula) snapshotItem else Integer.valueOf(i))
      }
    }
    deletes.iterator()
  }

  override def needsInserting(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[mutable.ListBuffer[Object]]
    list(i) != null && (i >= sn.size || sn(i) == null)
  }

  override def needsUpdating(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[mutable.ListBuffer[Object]]
    i < sn.size && sn(i) != null && list(i) != null && elemType.isDirty(list(i), sn(i), getSession)
  }

  override def getIndex(entry: Object, i: Int, persister: CollectionPersister): Object = {
    Integer.valueOf(i)
  }

  override def getElement(entry: Object): Object = {
    entry
  }

  override def getSnapshotElement(entry: Object, i: Int): Object = {
    getSnapshot().asInstanceOf[mutable.ListBuffer[Object]](i)
  }

  override def equals(other: Any): Boolean = {
    read()
    list.equals(other)
  }

  override def hashCode(): Int = {
    read()
    list.hashCode()
  }

  override def entryExists(entry: Object, i: Int): Boolean = {
    entry != null
  }

  final class Clear extends DelayedOperation {
    override def operate(): Unit = {
      list.clear()
    }

    override def getAddedInstance: Object = null

    override def getOrphan: Object = {
      throw new UnsupportedOperationException("queued clear cannot be used with orphan delete")
    }
  }

  final class Add(val value: Object) extends DelayedOperation {
    override def operate(): Unit = {
      list += value
    }

    override def getAddedInstance: Object = value

    override def getOrphan: Object = null
  }

  final class Set(index: Int, value: Object, old: Object) extends DelayedOperation {
    override def operate(): Unit = {
      list.update(index, value)
    }

    override def getAddedInstance: Object = value

    override def getOrphan: Object = null
  }

  final class Remove(index: Int, old: Object) extends DelayedOperation {
    override def operate(): Unit = {
      list.remove(index)
    }

    override def getAddedInstance: Object = null

    override def getOrphan: Object = old
  }

  final class SimpleRemove(old: Object) extends DelayedOperation {
    override def operate(): Unit = {
      list -= old
    }

    override def getAddedInstance: Object = null

    override def getOrphan: Object = old
  }

}
