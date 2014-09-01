package org.beangle.data.jpa.hibernate.tool

import java.io.FileWriter
import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.{ util => ju }

import org.beangle.commons.io.ResourcePatternResolver
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.annotation.description
import org.beangle.data.jpa.hibernate.{ DefaultSessionFactoryBuilder, OverrideConfiguration, RailsNamingStrategy }
import org.beangle.data.jpa.mapping.RailsNamingPolicy
import org.hibernate.`type`.{ CustomType, EnumType, Type }
import org.hibernate.cfg.{ AvailableSettings, Configuration }
import org.hibernate.dialect.Oracle10gDialect
import org.hibernate.mapping.{ Column, PersistentClass, Property, ToOne, Value }

import freemarker.cache.ClassTemplateLoader
import javax.validation.constraints.{ NotNull, Size }

object HbmGenerator {
  def main(args: Array[String]) {
    new HbmGenerator().gen("/tmp")
    println("/tmp/hibernate.hbm.xml generated.")
  }
}
/**
 * Generator Single Hibernate mapping file from runtime configuration.
 * NOTICE: Just experimental
 * TODO support Map/List mapping
 * @author chaostone
 */
class HbmGenerator {

  private var hbconfig: Configuration = null
  private var freemarkerConfig: freemarker.template.Configuration = _

  def gen(file: String) {
    val resolver = new ResourcePatternResolver
    hbconfig = new OverrideConfiguration()
    hbconfig.getProperties().put(AvailableSettings.DIALECT, new Oracle10gDialect())
    val hbconfigBuilder = new DefaultSessionFactoryBuilder(null, hbconfig)
    hbconfigBuilder.configLocations = resolver.getResources("classpath*:META-INF/hibernate.cfg.xml")
    hbconfigBuilder.persistLocations = resolver.getResources("classpath*:META-INF/beangle/orm.properties")
    val namingPolicy = new RailsNamingPolicy()
    for (resource <- resolver.getResources("classpath*:META-INF/beangle/orm-naming.xml"))
      namingPolicy.addConfig(resource)
    hbconfigBuilder.namingStrategy = new RailsNamingStrategy(namingPolicy)
    hbconfigBuilder.buildConfiguration()
    hbconfig.buildMappings()

    freemarkerConfig = new freemarker.template.Configuration()
    freemarkerConfig.setTemplateLoader(new ClassTemplateLoader(getClass(), "/"))

    val iter = hbconfig.getClassMappings
    val pcs = new ju.ArrayList[PersistentClass]
    while (iter.hasNext()) {
      val pc = iter.next()
      val cls = pc.getMappedClass
      val pi = pc.getPropertyIterator
      // For AnnotationBinder don't set column'length and nullable in ,let's we do it.
      while (pi.hasNext()) {
        val p = pi.next().asInstanceOf[Property]
        if (p.getColumnSpan == 1) {
          val column = p.getColumnIterator.next.asInstanceOf[Column]
          if (column.getLength == Column.DEFAULT_LENGTH) {
            val size = findAnnotation(cls, classOf[Size], p.getName)
            if (null != size) column.setLength(size.max())
          }
          if (column.isNullable()) {
            val notnull = findAnnotation(cls, classOf[NotNull], p.getName)
            if (null != notnull) column.setNullable(false)
          }
        }
      }
      if (!pc.getClassName().contains(".example.")) pcs.add(pc)
    }
    val data = new ju.HashMap[String, Object]
    data.put("classes", pcs)
    data.put("generator", this)
    val freemarkerTemplate = freemarkerConfig.getTemplate("hbm.ftl")
    val fw = new FileWriter("/tmp/hibernate.hbm.xml")
    freemarkerTemplate.process(data, fw)
  }

  /**
   * find annotation on specified member
   */
  private def findAnnotation[T <: Annotation](cls: Class[_], annotationClass: Class[T], name: String): T = {
    var curr = cls
    var ann: Object = null
    while (ann == null && curr != null && !curr.equals(classOf[Object])) {
      ann = findAnnotationLocal(curr, annotationClass, name)
      curr = curr.getSuperclass()
    }
    ann.asInstanceOf[T]
  }

  private def findAnnotationLocal[T <: Annotation](cls: Class[_], annotationClass: Class[T], name: String): T = {
    var ann: Object = null
    try {
      val field = cls.getDeclaredField(name)
      ann = field.getAnnotation(annotationClass)
    } catch {
      case e: Throwable =>
    }
    if (null == ann) {
      var method: Method = null
      try {
        method = cls.getMethod("get" + Strings.capitalize(name))
        ann = method.getAnnotation(annotationClass)
      } catch {
        case e: Throwable =>
      }
      if (null == ann && null == method) {
        try {
          method = cls.getMethod("is" + Strings.capitalize(name))
          ann = method.getAnnotation(annotationClass)
        } catch {
          case e: Throwable =>
        }
      }
    }
    ann.asInstanceOf[T]
  }

  def isToOne(value: Value): Boolean = {
    value.isInstanceOf[ToOne]
  }

  def isOneToMany(value: Value): Boolean = {
    value.isInstanceOf[org.hibernate.mapping.OneToMany]
  }

  def isManyToMany(value: Value): Boolean = {
    value.isInstanceOf[org.hibernate.mapping.ManyToOne]
  }

  def isCollection(value: Value): Boolean = {
    value.isInstanceOf[org.hibernate.mapping.Collection]
  }

  def isSet(value: Value): Boolean = {
    value.isInstanceOf[org.hibernate.mapping.Set]
  }
  def isBag(value: Value): Boolean = {
    value.isInstanceOf[org.hibernate.mapping.Bag]
  }

  def isCustomType(ty: Type): Boolean = {
    ty.isInstanceOf[CustomType]
  }

  def isEnumType(ty: CustomType): Boolean = {
    ty.getUserType().isInstanceOf[EnumType]
  }
  def isScalaEnumType(ty: CustomType): Boolean = {
    ty.getUserType().isInstanceOf[org.beangle.data.jpa.hibernate.udt.EnumType]
  }
}
