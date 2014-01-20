package org.beangle.data.hibernate.internal

import java.lang.reflect.Field

import scala.collection.JavaConversions._

import org.beangle.commons.logging.Logging
import org.hibernate.mapping.MappedSuperclass
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
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
      case e: Exception => logger.error("Cannot access PersistentClass " + name + " field ,Override Mapping will be disabled", e);
    }
    null
  }

  def merge(sub: PersistentClass, parent: PersistentClass) {
    if (!mergeSupport) throw new RuntimeException("Merge not supported!");

    val className = sub.getClassName();
    // 1. convert old to mappedsuperclass
    val msc = new MappedSuperclass(parent.getSuperMappedSuperclass(), null);
    msc.setMappedClass(parent.getMappedClass());

    // 2.clear old subclass property
    parent.setSuperMappedSuperclass(msc);
    parent.setClassName(className);
    parent.setProxyInterfaceName(className);
    if (parent.isInstanceOf[RootClass]) {
      val rootParent = parent.asInstanceOf[RootClass]
      rootParent.setDiscriminator(null)
      rootParent.setPolymorphic(false)
    }
    try {
      val declareProperties = declarePropertyField.get(parent).asInstanceOf[java.util.List[Property]]
      for (p <- declareProperties)
        msc.addDeclaredProperty(p);
      subPropertyField.get(parent).asInstanceOf[java.util.List[_]].clear();
      subclassField.get(parent).asInstanceOf[java.util.List[_]].clear();
    } catch {
      case e: Exception =>
    }

    // 3. add property to old
    try {
      val pIter = sub.getPropertyIterator();
      while (pIter.hasNext()) {
        parent.addProperty(pIter.next().asInstanceOf[Property]);
      }
    } catch {
      case e: Exception =>
    }
    logger.info("{} replace {}.", sub.getClassName(), parent.getClassName());
  }
}