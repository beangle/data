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

import org.beangle.commons.collection.Collections
import org.hibernate.`type`.Type
import org.hibernate.collection.spi.AbstractPersistentCollection
import org.hibernate.collection.spi.AbstractPersistentCollection.DelayedOperation
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.metamodel.mapping.PluralAttributeMapping
import org.hibernate.persister.collection.CollectionPersister

import java.io.Serializable as JSerializable
import java.util as ju
import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters.asJava

class ScalaPersistentBag(session: SharedSessionContractImplementor)
  extends AbstractPersistentCollection[Object](session), mutable.Buffer[Object] {

  protected var bag: mutable.Buffer[Object] = _

  def this(session: SharedSessionContractImplementor, data: Iterable[Object]) = {
    this(session)
    data match {
      case d: mutable.Buffer[Object] => bag = d
      case _ => bag = Collections.newBuffer[Object](data)
    }
    setInitialized()
    setDirectlyAccessible(true)
  }

  override def injectLoadedState(attributeMapping: PluralAttributeMapping, loadingState: ju.List[_]): Unit = {
    val collectionDescriptor = attributeMapping.getCollectionDescriptor
    val collectionSemantics = collectionDescriptor.getCollectionSemantics

    val elementCount = if null == loadingState then 0 else loadingState.size

    this.bag = collectionSemantics.instantiateRaw(elementCount, collectionDescriptor).asInstanceOf[mutable.Buffer[Object]]
    if null != loadingState then
      import scala.jdk.javaapi.CollectionConverters.asScala
      bag.addAll(asScala(loadingState))
  }

  override def equalsSnapshot(persister: CollectionPersister): Boolean = {
    val elementType = persister.getElementType()
    val sn = getSnapshot().asInstanceOf[mutable.Buffer[Object]]
    if (sn.size != bag.size) {
      return false
    }

    val hashToInstancesBag = groupByEqualityHash(bag, elementType)
    val hashToInstancesSn = groupByEqualityHash(sn, elementType)
    if (hashToInstancesBag.size != hashToInstancesSn.size) {
      return false
    }

    // First iterate over the hashToInstancesBag entries to see if the number
    // of List values is different for any hash value.
    val diff = hashToInstancesBag exists { case (hash, objs) =>
      hashToInstancesSn.get(hash) match
        case None => true
        case Some(instancesSn) => instancesSn.size != objs.size
    }

    if (diff) return false
    // We already know that both hashToInstancesBag and hashToInstancesSn have:
    // 1) the same hash values;
    // 2) the same number of values with the same hash value.

    // Now check if the number of occurrences of each element is the same.
    val iter = hashToInstancesBag.iterator
    while (iter.hasNext) {
      val entry = iter.next()
      val hash = entry._1
      val instancesBag = entry._2
      val instancesSn = hashToInstancesSn(hash)
      val bagIter = instancesBag.iterator
      while (bagIter.hasNext) {
        val instance = bagIter.next()
        if (!expectOccurrences(
          instance,
          instancesBag,
          elementType,
          countOccurrences(instance, instancesSn, elementType)
        )) {
          return false
        }
      }
    }
    true
  }

  /**
   * Groups items in searchedBag according to persistence "equality" as defined in Type.isSame and Type.getHashCode
   *
   * @return Map of "equality" hashCode to List of objects
   */
  private def groupByEqualityHash(searchedBag: mutable.Buffer[Object], elementType: Type): Map[Integer, mutable.Buffer[Object]] = {
    if searchedBag.isEmpty then Map.empty[Integer, mutable.Buffer[Object]]
    else searchedBag.groupBy(x => nullableHashCode(x, elementType))
  }

  /**
   * @return the default elementType hashcode of the object o, or null if the object is null
   */
  private def nullableHashCode(o: Object, elementType: Type): Integer = {
    if o == null then Integer.valueOf(0) else elementType.getHashCode(o)
  }

  override def isSnapshotEmpty(snapshot: JSerializable): Boolean = {
    snapshot.asInstanceOf[collection.Seq[_]].isEmpty
  }

  override def isWrapper(collection: Object): Boolean = {
    bag eq collection
  }

  private def countOccurrences(element: Object, list: mutable.Buffer[Object], elementType: Type): Int = {
    list.count(x => elementType.isSame(element, x))
  }

  private def expectOccurrences(element: Object, list: mutable.Buffer[Object], elementType: Type, expected: Int): Boolean = {
    list.count(x => elementType.isSame(element, x)) == expected
  }

  override def getSnapshot(persister: CollectionPersister): JSerializable = {
    val clonedList = new mutable.ArrayBuffer[Object]
    bag.foreach { ele => clonedList += persister.getElementType.deepCopy(ele, persister.getFactory) }
    clonedList
  }

  override def getOrphans(snapshot: JSerializable, entityName: String): ju.Collection[Object] = {
    SeqHelper.getOrphans(snapshot.asInstanceOf[mutable.ArrayBuffer[Object]], bag, entityName, getSession)
  }

  override def initializeEmptyCollection(persister: CollectionPersister): Unit = {
    bag = persister.getCollectionType.instantiate(0).asInstanceOf[mutable.Buffer[Object]]
    endRead()
  }

  override def disassemble(persister: CollectionPersister): Object = {
    bag.map(ele => persister.getElementType.disassemble(ele, getSession, null)).toArray[JSerializable]
  }

  override def initializeFromCache(persister: CollectionPersister, disassembled: Object, owner: Object): Unit = {
    val array = disassembled.asInstanceOf[Array[JSerializable]]
    this.bag = persister.getCollectionSemantics.instantiateRaw(array.length, persister).asInstanceOf[mutable.Buffer[Object]]
    array foreach { ele =>
      val item = persister.getElementType.assemble(ele, getSession, owner)
      if (null != item) bag.addOne(item)
    }
  }

  override def needsRecreate(persister: CollectionPersister): Boolean = {
    !persister.isOneToMany
  }

  override def getDeletes(persister: CollectionPersister, indexIsFormula: Boolean): ju.Iterator[_] = {
    val deletes = new ju.ArrayList[Object]()
    val sn = getSnapshot().asInstanceOf[mutable.ArrayBuffer[Object]]
    val elementType = persister.getElementType
    val olditer = sn.iterator
    var i = 0;
    while (olditer.hasNext) {
      val old = olditer.next();
      val newiter = bag.iterator
      var found = false
      if (bag.size > i && elementType.isSame(old, bag(i))) {
        //a shortcut if its location didn't change!
        found = true
      } else {
        //search for it note that this code is incorrect for other than one-to-many
        while (newiter.hasNext && !found) {
          if (elementType.isSame(old, newiter.next())) {
            found = true
          }
        }
      }
      i += 1
      if (!found) deletes.add(old)
    }
    deletes.iterator()
  }

  //used by hibernate 7
  def hasDeletes(persister: CollectionPersister): Boolean = {
    val sn = getSnapshot().asInstanceOf[mutable.ArrayBuffer[Object]]
    val elementType = persister.getElementType
    if (sn == null) {
      return false
    }
    val olditer = sn.iterator
    var i: Int = 0
    val bagiter = bag.iterator
    while (olditer.hasNext) {
      val old: AnyRef = olditer.next
      val newiter = bag.iterator
      var found: Boolean = false
      if (bag.size > i && {
        i += 1;
        i - 1
      } > 0 && elementType.isSame(old, bagiter.next)) {
        //a shortcut if its location didn't change!
        found = true
      }
      else {
        //search for it
        //note that this code is incorrect for other than one-to-many
        while (newiter.hasNext && !found) {
          if (elementType.isSame(old, newiter.next)) {
            found = true
          }
        }
      }
      if (!found) {
        return true
      }
    }
    false
  }

  override def needsInserting(entry: Object, i: Int, elemType: Type): Boolean = {
    val sn = getSnapshot().asInstanceOf[mutable.ArrayBuffer[Object]]
    if (sn.size > i && elemType.isSame(sn(i), entry)) {
      false
    } else {
      val iter = sn.iterator
      while (iter.hasNext) {
        val old = iter.next()
        if elemType.isSame(old, entry) then return false
      }
      true
    }
  }

  override def needsUpdating(entry: AnyRef, i: Int, elemType: Type) = false

  override def isRowUpdatePossible = false

  override def length: Int = {
    if (readSize()) getCachedSize else bag.size
  }

  override def isEmpty: Boolean = {
    if (readSize()) getCachedSize == 0 else bag.isEmpty
  }

  override def contains[A1 >: Object](elem: A1): Boolean = {
    val exists = readElementExistence(elem)
    if (exists == null) bag.contains(elem) else exists
  }

  override def iterator: Iterator[Object] = {
    read()
    bag.iterator
  }

  override def addOne(ele: Object): this.type = {
    if (!isOperationQueueEnabled) {
      write()
      bag += ele
    } else {
      queueOperation(new SimpleAdd(ele, true))
    }
    this
  }

  override def prepend(ele: Object): this.type = {
    if (!isOperationQueueEnabled) {
      write()
      ele +=: bag
    } else {
      queueOperation(new SimpleAdd(ele, false))
    }
    this
  }

  override def subtractOne(ele: Object): this.type = {
    initialize(true)
    val oldSize = bag.size
    bag.subtractOne(ele)
    if (oldSize != bag.size) {
      elementRemoved = true
      dirty()
    }
    this
  }

  override def apply(index: Int): Object = {
    read()
    bag(index)
  }

  override def patchInPlace(from: Int, patch: collection.IterableOnce[Object], replaced: Int): this.type = {
    remove(from, replaced)
    bag.insertAll(from, patch)
    this
  }

  override def update(n: Int, elem: Object): Unit = {
    write()
    bag.update(n, elem)
  }

  override def remove(idx: Int, count: Int): Unit = {
    (0 until count) foreach (_ => remove(idx))
  }

  override def remove(idx: Int): Object = {
    write()
    this.elementRemoved = true
    bag.remove(idx)
  }

  override def insert(idx: Int, elem: Object): Unit = {
    if (null != elem) {
      write()
      bag.insert(idx, elem)
    }
  }

  override def insertAll(n: Int, elems: IterableOnce[Object]): Unit = {
    if (elems.iterator.nonEmpty) {
      write()
      bag.insertAll(n, elems)
    }
  }

  override def indexWhere(p: Object => Boolean, from: Int): Int = {
    read()
    bag.indexWhere(p, from)
  }

  override def lastIndexWhere(p: Object => Boolean, from: Int): Int = {
    read()
    bag.lastIndexWhere(p, from)
  }

  override def isCollectionEmpty: Boolean = bag.isEmpty

  override def entries(persister: CollectionPersister): ju.Iterator[_] = {
    asJava(bag.iterator)
  }

  override def clear(): Unit = {
    if (isClearQueueEnabled) {
      queueOperation(new Clear())
    } else {
      initialize(true)
      if (bag.nonEmpty) {
        bag.clear()
        dirty()
      }
    }
  }

  override def getIndex(entry: Object, i: Int, persister: CollectionPersister): Object = {
    throw new UnsupportedOperationException("Bags don't have indexes : " + persister.getRole)
  }

  override def getElement(entry: Object): Object = {
    entry
  }

  override def getSnapshotElement(entry: Object, i: Int): Object = {
    getSnapshot().asInstanceOf[mutable.ArrayBuffer[Object]](i)
  }

  override def entryExists(entry: Object, i: Int): Boolean = {
    entry != null
  }

  override def toString: String = {
    read()
    bag.toString()
  }

  /** Bag does not respect the collection API
   *
   * @param other
   * @return
   */
  override def equals(other: Any): Boolean = {
    super.equals(other)
  }

  override def hashCode(): Int = {
    super.hashCode()
  }

  final class Clear extends DelayedOperation[Object] {
    override def operate(): Unit = {
      bag.clear()
    }

    override def getAddedInstance: Object = null

    override def getOrphan: Object = {
      throw new UnsupportedOperationException("queued clear cannot be used with orphan delete")
    }
  }

  final class SimpleAdd(value: Object, append: Boolean) extends SeqHelper.Delayed(value, null, session, getOwner) {
    override def operate(): Unit = {
      val added = getAddedInstance
      if (!bag.contains(added)) {
        if append then bag.addOne(added)
        else bag.prepend(added)
      }
    }
  }

  final class SimpleRemove(orphan: Object) extends SeqHelper.Delayed(null, orphan, session, getOwner) {
    override def operate(): Unit = {
      bag.subtractOne(getOrphan)
    }
  }

}
