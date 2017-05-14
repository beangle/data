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

import java.io.{ Serializable => JSerializable }
import java.sql.ResultSet
import java.{ util => ju }

import scala.collection.JavaConverters
import scala.collection.mutable
import scala.collection.mutable.Buffer

import org.hibernate.`type`.Type
import org.hibernate.collection.internal.AbstractPersistentCollection
import org.hibernate.collection.internal.AbstractPersistentCollection.DelayedOperation
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.loader.CollectionAliases
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.engine.spi.SharedSessionContractImplementor

class PersistentSet(session: SharedSessionContractImplementor, var set: mutable.Set[Object] = null)
    extends AbstractPersistentCollection(session) with collection.mutable.Set[Object] {

  protected var tempList: Buffer[Object] = _

  if (null != set) {
    setInitialized()
    setDirectlyAccessible(true)
  }

  override def getSnapshot(persister: CollectionPersister): JSerializable = {
    val cloned = new mutable.HashMap[Object, Object]
    set foreach { ele =>
      val copied = persister.getElementType().deepCopy(ele, persister.getFactory())
      cloned.put(copied, copied);
    }
    cloned
  }

  override def getOrphans(snapshot: JSerializable, entityName: String): ju.Collection[_] = {
    SeqHelper.getOrphans(snapshot.asInstanceOf[mutable.HashMap[Object, Object]].keys, set, entityName, getSession())
  }

  override def equalsSnapshot(persister: CollectionPersister): Boolean = {
    val elementType = persister.getElementType()
    val sn = getSnapshot().asInstanceOf[mutable.HashMap[Object, Object]]
    (sn.size == set.size) && !set.exists { test => !sn.contains(test) || elementType.isDirty(sn(test), test, getSession()) }
  }

  override def isSnapshotEmpty(snapshot: JSerializable): Boolean = snapshot.asInstanceOf[mutable.HashMap[_, _]].isEmpty

  def beforeInitialize(persister: CollectionPersister, anticipatedSize: Int) {
    this.set = persister.getCollectionType().instantiate(anticipatedSize).asInstanceOf[mutable.HashSet[Object]]
  }

  override def initializeFromCache(persister: CollectionPersister, disassembled: JSerializable, owner: Object) {
    val array = disassembled.asInstanceOf[Array[JSerializable]]
    val size = array.length
    beforeInitialize(persister, size)
    array foreach { ele =>
      val newone = persister.getElementType().assemble(ele, getSession(), owner)
      if (null != newone) set += newone
    }
  }

  override def isWrapper(collection: Object): Boolean = set eq collection

  override def readFrom(rs: ResultSet, persister: CollectionPersister, descriptor: CollectionAliases, owner: Object): Object = {
    val element = persister.readElement(rs, owner, descriptor.getSuffixedElementAliases(), getSession())
    if (null != element) tempList += element
    element
  }
  override def beginRead() {
    super.beginRead();
    tempList = new mutable.ListBuffer[Object]
  }

  override def endRead(): Boolean = {
    set ++= tempList
    tempList = null
    setInitialized()
    true
  }

  override def getDeletes(persister: CollectionPersister, indexIsFormula: Boolean): ju.Iterator[_] = {
    val elementType = persister.getElementType()
    val sn = getSnapshot().asInstanceOf[mutable.HashMap[Object, Object]]
    val deletes = new mutable.ListBuffer[Object]()
    deletes ++= sn.filterKeys(!set.contains(_)).keys
    deletes ++= set.filter { ele => sn.contains(ele) && elementType.isDirty(ele, sn(ele), getSession()) }
    JavaConverters.asJavaIterator(deletes.iterator)
  }
  override def disassemble(persister: CollectionPersister): JSerializable = {
    set.map(ele => persister.getElementType().disassemble(ele, getSession(), null)).toArray.asInstanceOf[Array[JSerializable]]
  }
  override def entries(persister: CollectionPersister): ju.Iterator[_] = {
    JavaConverters.asJavaIterator(set.iterator)
  }

  override def entryExists(entry: Object, i: Int): Boolean = true

  override def getElement(entry: Object): Object = entry

  override def getSnapshotElement(entry: Object, i: Int): Object = {
    throw new UnsupportedOperationException("Sets don't support updating by element")
  }

  override def getIndex(entry: Object, i: Int, persister: CollectionPersister): Object = {
    throw new UnsupportedOperationException("Sets don't have indexes");
  }

  override def needsInserting(entry: Object, i: Int, elemType: Type): Boolean = {
    // note that it might be better to iterate the snapshot but this is safe,
    // assuming the user implements equals() properly, as required by the Set contract!
    !getSnapshot().asInstanceOf[mutable.HashMap[Object, Object]].get(entry).exists(ele => !elemType.isDirty(ele, entry, getSession()))
  }

  override def needsUpdating(entry: Object, i: Int, elemType: Type): Boolean = false

  override def isCollectionEmpty: Boolean = {
    set.isEmpty
  }

  override def size: Int = {
    if (readSize()) getCachedSize() else set.size
  }

  override def iterator: Iterator[Object] = {
    read(); set.iterator
  }

  override def +=(elem: Object): this.type = {
    val exists = if (isOperationQueueEnabled()) readElementExistence(elem) else null
    if (exists == null) {
      initialize(true)
      if (set.add(elem)) dirty()
    } else if (!exists) {
      queueOperation(new Add(elem));
    }
    this
  }

  override def -=(elem: Object): this.type = {
    val exists = if (isOperationQueueEnabled()) readElementExistence(elem) else null
    if (exists == null) {
      initialize(true)
      if (set.remove(elem)) dirty()
    } else if (exists)
      queueOperation(new Remove(elem));
    this
  }

  override def clear() {
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

  override def contains(elem: Object): Boolean = { set.contains(elem) }

  override def toString(): String = {
    read(); set.toString()
  }

  override def equals(other: Any): Boolean = {
    read(); set.equals(other)
  }

  override def hashCode(): Int = {
    read(); set.hashCode()
  }
  final class Add(val value: Object) extends DelayedOperation {
    override def operate() { set += value }
    override def getAddedInstance(): Object = value
    override def getOrphan(): Object = null
  }

  final class Remove(old: Object) extends DelayedOperation {
    override def operate() { set.remove(old) }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = old
  }
  final class Clear extends DelayedOperation {
    override def operate() { set.clear() }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = throw new UnsupportedOperationException("queued clear cannot be used with orphan delete")
  }
}
