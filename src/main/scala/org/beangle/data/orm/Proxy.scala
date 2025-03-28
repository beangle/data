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

package org.beangle.data.orm

import javassist.*
import javassist.compiler.Javac
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.reflect.BeanInfo.PropertyInfo
import org.beangle.commons.lang.reflect.{BeanInfos, Reflections}
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.lang.{ClassLoaders, Primitives}
import org.beangle.commons.logging.Logging

/**
 * @author chaostone
 */
private[orm] object Proxy extends Logging {

  trait ModelProxy {
    def lastAccessed: java.util.LinkedHashSet[String]
  }

  trait EntityProxy extends ModelProxy

  trait ComponentProxy extends ModelProxy {
    def setParent(proxy: ModelProxy, path: String): Unit
  }

  private var pool: ClassPool = _

  private var proxies: collection.mutable.HashMap[String, Class[_]] = _

  init()

  def init(): Unit = {
    if (null == pool) {
      pool = new ClassPool(true)
      pool.appendClassPath(new LoaderClassPath(ClassLoaders.defaultClassLoader))
    }
    if (null == proxies) {
      proxies = new collection.mutable.HashMap[String, Class[_]]
    }
  }

  def generate(clazz: Class[_]): EntityProxy = {
    init()
    val proxyClassName = clazz.getSimpleName + "_proxy"
    val classFullName = clazz.getName + "_proxy"
    var exised = proxies.getOrElse(classFullName, null)
    if (null == exised) {
      proxies.synchronized {
        exised = proxies.getOrElse(classFullName, null)
        if (null == exised) exised = generateProxyClass(proxyClassName, classFullName, clazz)
      }
    }
    Reflections.newInstance(exised).asInstanceOf[EntityProxy]
  }

  private def generateProxyClass(proxyClassName: String, classFullName: String, clazz: Class[_]): Class[_] = {
    val watch = new Stopwatch(true)
    val cct = pool.makeClass(classFullName)
    if (clazz.isInterface) cct.addInterface(pool.get(clazz.getName))
    else cct.setSuperclass(pool.get(clazz.getName))
    cct.addInterface(pool.get(classOf[EntityProxy].getName))
    val javac = new Javac(cct)
    cct.addField(javac.compile("public java.util.LinkedHashSet _lastAccessed;").asInstanceOf[CtField])

    val manifest = BeanInfos.get(clazz)
    val componentTypes = Collections.newMap[String, Class[_]]
    manifest.properties foreach {
      case (name, p) =>
        if (p.readable) {
          val getter = p.getter.get
          val value = if (p.typeinfo.isOptional) "null" else Primitives.defaultLiteral(p.clazz)
          val javaTypeName = toJavaType(p)
          val body = s"public $javaTypeName ${getter.getName}() { return $value;}"
          val ctmod = javac.compile(body).asInstanceOf[CtMethod]
          if (Jpas.isComponent(p.clazz)) {
            componentTypes += (name -> generateComponent(p.clazz, name + "."))
            ctmod.setBody("{return super." + getter.getName + "();}")
          } else {
            ctmod.setBody("{_lastAccessed.add( \"" + name + "\");return " + value + ";}")
          }
          cct.addMethod(ctmod)
        }
    }
    val ctor = javac.compile("public " + proxyClassName + "(){}").asInstanceOf[CtConstructor]
    val ctorBody = new StringBuilder("{ _lastAccessed = new java.util.LinkedHashSet();")
    var componentIdx = 0
    componentTypes foreach {
      case (name, componentClass) =>
        val p = manifest.properties(name)
        val setName = p.setter.get.getName
        //User component variable instead call get method,otherwise with add name into _lastAccessed.
        val componentVariable = "comp" + componentIdx
        ctorBody ++= componentClass.getName + " " + componentVariable + " = new " + componentClass.getName + "();"
        ctorBody ++= (setName + "(" + componentVariable + ");" + componentVariable + ".setParent(this," + "\"" + name + ".\");")
        componentIdx += 1
    }
    ctorBody ++= "}"
    ctor.setBody(ctorBody.toString)
    cct.addConstructor(ctor)

    val ctmod = javac.compile("public java.util.LinkedHashSet lastAccessed() { return null;}").asInstanceOf[CtMethod]
    ctmod.setBody("{return _lastAccessed;}")
    cct.addMethod(ctmod)
    val maked = cct.toClass(clazz)
    logger.debug(s"generate $classFullName using $watch")
    //cct.debugWriteFile("/tmp/model/")
    proxies.put(classFullName, maked)
    maked
  }

  private def generateComponent(clazz: Class[_], path: String): Class[_] = {
    val proxyClassName = clazz.getSimpleName + "_proxy"
    val classFullName = clazz.getName + "_proxy"
    val exised = proxies.getOrElse(classFullName, null)
    if (null != exised) return exised

    val cct = pool.makeClass(classFullName)
    if (clazz.isInterface) cct.addInterface(pool.get(clazz.getName))
    else cct.setSuperclass(pool.get(clazz.getName))
    cct.addInterface(pool.get(classOf[ComponentProxy].getName))
    val javac = new Javac(cct)

    cct.addField(javac.compile("public " + classOf[ModelProxy].getName + " _parent;").asInstanceOf[CtField])
    cct.addField(javac.compile("public java.lang.String _path=null;").asInstanceOf[CtField])

    val manifest = BeanInfos.get(clazz)
    val componentTypes = Collections.newMap[String, Class[_]]
    manifest.properties foreach {
      case (name, p) =>
        if (p.readable) {
          val getter = p.getter.get
          val value = if (p.typeinfo.isOptional) "null" else Primitives.defaultLiteral(p.clazz)
          val javaTypeName = toJavaType(p)
          val body = s"public $javaTypeName ${getter.getName}() { return $value;}"
          val ctmod = javac.compile(body).asInstanceOf[CtMethod]
          val accessed = "_parent.lastAccessed()"
          if (Jpas.isComponent(p.clazz)) {
            componentTypes += (name -> generateComponent(p.clazz, path + name + "."))
            ctmod.setBody("{" + accessed + ".add(_path + \"" + name + "\");return super." + getter.getName + "();}")
          } else {
            val value = Primitives.defaultLiteral(p.clazz)
            ctmod.setBody("{" + accessed + ".add(_path + \"" + name + "\");return " + value + ";}")
          }
          cct.addMethod(ctmod)
        }
    }
    val ctor = javac.compile("public " + proxyClassName + "(){}").asInstanceOf[CtConstructor]
    ctor.setBody("{this._parent=null;this._path=null;}")
    cct.addConstructor(ctor)

    //implement setParent and lastAccessed
    var ctmod = javac.compile("public void setParent(" + classOf[ModelProxy].getName + " proxy,String path) { return null;}").asInstanceOf[CtMethod]
    val setParentBody = new StringBuilder("{this._parent=$1;this._path=$2;")
    var componentIdx = 0
    componentTypes foreach {
      case (name, componentClass) =>
        val p = manifest.properties(name)
        val setName = p.setter.get.getName
        //User component variale instead call get method,otherwise with add name into _lastAccessed.
        val componentVariable = "comp" + componentIdx
        setParentBody ++= componentClass.getName + " " + componentVariable + " = new " + componentClass.getName + "();"
        setParentBody ++= (setName + "(" + componentVariable + ");" + componentVariable + ".setParent(this," + "\"" + path + name + ".\");")
        componentIdx += 1
    }
    setParentBody ++= "}"
    ctmod.setBody(setParentBody.toString)

    cct.addMethod(ctmod)
    ctmod = javac.compile("public java.util.LinkedHashSet lastAccessed() { return null;}").asInstanceOf[CtMethod]
    ctmod.setBody("{return _parent.lastAccessed();}")
    cct.addMethod(ctmod)

    val maked = cct.toClass(clazz)
    // cct.debugWriteFile("/tmp/model/")
    proxies.put(classFullName, maked)
    maked
  }

  private def toJavaType(p: PropertyInfo): String = {
    if (p.typeinfo.isOptional) {
      "scala.Option"
    } else {
      if (p.clazz.isArray) {
        p.clazz.getComponentType.getName + "[]"
      } else {
        p.clazz.getName
      }
    }
  }

  def cleanup(): Unit = {
    proxies.clear()
    proxies = null
    pool = null
  }
}
