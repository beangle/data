/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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
package org.beangle.data.jpa.hibernate.tool

import java.io.{ File, FileOutputStream, StringWriter, Writer }

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.{ Charsets, ClassLoaders, Primitives }
import org.beangle.commons.lang.Strings.isBlank
import org.beangle.commons.lang.annotation.beta
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.jpa.hibernate.cfg.{ ConfigurationBuilder, OverrideConfiguration }
import org.hibernate.`type`.{ BasicType, Type }
import org.hibernate.mapping.{ Component, Property, RootClass }

object HbmLint {
  def main(args: Array[String]): Unit = {
    val target = if (args.length > 0) args(0) else "/tmp"
    if (!(new File(target).exists)) {
      println(target + " not exists.")
      return
    }
    var pattern: String = null
    if (args.length > 1) pattern = args(1)
    new HbmLint().check(target, pattern)
  }
}

@beta
class HbmLint {
  val configuration = ConfigurationBuilder.build(new OverrideConfiguration)
  val mapping = configuration.buildMapping

  def check(dir: String, packageName: String): Unit = {
    val iterpc = configuration.getClassMappings
    val writer = new StringWriter
    while (iterpc.hasNext) {
      val pc = iterpc.next
      val clazz = pc.getMappedClass
      val meta = BeanManifest.get(clazz)
      if (isBlank(packageName) || clazz.getPackage.getName.startsWith(packageName)) {
        if (pc.isInstanceOf[RootClass]) {
          checkProperty(clazz, meta, pc.getIdentifierProperty, writer)
          val pIter = pc.getPropertyIterator
          while (pIter.hasNext()) {
            val p = pIter.next().asInstanceOf[Property]
            checkProperty(clazz, meta, p, writer)
          }
        }
      }
    }
    if (writer.getBuffer.length() > 0) {
      IOs.write(writer.getBuffer.toString(), new FileOutputStream(dir + "/hbmlint.txt"), Charsets.UTF_8)
      println(s"write lint result to $dir/hbmlint.txt")
    } else {
      println("Hbm files is ok.")
    }
  }

  private def checkProperty(clazz: Class[_], meta: BeanManifest, p: Property, writer: Writer): Unit = {
    if (p.getColumnSpan == 1) {
      meta.getPropertyType(p.getName) match {
        case Some(ptype) =>
          val clz = Primitives.wrap(ptype)
          p.getType match {
            case bpt: BasicType =>
              if (!clz.isAssignableFrom(p.getType.getReturnedClass)) {
                var matched = false
                bpt.getRegistrationKeys foreach { typekey =>
                  if (typekey.contains(".")) {
                    try {
                      val keyClass = ClassLoaders.loadClass(typekey)
                      if (clz.isAssignableFrom(keyClass)) matched = true
                    } catch {
                      case e: Throwable =>
                    }
                  }
                }
                if (!matched)
                  writer.write(s"${clazz.getName}'s ${p.getName} type mismatch,require [${clz.getName}] found [${p.getType.getReturnedClass.getName}(${p.getType.getClass.getName})]\n");
              }
            case pt: Type =>
              if (!clz.isAssignableFrom(p.getType.getReturnedClass))
                writer.write(s"${clazz.getName}'s ${p.getName} type mismatch,require [${clz.getName}] found [${pt.getReturnedClass.getName}(${pt.getClass.getName})]\n");
          }
        case None => writer.write(s"Cannot find ${clazz.getName}'s ${p.getName} \n")
      }
    } else if (p.getColumnSpan > 1) {
      val pc = p.getValue.asInstanceOf[Component]
      val componentClass = pc.getComponentClass
      val cpi = pc.getPropertyIterator
      val pcMeta = BeanManifest.get(componentClass)
      while (cpi.hasNext) {
        checkProperty(clazz, pcMeta, cpi.next.asInstanceOf[Property], writer)
      }
    }
  }
}