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

import scala.Range
import scala.collection.JavaConverters
import scala.collection.mutable

import org.hibernate.`type`.Type
import org.hibernate.collection.internal.AbstractPersistentCollection
import org.hibernate.collection.internal.AbstractPersistentCollection.{ DelayedOperation, UNKNOWN }
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.loader.CollectionAliases
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.engine.spi.SharedSessionContractImplementor

class PersistentMap(session: SharedSessionContractImplementor, var map: mutable.Map[Object, Object] = null)
    extends AbstractPersistentCollection(session) with mutable.Map[Object, Object] {
  type MM = mutable.Map[Object, Object]
  type MHM = mutable.HashMap[Object, Object]

  private var loadingEntries: mutable.ListBuffer[Array[Object]] = _

  if (null != map) {
    setInitialized()
    setDirectlyAccessible(true)
  }

  override def getSnapshot(persister: CollectionPersister): JSerializable = {
    val cloned = new MHM
    map foreach { e => cloned.put(e._1, persister.getElementType().deepCopy(e._2, persister.getFactory())) }
    cloned
  }

  override def endRead(): Boolean = {
    if (loadingEntries != null) {
      loadingEntries.foreach { entry =>
        map.put(entry(0), entry(1))
      }
      loadingEntries = null
    }
    super.endRead()
  }
  override def getOrphans(snapshot: JSerializable, entityName: String): ju.Collection[_] = {
    SeqHelper.getOrphans(snapshot.asInstanceOf[MM].values, map.values, entityName, getSession())
  }

  override def equalsSnapshot(persister: CollectionPersister): Boolean = {
    val elementType = persister.getElementType()
    val sn = getSnapshot().asInstanceOf[MM]
    (sn.size == map.size) && !map.exists { e => elementType.isDirty(e._2, sn.get(e._1).orNull, getSession()) }
  }

  override def isSnapshotEmpty(snapshot: JSerializable): Boolean = snapshot.asInstanceOf[MM].isEmpty

  def beforeInitialize(persister: CollectionPersister, anticipatedSize: Int) {
    this.map = persister.getCollectionType().instantiate(anticipatedSize).asInstanceOf[MM]
  }

  override def initializeFromCache(persister: CollectionPersister, disassembled: JSerializable, owner: Object) {
    val array = disassembled.asInstanceOf[Array[JSerializable]]
    val size = array.length
    beforeInitialize(persister, size)
    Range(0, size, 2) foreach { i =>
      map.put(persister.getIndexType().assemble(array(i), getSession(), owner), persister.getElementType().assemble(array(i + 1), getSession(), owner))
    }
  }

  override def isWrapper(collection: Object): Boolean = map eq collection

  override def readFrom(rs: ResultSet, persister: CollectionPersister, descriptor: CollectionAliases, owner: Object): Object = {
    val element = persister.readElement(rs, owner, descriptor.getSuffixedElementAliases(), getSession())
    if (null != element) {
      val index = persister.readIndex(rs, descriptor.getSuffixedIndexAliases(), getSession())
      if (loadingEntries == null) loadingEntries = new mutable.ListBuffer[Array[Object]]
      loadingEntries += Array[Object](index, element)
    }
    element
  }
  override def getDeletes(persister: CollectionPersister, indexIsFormula: Boolean): ju.Iterator[_] = {
    val deletes = new mutable.ListBuffer[Object]
    val sn = getSnapshot().asInstanceOf[MM]
    sn foreach { e =>
      if (null != e._2 && !map.contains(e._1)) {
        deletes += (if (indexIsFormula) e._2 else e._1)
      }
    }
    JavaConverters.asJavaIterator(deletes.iterator)
  }

  override def disassemble(persister: CollectionPersister): JSerializable = {
    val result = new Array[JSerializable](map.size * 2)
    var i = 0
    map foreach { e =>
      result(i) = persister.getIndexType().disassemble(e._1, getSession(), null)
      result(i + 1) = persister.getElementType().disassemble(e._2, getSession(), null)
      i += 2
    }
    result
  }

  override def entries(persister: CollectionPersister): ju.Iterator[_] = {
    JavaConverters.asJavaIterator(map.iterator)
  }

  override def entryExists(entry: Object, i: Int): Boolean = {
    null != entry.asInstanceOf[(_, _)]._2
  }

  override def getElement(entry: Object): Object = entry.asInstanceOf[(_, Object)]._2

  override def getSnapshotElement(entry: Object, i: Int): Object = {
    getSnapshot().asInstanceOf[MM].get(entry.asInstanceOf[(_, Object)]._2).orNull
  }

  override def getIndex(entry: Object, i: Int, persister: CollectionPersister): Object = {
    entry.asInstanceOf[(Object, _)]._1
  }

  override def needsInserting(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[MM]
    val e = entry.asInstanceOf[(Object, Object)]
    e._2 != null && !sn.contains(e._1)
  }

  override def needsUpdating(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[MM]
    val e = entry.asInstanceOf[(Object, Object)]
    val snValue = sn.get(e._1).orNull
    e._2 != null && snValue != null && elemType.isDirty(snValue, e._2, getSession())
  }

  override def isCollectionEmpty: Boolean = {
    map.isEmpty
  }

  override def size: Int = {
    if (readSize()) getCachedSize() else map.size
  }

  override def iterator: Iterator[(Object, Object)] = {
    read()
    map.iterator
  }

  override def get(key: Object): Option[Object] = {
    val result = readElementByIndex(key)
    if (result eq UNKNOWN) map.get(key) else Some(result)
  }

  override def -=(key: Object): this.type = {
    if (isPutQueueEnabled()) {
      val old = readElementByIndex(key)
      if (!(old eq UNKNOWN)) queueOperation(new Remove(key, old))
    }
    initialize(true)
    if (map.contains(key)) dirty()
    map -= key
    this
  }

  def +=(kv: (Object, Object)): this.type = {
    if (isPutQueueEnabled()) {
      val old = readElementByIndex(kv._1)
      if (!(old eq UNKNOWN)) queueOperation(new Put(kv, old))
    }
    initialize(true)
    val old = map.put(kv._1, kv._2).orNull
    if (old != kv._2) dirty()
    this
  }

  override def clear() {
    if (isClearQueueEnabled()) {
      queueOperation(new Clear())
    } else {
      initialize(true)
      if (!map.isEmpty) {
        dirty()
        map.clear()
      }
    }
  }

  override def toString(): String = {
    read()
    map.toString()
  }

  override def equals(other: Any): Boolean = {
    read()
    map.equals(other)
  }

  override def hashCode(): Int = {
    read()
    map.hashCode()
  }

  final class Put(val value: (Object, Object)) extends DelayedOperation {
    override def operate() { map += value }
    override def getAddedInstance(): Object = value
    override def getOrphan(): Object = null
  }

  final class Remove(index: Object, old: Object) extends DelayedOperation {
    override def operate() { map.remove(index) }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = old
  }
  final class Clear extends DelayedOperation {
    override def operate() { map.clear() }
    override def getAddedInstance(): Object = null
    override def getOrphan(): Object = throw new UnsupportedOperationException("queued clear cannot be used with orphan delete")
  }
}
