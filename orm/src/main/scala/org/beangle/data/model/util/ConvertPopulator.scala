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

package org.beangle.data.model.util

import org.beangle.commons.bean.Properties
import org.beangle.commons.conversion.Conversion
import org.beangle.commons.conversion.impl.DefaultConversion
import org.beangle.commons.lang.reflect.Reflections
import org.beangle.commons.lang.{Objects, Strings}
import org.beangle.commons.logging.Logging
import org.beangle.data.model.Entity
import org.beangle.data.model.meta.*

object ConvertPopulator extends Logging {
  val TrimStr = true
}

/**
 * ConvertPopulator
 *
 * @author chaostone
 */

import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.data.model.util.ConvertPopulator.*

class ConvertPopulator(conversion: Conversion = DefaultConversion.Instance) extends Populator with Logging {
  val properties = new Properties(conversion)

  /** Initialize target's attribuate path
   * Return the last property value and type.
   */
  override def init(target: Entity[_], et: EntityType, attr: String): (Any, Property) = {
    var propObj: Any = target
    var property: Any = null
    var objtype: StructType = et
    var propertyType: Property = null

    var index = 0
    val attrs = Strings.split(attr, ".")
    while (index < attrs.length) {
      val nested = attrs(index)
      property = properties.get[Object](propObj, nested)
      objtype.getProperty(nested) match {
        case Some(t) =>
          property match {
            case null | None =>
              property = Reflections.newInstance(t.clazz)
              properties.set(propObj.asInstanceOf[AnyRef], nested, property)
            case Some(p) =>
              property = p
            case _ =>
          }
          if (index < attrs.length) {
            t match {
              case n: SingularProperty =>
                n.propertyType match {
                  case s: StructType => objtype = s
                  case b: BasicType => if (index != attrs.length - 1) logError(propObj, nested)
                }
              case _ => logError(propObj, nested)
            }
          }
          propertyType = t
        case None =>
          if (nested.contains("[") && null != property) {
            propertyType = new Domain.SimpleProperty(nested, property.getClass, true)
          } else {
            logError(propObj, nested)
          }
      }
      index += 1
      propObj = property
    }
    (property, propertyType)
  }

  private def logError(obj: Any, propertyName: String): Unit = {
    logger.error(s"Cannot find property type [$propertyName] of ${obj.getClass}")
  }

  /**
   * 安静的拷贝属性，如果属性非法或其他错误则记录日志
   */
  override def populate(target: Entity[_], entityType: EntityType, attr: String, value: Any): Boolean = {
    populate(target, entityType, Map(attr -> value)).fails.isEmpty
  }

  /**
   * 将params中的属性([attr(string)->value(object)]，放入到实体类中。
   * <p>
   * 如果引用到了别的实体，那么<br>
   * 如果params中的id为null，则将该实体的置为null.<br>
   * 否则新生成一个实体，将其id设为params中指定的值。 空字符串按照null处理
   */
  override def populate(entity: Entity[_], entityType: EntityType, params: collection.Map[String, Any]): Populator.CopyResult = {
    val result = new Populator.CopyResult
    val idName = entityType.id.name
    params foreach {
      case (attr, v) =>
        var value = v
        value match {
          case s: String =>
            if (Strings.isEmpty(s)) value = null
            else if (TrimStr) value = s.trim()
          case _ =>
        }
        if (-1 == attr.indexOf('.')) {
          if (attr == idName) {
            if (null != value && value.toString != "0") {
              val old = properties.get[Any](entity, idName)
              if (null == old || old.toString == "0") {
                copyValue(entity, entityType, attr, value, result)
              }
            }
          } else {
            copyValue(entity, entityType, attr, value, result)
          }
        } else {
          val parentAttr = Strings.substring(attr, 0, attr.lastIndexOf('.'))
          try {
            val ot = init(entity, entityType, parentAttr)
            if (null == ot) {
              result.addFail(attr, s"error attr:[$attr] value:[$value]")
            } else {
              ot._2 match {
                case sp: SingularProperty =>
                  sp.propertyType match {
                    case ft: EntityType =>
                      val foreignKey = ft.id.name
                      if (attr.endsWith("." + foreignKey)) {
                        if (null == value) {
                          copyValue(entity, entityType, parentAttr, null, result)
                        } else {
                          val oldValue = properties.get[Object](entity, attr)
                          val newValue = convert(ft, foreignKey, value)
                          if (!Objects.equals(oldValue, newValue)) {
                            // 如果外键已经有值
                            if (null != oldValue) {
                              copyValue(entity, entityType, parentAttr, null, result)
                              init(entity, entityType, parentAttr)
                            }
                            properties.set(entity, attr, newValue)
                          }
                        }
                      } else {
                        copyValue(entity, entityType, attr, value, result)
                      }
                    case _ =>
                      copyValue(entity, entityType, attr, value, result)
                  }
                case _ =>
              }
            }
          } catch {
            case _: Exception => result.addFail(attr, "error attr:[$attr] value:[$value]")
          }
        }
    }
    result
  }

  private def convert(t: EntityType, attr: String, value: Any): Any = {
    if (value.isInstanceOf[AnyRef] && null == value) null
    else {
      t.getProperty(attr) match {
        case Some(ty) => conversion.convert(value, ty.clazz)
        case None => throw new RuntimeException("cannot find attribute type of " + attr + " in " + t.entityName)
      }
    }
  }

  private def copyValue(target: AnyRef, entityType: EntityType, attr: String, value: Any, result: Populator.CopyResult): Any = {
    val targetValue = properties.copy(target, BeanInfos.get(entityType.clazz), attr, value)
    if (null == value && null != targetValue && None != targetValue || null != value && null == targetValue) {
      result.addFail(attr, "copied value" + targetValue)
    }
  }
}
