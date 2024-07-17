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

package org.beangle.data.orm.tool

import org.beangle.commons.io.{Dirs, IOs, ResourcePatternResolver}
import org.beangle.commons.lang.SystemInfo
import org.beangle.data.orm.Mappings
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.Database
import org.hibernate.bytecode.enhance.spi.{DefaultEnhancementContext, Enhancer, UnloadedClass}

import java.io.{ByteArrayInputStream, File, FileOutputStream}
import java.util.Locale

object StaticEnhancer {
  def main(args: Array[String]): Unit = {
    val engine = Engines.forName("PostgreSQL")
    val ormLocations = ResourcePatternResolver.getResources("classpath*:META-INF/beangle/orm.xml")
    val database = new Database(engine)
    val mappings = new Mappings(database, ormLocations)
    mappings.locale = Locale.SIMPLIFIED_CHINESE
    mappings.autobind()

    val se = new StaticEnhancer
    se.enhance(new File(SystemInfo.tmpDir), mappings)
  }
}

class StaticEnhancer {

  import org.hibernate.bytecode.internal.BytecodeProviderInitiator.buildDefaultBytecodeProvider

  def enhance(dir: File, mappings: Mappings): Unit = {
    val bytecodeProvider = buildDefaultBytecodeProvider
    val context = new DefaultEnhancementContext() {
      override def isEntityClass(classDescriptor: UnloadedClass): Boolean = {
        true
      }
    }

    try {
      val enhancer = bytecodeProvider.getEnhancer(context)
      for (entityType <- mappings.entityTypes.values) {
        val clazz = entityType.clazz
        enhancer.discoverTypes(clazz.getName, readClazz(clazz))
      }
      for (entityType <- mappings.entityTypes.values) {
        val clazz = entityType.clazz
        val enhancedBytecode = doEnhancement(clazz, enhancer)
        if (enhancedBytecode != null) {
          val target = dir.getAbsolutePath + "/" + clazz.getName.replace('.', '/') + ".class"
          val file = new File(target)
          Dirs.on(file.getParentFile).mkdirs()
          IOs.copy(new ByteArrayInputStream(enhancedBytecode), new FileOutputStream(target))
        }
      }
    }
    finally bytecodeProvider.resetCaches
  }

  private def doEnhancement(clazz: Class[_], enhancer: Enhancer): Array[Byte] = {
    try {
      val className = clazz.getName
      val rs = enhancer.enhance(className, readClazz(clazz))
      rs
    } catch {
      case e: Exception =>
        e.printStackTrace()
        val msg = "Unable to enhance class: " + clazz.getName
        null
    }
  }

  private def readClazz(clazz: Class[_]): Array[Byte] = {
    val filePath = clazz.getName.replace('.', '/') + ".class"
    val is = clazz.getClassLoader.getResourceAsStream(filePath)
    IOs.readBytes(is)
  }

}
