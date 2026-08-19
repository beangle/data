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

import org.beangle.commons.config.XmlDocs
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.data.orm.cfg.Profiles
import org.beangle.data.orm.{Jpas, Mappings}
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.Database

import java.io.File
import scala.collection.mutable

/** 构建期工具：依据 MappingModule 的绑定，预生成 AccessTracker 的 $Tracker 类。
 *
 * GraalVM native-image 不允许运行期生成字节码，因此 `declare{...}` DSL 所依赖的
 * `AccessTracker` 子类必须在构建期（JVM 上）生成，随应用一同打包。
 *
 * 用法：
 * {{{
 *   java -cp <model+hibernate 全部 classpath> org.beangle.data.dao.AccessTrackerGenerator \
 *       --output target/generated-trackers --config "classpath*:beangle.xml" [--engine postgresql]
 * }}}
 *
 * 生成的 .class 文件需加入应用 classpath（或打进应用 jar），运行期 `AccessTracker.generate`
 * 会优先从 classpath 加载同名 `$Tracker` 类。
 */
object AccessTrackerGenerator {

  def main(args: Array[String]): Unit = {
    var output: File = new File("target/generated-trackers")
    var config = "classpath*:beangle.xml"
    var engineName = "postgresql"
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--output" => output = new File(args(i + 1)); i += 2
        case "--config" => config = args(i + 1); i += 2
        case "--engine" => engineName = args(i + 1); i += 2
        case other => throw new IllegalArgumentException("Unknown argument: " + other)
      }
    }
    val classes = generate(config, engineName, output)
    println(s"Generated ${classes.size} tracker classes to ${output.getAbsolutePath}")
  }

  /** 依据 beangle.xml 中的 MappingModule 构建 Mappings 并生成全部 tracker 类
   *
   * @return 生成 tracker 的实体/组件类列表
   */
  def generate(configPattern: String, engineName: String, outputDir: File): Seq[Class[_]] = {
    val mappings = buildMappings(configPattern, engineName)
    val classes = collectTrackedClasses(mappings)
    if (outputDir.exists()) deleteRecursively(outputDir)
    outputDir.mkdirs()
    classes foreach { clazz =>
      val trackerClazzName = clazz.getName + AccessTracker.TrackerNamePostfix
      val unloaded = AccessTracker.buildUnloaded(clazz, trackerClazzName)
      unloaded.saveIn(outputDir)
    }
    classes.toSeq
  }

  private def buildMappings(configPattern: String, engineName: String): Mappings = {
    val engine = Engines.forName(engineName)
    val config = XmlDocs.load(configPattern).getOrElse(throw new RuntimeException(s"Cannot find $configPattern"))
    val mappings = new Mappings(new Database(engine), new Profiles(config))
    mappings.autobind()
    mappings
  }

  /** 与 AccessTracker.generate 的递归逻辑保持一致：实体 + 组件 + 实体/组件属性
   */
  private def collectTrackedClasses(mappings: Mappings): mutable.LinkedHashSet[Class[_]] = {
    val out = new mutable.LinkedHashSet[Class[_]]
    def collect(clazz: Class[_]): Unit = {
      if (out.add(clazz)) {
        BeanInfos.get(clazz).properties.values foreach { p =>
          if (p.readable && p.writable && (Jpas.isComponent(p.clazz) || Jpas.isEntity(p.clazz))) collect(p.clazz)
        }
      }
    }
    mappings.classTypes.keys foreach collect
    out
  }

  private def deleteRecursively(dir: File): Unit = {
    val children = dir.listFiles()
    if (children != null) children foreach { f =>
      if (f.isDirectory) deleteRecursively(f) else f.delete()
    }
    dir.delete()
  }
}
