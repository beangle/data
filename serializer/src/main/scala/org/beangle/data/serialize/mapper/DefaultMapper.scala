package org.beangle.data.serialize.mapper

import java.io.File
import java.net.{ URI, URL }
import java.{ util => ju }
import java.{ lang => jl }
import scala.math.{ BigDecimal, BigInt }
import org.beangle.commons.lang.{ ClassLoaders, Strings }
import scala.collection.convert.Wrappers.JListWrapper
import scala.collection.convert.Wrappers.JSetWrapper
import scala.collection.convert.Wrappers.JMapWrapper
class DefaultMapper extends Mapper {

  val classAlias = new collection.mutable.HashMap[Class[_], String]

  aliasBuildin()

  def alias(alias: String, clazz: Class[_]): Unit = {
    classAlias.put(clazz, alias)
  }

  def aliasUnCamel(classes: Class[_]*): Unit = {
    classes foreach { clazz =>
      classAlias.put(clazz, Strings.unCamel(clazz.getSimpleName))
    }
  }

  def alias(alias: String, className: String): Unit = {
    try {
      classAlias.put(ClassLoaders.loadClass(className), alias)
    } catch {
      case e: Throwable =>
    }
  }

  override def serializedClass(clazz: Class[_]): String = {
    var simpleName = clazz.getSimpleName
    if (simpleName.endsWith("Bean")) simpleName = simpleName.substring(0, simpleName.length() - 4)
    classAlias.getOrElse(clazz, Strings.unCamel(simpleName))
  }

  override def aliasForSystemAttribute(name: String): String = {
    name
  }

  override def serializedMember(clazz: Class[_], memberName: String): String = {
    memberName
  }

  private def aliasBuildin() {
    //basic
    alias("int", classOf[jl.Integer])
    alias("char", classOf[jl.Character])
    alias("big-int", classOf[java.math.BigInteger])
    alias("java-class", classOf[Class[_]])
    aliasUnCamel(classOf[Null], classOf[jl.Float], classOf[jl.Double], classOf[jl.Long], classOf[jl.Short])
    aliasUnCamel(classOf[jl.Byte], classOf[jl.Boolean], classOf[jl.Number], classOf[jl.Object], classOf[BigDecimal])
    aliasUnCamel(classOf[jl.StringBuilder], classOf[StringBuffer], classOf[scala.StringBuilder])
    aliasUnCamel(classOf[String], classOf[jl.reflect.Method], classOf[jl.reflect.Constructor[_]], classOf[jl.reflect.Field], classOf[ju.Date])
    aliasUnCamel(classOf[URI], classOf[URL])
    aliasUnCamel(classOf[scala.math.BigInt], classOf[scala.math.BigDecimal])

    //java util
    alias("bit-set", classOf[ju.BitSet])
    alias("map", classOf[ju.Map[_, _]])
    alias("entry", classOf[ju.Map.Entry[_, _]])
    alias("properties", classOf[ju.Properties])
    alias("list", classOf[ju.List[_]])
    alias("set", classOf[ju.Set[_]])
    alias("sorted-set", classOf[ju.SortedSet[_]])

    alias("linked-list", classOf[ju.LinkedList[_]])
    alias("vector", classOf[ju.Vector[_]])
    alias("tree-map", classOf[ju.TreeMap[_, _]])
    alias("tree-set", classOf[ju.TreeSet[_]])
    alias("hashtable", classOf[ju.Hashtable[_, _]])

    alias("empty-list", ju.Collections.emptyList.getClass)
    alias("empty-map", ju.Collections.emptyMap.getClass)
    alias("empty-set", ju.Collections.emptySet.getClass)
    alias("singleton-list", ju.Collections.singletonList(this).getClass())
    alias("singleton-map", ju.Collections.singletonMap(this, null).getClass())
    alias("singleton-set", ju.Collections.singleton(this).getClass())

    //scala collection
    alias("list", classOf[::[_]])
    alias("list", classOf[JListWrapper[_]])
    alias("set", classOf[JSetWrapper[_]])
    alias("map", classOf[JMapWrapper[_, _]])
    alias("sql-timestamp", "java.sql.Timestamp")
    alias("sql-time", "java.sql.Time")
    alias("sql-date", "java.sql.Date")

    alias("file", classOf[File])
    alias("locale", classOf[ju.Locale])
    alias("gregorian-calendar", classOf[ju.Calendar])

    alias("duration", "javax.xml.datatype.Duration")
    alias("concurrent-hash-map", classOf[ju.concurrent.ConcurrentHashMap[_, _]])
    alias("enum-set", classOf[ju.EnumSet[_]])
    alias("enum-map", classOf[ju.EnumMap[_, _]])

    alias("uuid", classOf[ju.UUID])
  }
}