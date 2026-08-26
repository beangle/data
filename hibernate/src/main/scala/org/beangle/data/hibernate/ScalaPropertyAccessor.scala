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

package org.beangle.data.hibernate

import org.beangle.commons.lang.Throwables
import org.beangle.commons.lang.reflect.{BeanInfo, BeanInfos}
import org.beangle.data.model.Entity
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.property.access.spi.{Getter, PropertyAccess, PropertyAccessStrategy, Setter}
import org.hibernate.{PropertyAccessException, PropertyNotFoundException, PropertySetterAccessException}

import java.lang.reflect.{Member, Method, Type}
import java.util as ju

object ScalaPropertyAccessor {

  def name: String = "scala"

  final class BasicSetter(beanInfo: BeanInfo, propertyInfo: BeanInfo.PropertyInfo) extends Setter {
    private def clazz: Class[_] = beanInfo.clazz

    override def set(target: Object, value: Object): Unit = {
      val methodHandle = propertyInfo.setter.get
      try {
        val arg =
          if (propertyInfo.isOptional) {
            if (value.isInstanceOf[Option[_]]) value else Option(value)
          } else {
            value
          }
        methodHandle.invoke(target, arg)
      } catch {
        case npe: NullPointerException =>
          if (value == null && methodHandle.`type`.parameterArray()(1).isPrimitive) {
            throw new PropertyAccessException(npe, "Null value was assigned to a property of primitive type", true, clazz, propertyInfo.name)
          } else {
            throw new PropertyAccessException(npe, "NullPointerException occurred while calling", true, clazz, propertyInfo.name)
          }

        case iae: IllegalArgumentException =>
          if (value == null && methodHandle.`type`.parameterArray()(1).isPrimitive) {
            target match
              case e: Entity[_] => throw new PropertyAccessException(iae, "Null value was assigned to primitive type of " + e.id, true, clazz, propertyInfo.name)
              case _ => throw new PropertyAccessException(iae, "Null value was assigned to a property of primitive type", true, clazz, propertyInfo.name)
          } else {
            val expectedType = methodHandle.`type`.parameterArray()(1)
            throw new PropertySetterAccessException(iae, clazz, propertyInfo.name, expectedType, target, value.getClass)
          }
        case e: Exception => Throwables.propagate(e)
      }
    }

    override def getMethod: Method = null

    override def getMethodName: String = null

    override def toString: String = "BasicSetter(" + clazz.getName + '.' + propertyInfo.name + ')'
  }

  final class BasicGetter(beanInfo: BeanInfo, pi: BeanInfo.PropertyInfo) extends Getter {
    private def clazz: Class[_] = beanInfo.clazz

    override def get(target: Object): Object = {
      val result: AnyRef = target match {
        case None => null
        case Some(t) => pi.getter.invoke(t)
        case _ => pi.getter.invoke(target)
      }
      if pi.isOptional then
        result match {
          case null => null
          case None => null
          case Some(r) => r.asInstanceOf[AnyRef]
        }
      else result
    }

    override def getForInsert(target: Object, mergeMap: ju.Map[Object, Object], session: SharedSessionContractImplementor): Object = {
      get(target)
    }

    override def getReturnTypeClass: Class[_] = {
      pi.meta.typeinfo.clazz
    }

    override def getReturnType: Type = getReturnTypeClass

    /**
     * Disable Hibernate Using member to introspate javaType.
     * found scala.Option eg.
     *
     * @return
     */
    override def getMember: Member = beanInfo.getGetterMethod(pi.name).orNull

    override def getMethod: Method = beanInfo.getGetterMethod(pi.name).orNull

    override def getMethodName: String = pi.name

    override def toString: String = "BasicGetter(" + clazz.getName + '.' + pi.name + ')'
  }
}

class ScalaPropertyAccessStrategy extends PropertyAccessStrategy {

  import ScalaPropertyAccessor.*

  override def buildPropertyAccess(theClass: Class[_], propertyName: String, setterRequired: Boolean): PropertyAccess = {
    val beanInfo = BeanInfos.get(theClass)
    beanInfo.properties.get(propertyName) match {
      case Some(p) =>
        new ScalaPropertyAccessBasicImpl(this,
          new BasicGetter(beanInfo, p),
          new BasicSetter(beanInfo, p))

      case None => throw new PropertyNotFoundException("Could not find a setter for " + propertyName + " in class " + theClass.getName)
    }
  }
}

class ScalaPropertyAccessBasicImpl(strategy: PropertyAccessStrategy, getter: Getter, setter: Setter) extends PropertyAccess {
  override def getPropertyAccessStrategy: PropertyAccessStrategy = strategy

  override def getGetter: Getter = getter

  override def getSetter: Setter = setter
}
