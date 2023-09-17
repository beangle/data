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

package org.beangle.data.dao

import org.beangle.commons.bean.Properties
import org.beangle.commons.conversion.impl.DefaultConversion
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.data.model.util.Id
import org.beangle.data.model.{Component, Entity}

import scala.collection.mutable

/** 条件提取辅助类
 *
 * @author chaostone
 */
object Conditions extends Logging {

  def toQueryString(conditions: List[Condition]): String = {
    if (null == conditions || conditions.isEmpty) return ""
    val buf = new StringBuilder("")
    val seperator = " and "
    for (con <- conditions) {
      buf.append('(').append(con.content).append(')').append(seperator)
    }
    if (buf.nonEmpty) buf.delete(buf.length - seperator.length, buf.length)
    buf.toString
  }

  /** 提取对象中的条件
   *
   * 提取的属性仅限"平面"属性(允许包括component)<br>
   * 过滤掉属性:null,或者空Collection
   *
   * @param alias  对象别名
   * @param entity 实体对象
   */
  def extractConditions(alias: String, entity: Entity[_]): List[Condition] = {
    if (null == entity) return Nil
    val conditions = new collection.mutable.ListBuffer[Condition]
    val prefix = if (null != alias && alias.nonEmpty && !alias.endsWith(".")) alias + "." else ""
    var curr = ""
    try {
      val props = Properties.writables(entity.getClass)
      for (attr <- props) {
        curr = attr
        val value = Properties.get[Any](entity, attr)
        if (null != value && !value.isInstanceOf[Seq[_]] && !value.isInstanceOf[java.util.Collection[_]])
          addAttrCondition(conditions, prefix + attr, value)
      }
    } catch {
      case _: Exception =>
        logger.debug(s"error occur in extractConditions for  bean $entity with attr named $curr")
    }
    conditions.toList
  }

  /**
   * 获得条件的绑定参数映射
   */
  def getParamMap(conditions: Seq[Condition]): Map[String, Any] = {
    val params = new collection.mutable.HashMap[String, Any]
    for (con <- conditions) params ++= getParamMap(con)
    params.toMap
  }

  /**
   * 获得条件的绑定参数映射
   */
  def getParamMap(condition: Condition): Map[String, Any] = {
    val params = new collection.mutable.HashMap[String, Any]
    if (!Strings.contains(condition.content, "?")) {
      val paramNames = condition.paramNames
      var i = 0
      while (i < Math.min(paramNames.size, condition.params.size)) {
        params.put(paramNames(i), condition.params(i))
        i += 1
      }
    }
    params.toMap
  }

  /**
   * 为extractConditions使用的私有方法<br>
   */
  def addAttrCondition(conditions: collection.mutable.ListBuffer[Condition], name: String, value: Any): Unit = {
    value match {
      case s: String =>
        if (Strings.isNotBlank(s)) {
          val content = new StringBuilder(name)
          content.append(" like :").append(name.replace('.', '_'))
          conditions += new Condition(content.toString(), "%" + value + "%")
        }
      case c: Component =>
        conditions ++= extractComponent(name, c)
      case e: Entity[_] =>
        try {
          val key = "id"
          val property = Properties.get[Any](e, key)
          if (Id.isValid(property)) {
            val content = new StringBuilder(name)
            content.append('.').append(key).append(" = :").append(name.replace('.', '_')).append('_').append(key)
            conditions += Condition(content.toString, property)
          }
        } catch {
          case e: Exception => logger.warn(s"getProperty $value error", e);
        }
      case _ =>
        conditions += Condition(name + " = :" + name.replace('.', '_'), value)
    }
  }

  def extractComponent(prefix: String, component: Component): List[Condition] = {
    if (null == component) return Nil
    val conditions = new collection.mutable.ListBuffer[Condition]
    var curr = ""
    try {
      val props = Properties.writables(component.getClass)
      for (attr <- props) {
        curr = attr
        val value = Properties.get[Any](component, attr)
        if (null != value && !value.isInstanceOf[Seq[_]] && !value.isInstanceOf[java.util.Collection[_]])
          addAttrCondition(conditions, prefix + "." + attr, value)
      }
    } catch {
      case _: Exception => logger.warn(s"error occur in extractComponent of component:$component with attr named :$curr")
    }
    conditions.toList
  }

  /** Parse string based query value into conditions
   *
   * @param attr
   * @param value
   * @param clazz
   * @return
   */
  def parse(attr: String, value: String, clazz: Class[_]): Condition = {
    val ops = split(value, clazz).map(x => Operator(x, clazz))
    if (ops.forall(x => x.op == "=")) {
      if (ops.size == 1) {
        new Condition(s"$attr = :${attr.replace('.', '_')}", ops.head.value)
      } else {
        new Condition(s"$attr in (:${attr.replace('.', '_')})", ops.map(_.value))
      }
    } else {
      if (ops.size == 1) {
        if (ops.head.value == null) {
          new Condition(s"$attr ${ops.head.op}")
        } else {
          new Condition(s"$attr ${ops.head.op} :${attr.replace('.', '_')}", ops.head.value)
        }
      } else {
        if (ops.forall(x => x.op == "like")) {
          if (ops.length < 4) {
            var i = 0
            val content = ops.map { x => i += 1; s"$attr ${x.op} :${attr.replace('.', '_')}_$i"; }.mkString(" or ")
            new Condition(content, ops.map(_.value).toSeq: _*)
          } else {
            new Condition(s"$attr in (:${attr.replace('.', '_')})", ops.map(x => Operator.unlike(x.value.toString)))
          }
        } else {
          var i = 0
          val content = ops.map { x =>
            if x.value == null then s"$attr ${x.op}"
            else {
              i += 1
              s"$attr ${x.op} :${attr.replace('.', '_')}_$i"
            }
          }.mkString(" or ")
          new Condition(content, ops.map(_.value).toSeq.filter(_ != null): _*)
        }
      }
    }
  }

  object Operator {
    def apply(value: String, clazz: Class[_]): Operator = {
      if (clazz == classOf[String]) {
        var v = value
        var op = "="
        var startsWith = false
        var endsWith = false
        if (v.charAt(0) == '^') {
          v = v.substring(1)
          startsWith = true
        }
        if (v.charAt(v.length - 1) == '$') {
          v = v.substring(0, v.length - 1)
          endsWith = true
        }
        v = unquote(v)

        if (startsWith && endsWith) {
          new Operator("=", v)
        } else if (startsWith) {
          new Operator("like", s"$v%")
        } else if (endsWith) {
          new Operator("like", s"%$v")
        } else {
          if "null" == value then new Operator("is null", null)
          else new Operator("like", s"%$v%")
        }
      } else {
        if "null" == value then new Operator("is null", null)
        else new Operator("=", DefaultConversion.Instance.convert(value, clazz))
      }
    }

    def unlike(value: String): String = {
      var v = value
      if (v.charAt(0) == '%') v = v.substring(1)
      if (v.charAt(v.length - 1) == '%') v = v.substring(0, v.length - 1)
      v
    }

    def unquote(value: String): String = {
      var v = value
      if (v.charAt(0) == '\"') v = v.substring(1)
      if (v.charAt(v.length - 1) == '\"') v = v.substring(0, v.length - 1)
      v
    }
  }

  case class Operator(op: String, value: Any)

  protected[dao] def split(value: String, clazz: Class[_]): collection.Seq[String] = {
    if (clazz == classOf[String]) {
      var i = 0
      val chars = value.toCharArray
      val len = chars.length
      var quotStarted = false
      val commaIdxs = new mutable.ArrayBuffer[Int]
      val commaOuterIdx = new mutable.ArrayBuffer[Int]
      while (i < len) {
        if (chars(i) == ',') commaIdxs.addOne(i)
        if (chars(i) == '，') {
          commaIdxs.addOne(i)
          chars(i) = ','
        } else {
          if (chars(i) == '\"') {
            if (quotStarted) {
              commaIdxs.clear()
              quotStarted = false
            } else {
              quotStarted = true
              commaOuterIdx ++= commaIdxs
              commaIdxs.clear()
            }
          }
        }
        i += 1
      }
      commaOuterIdx ++= commaIdxs
      if (commaOuterIdx.isEmpty) {
        List(value.trim())
      } else {
        var lastIdx = 0
        val rs = new mutable.ArrayBuffer[String]
        commaOuterIdx foreach { i =>
          rs.addOne(new String(chars, lastIdx, i - lastIdx).trim())
          lastIdx = i + 1
        }
        if (lastIdx < len) rs.addOne(new String(chars, lastIdx, len - lastIdx).trim())
        rs
      }
    } else {
      Strings.split(value)
    }
  }
}
