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
package org.beangle.data.hibernate.cfg

import java.lang.reflect.Field

import scala.collection.JavaConverters.asScalaBuffer

import org.beangle.commons.collection.Collections
import org.beangle.commons.logging.Logging
import org.hibernate.mapping.{ MappedSuperclass, PersistentClass, Property, RootClass }

object PersistentClassMerger extends Logging {

  private val subPropertyField = getField("subclassProperties")
  private val declarePropertyField = getField("declaredProperties")
  private val subclassField = getField("subclasses")

  val mergeSupport = (null != subPropertyField) && (null != declarePropertyField) && (null != subclassField)

  private def getField(name: String): Field = {
    try {
      val field = classOf[PersistentClass].getDeclaredField(name)
      field.setAccessible(true)
      field
    } catch {
      case e: Exception =>
        logger.error(s"Cannot access PersistentClass $name field ,Override Mapping will be disabled", e)
        null
    }
  }

  /**
   * Merge sub property into parent
   *
   * Make parent persistent class as TARGET.
   *
   * 1. Modify parent className using sub's className
   * 2. Clear any sub/super/polymorphic/discriminator value
   * 3. Put all properties into parent
   */
  def merge(sub: PersistentClass, parent: PersistentClass): Unit = {
    if (!mergeSupport) throw new RuntimeException("Merge not supported!")
    if (sub.getMappedClass == parent.getMappedClass) return ;

    val className = sub.getClassName()
    // 1. convert old to mappedsuperclass
    val msc = new MappedSuperclass(parent.getSuperMappedSuperclass(), null)
    msc.setMappedClass(parent.getMappedClass())

    // 2.clear old subclass property
    val parentClassName = parent.getClassName
    parent.setSuperMappedSuperclass(msc)
    parent.setClassName(className)
    parent.setProxyInterfaceName(className)
    if (parent.isInstanceOf[RootClass]) {
      val rootParent = parent.asInstanceOf[RootClass]
      rootParent.setDiscriminator(null)
      rootParent.setPolymorphic(false)
    }
    try {
      val declareProperties = declarePropertyField.get(parent).asInstanceOf[java.util.List[Property]]
      for (p <- asScalaBuffer(declareProperties))
        msc.addDeclaredProperty(p)
      subPropertyField.get(parent).asInstanceOf[java.util.List[_]].clear()
      subclassField.get(parent).asInstanceOf[java.util.List[_]].clear()
    } catch {
      case e: Exception =>
    }

    // 3. add property to old
    try {
      val pIter = sub.getPropertyIterator()
      val ppIter = parent.getPropertyIterator;
      val pps = Collections.newSet[String]
      while (ppIter.hasNext()) {
        pps += ppIter.next().asInstanceOf[Property].getName
      }
      while (pIter.hasNext()) {
        val property = pIter.next().asInstanceOf[Property]
        if (!pps.contains(property.getName)) parent.addProperty(property)
      }
    } catch {
      case e: Exception =>
    }
    logger.info(s"${sub.getClassName()} replace $parentClassName.")
  }
}
