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

import org.beangle.data.orm.hibernate.udt.PersistentHelper.*
import org.hibernate.`type`.Type
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.UserCollectionType

import java.util as ju
import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters.asJava

/** 定制scala collection的公共特性
 */
abstract class ScalaCollectionType extends UserCollectionType {

  override def getElementsIterator(collection: Object): ju.Iterator[_] = {
    asJava(collection.asInstanceOf[IterableOnce[_]].iterator)
  }

  override def indexOf(collection: Object, entity: Object): Object = null

  /** 替换元素，当集合进行持久化时，会将保存的对象替换原有的对象，整个集合也会进行替换
   *
   * @param original  original collection
   * @param target    new collection
   * @param persister collection persister descriptor
   * @param owner     owner object
   * @param copyCache cache
   * @param session   session
   * @return
   */
  override def replaceElements(original: Object, target: Object, persister: CollectionPersister,
                               owner: Object, copyCache: ju.Map[_, _], session: SharedSessionContractImplementor): Object = {
    val result = target.asInstanceOf[mutable.Growable[Object]]
    result.clear()
    // copy elements into newly empty target collection
    val elemType = getElementType(session.getFactory.getMappingMetamodel.getCollectionDescriptor(persister.getRole))
    val cache = copyCache.asInstanceOf[ju.Map[Object, Object]]

    result match {
      case m: mutable.Map[Any @unchecked, Any @unchecked] =>
        val om = original.asInstanceOf[collection.Map[Any, Any]]
        om foreach { case (k, v) =>
          m.put(k, elemType.replace(v, null, session, owner, cache))
        }
      case _ =>
        for (element <- original.asInstanceOf[Iterable[_]]) {
          result.addOne(elemType.replace(element, null, session, owner, cache))
        }
    }

    //next code is translated from org.hibernate.type.CollectionType
    //@see org.hibernate.type.CollectionType
    if (original.isInstanceOf[PersistentCollection[_]] && result.isInstanceOf[PersistentCollection[_]]) {
      val opc = original.asInstanceOf[PersistentCollection[_]]
      val rpc = result.asInstanceOf[PersistentCollection[_]]
      // preserveSnapshot
      val collectionEntry = session.getPersistenceContextInternal.getCollectionEntry(rpc)
      if (collectionEntry != null) {
        collectionEntry.resetStoredSnapshot(rpc, createSnapshot(opc, rpc, elemType, owner, copyCache, session));
      }
      if !opc.isDirty then rpc.clearDirty()
    }
    result
  }

  private def createSnapshot(original: PersistentCollection[_], result: PersistentCollection[_], elemType: Type,
                             owner: AnyRef, copyCache: ju.Map[_, _], session: SharedSessionContractImplementor) = {
    val originalSnapshot = original.getStoredSnapshot
    originalSnapshot match {
      case buffer: mutable.Buffer[_] => createBufferSnapshot(buffer, elemType, owner, copyCache, session)
      case map: mutable.Map[_, _] => createMapSnapshot(map, result, elemType, owner, copyCache, session)
      case _ => result.getStoredSnapshot
    }
  }

  private def createBufferSnapshot(buffer: mutable.Buffer[_], elemType: Type, owner: AnyRef, copyCache: ju.Map[_, _],
                                   session: SharedSessionContractImplementor) = {
    val targetList = new mutable.ArrayBuffer[AnyRef](buffer.size)
    val cache = copyCache.asInstanceOf[ju.Map[Object, Object]]
    for (obj <- buffer) {
      targetList.addOne(elemType.replace(obj, null, session, owner, cache))
    }
    targetList
  }

  private def createMapSnapshot[K, V](map: mutable.Map[K, V], result: PersistentCollection[_], elemType: Type, owner: AnyRef,
                                      copyCache: ju.Map[_, _], session: SharedSessionContractImplementor) = {
    val targetMap = new mutable.HashMap[K, V]()
    val snapshot: java.io.Serializable = targetMap
    val resultSnapshot = result.getStoredSnapshot.asInstanceOf[mutable.Map[K, V]]
    val cache = copyCache.asInstanceOf[ju.Map[Object, Object]]
    map foreach { case (key, value) =>
      val resultSnapshotValue = if (resultSnapshot == null) null else resultSnapshot.get(key).orNull
      val newValue = elemType.replace(value, resultSnapshotValue, session, owner, cache)
      //noinspection unchecked
      val newKey = if key.asInstanceOf[AnyRef] eq value.asInstanceOf[AnyRef] then newValue.asInstanceOf[K] else key
      targetMap.put(newKey, newValue.asInstanceOf[V])
    }
    snapshot
  }
}
