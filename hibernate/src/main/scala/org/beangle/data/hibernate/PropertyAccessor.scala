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

import java.lang.reflect.{ Member, Method }
import java.{ util => ju }
import org.beangle.commons.lang.Throwables
import org.beangle.commons.lang.reflect.BeanInfos
import org.hibernate.{ PropertyAccessException, PropertyNotFoundException, PropertySetterAccessException }
import org.hibernate.engine.spi.{ SessionFactoryImplementor, SessionImplementor }
import java.{ util => ju }
import org.hibernate.property.access.spi.Getter
import org.hibernate.property.access.spi.Setter
import org.hibernate.property.access.spi.PropertyAccessStrategy
import org.hibernate.property.access.spi.PropertyAccess
import org.hibernate.engine.spi.SharedSessionContractImplementor

object PropertyAccessor {

  def createSetter(theClass: Class[_], propertyName: String): Setter = {
    BeanInfos.get(theClass).properties.get(propertyName) match {
      case Some(p) => new BasicSetter(theClass, p.setter.get, propertyName, p.typeinfo.isOptional)
      case None    => throw new PropertyNotFoundException("Could not find a setter for " + propertyName + " in class " + theClass.getName())
    }
  }

  def createGetter(theClass: Class[_], propertyName: String): Getter = {
    BeanInfos.get(theClass).properties.get(propertyName) match {
      case Some(p) => new BasicGetter(theClass, p.getter.get, p.clazz, propertyName, p.typeinfo.isOptional)
      case None    => throw new PropertyNotFoundException("Could not find a getter for " + propertyName + " in class " + theClass.getName())
    }
  }

  final class BasicSetter(val clazz: Class[_], val method: Method, val propertyName: String, optional: Boolean) extends Setter {
    def set(target: Object, value: Object, factory: SessionFactoryImplementor): Unit = {
      try {
        val arg = if (optional) {
          if (value.isInstanceOf[Option[_]]) value else Option(value)
        } else {
          value
        }
        method.invoke(target, arg)
      } catch {
        case npe: NullPointerException =>
          if (value == null && method.getParameterTypes()(0).isPrimitive) {
            throw new PropertyAccessException(npe, "Null value was assigned to a property of primitive type", true, clazz, propertyName)
          } else {
            throw new PropertyAccessException(npe, "NullPointerException occurred while calling", true, clazz, propertyName)
          }

        case iae: IllegalArgumentException =>
          if (value == null && method.getParameterTypes()(0).isPrimitive) {
            throw new PropertyAccessException(iae,
              "Null value was assigned to a property of primitive type", true, clazz, propertyName);
          } else {
            val expectedType = method.getParameterTypes()(0)
            throw new PropertySetterAccessException(iae, clazz, propertyName, expectedType, target, value.getClass);
          }
        case e: Exception => Throwables.propagate(e)
      }
    }

    def getMethod() = method

    def getMethodName(): String = method.getName()

    def readResolve(): Object = createSetter(clazz, propertyName);

    override def toString(): String = "BasicSetter(" + clazz.getName() + '.' + propertyName + ')'
  }

  final class BasicGetter(val clazz: Class[_], val method: Method, val returnType: Class[_], val propertyName: String, optional: Boolean) extends Getter {
    def get(target: Object): Object = {
      val result = target match {
        case None    => null
        case Some(t) => method.invoke(t)
        case _       => method.invoke(target)
      }
      if (optional) {
        result match {
          case null    => null
          case None    => null
          case Some(r) => r.asInstanceOf[AnyRef]
        }
      } else {
        result
      }
    }

    def getForInsert(target: Object, mergeMap: ju.Map[_, _], session: SharedSessionContractImplementor): Object = {
      get(target)
    }

    def getReturnType(): Class[_] = returnType

    def getMember(): Member = method

    def getMethod(): Method = method

    def getMethodName(): String = method.getName()

    override def toString(): String = "BasicGetter(" + clazz.getName() + '.' + propertyName + ')'

    def readResolve(): Object = createGetter(clazz, propertyName)
  }
}

class ScalaPropertyAccessStrategy extends PropertyAccessStrategy {

  override def buildPropertyAccess(theClass: Class[_], propertyName: String): PropertyAccess = {
    new ScalaPropertyAccessBasicImpl(this, PropertyAccessor.createGetter(theClass, propertyName),
      PropertyAccessor.createSetter(theClass, propertyName))
  }

}

class ScalaPropertyAccessBasicImpl(strategy: PropertyAccessStrategy, getter: Getter, setter: Setter)
    extends PropertyAccess {
  override def getPropertyAccessStrategy(): PropertyAccessStrategy = {
    strategy
  }
  override def getGetter: Getter = getter

  override def getSetter: Setter = setter
}
