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

package org.beangle.data.hibernate.nativeimage

import org.beangle.commons.config.XmlDocs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.model.annotation.{archive, code, config, flash, flow, log, shard, temp}
import org.beangle.data.orm.cfg.{EJB3NamingPolicy, Profiles, RailsNamingPolicy}
import org.beangle.data.orm.*
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.Database

import java.io.{File, PrintWriter}
import scala.collection.mutable

/** 构建期工具：为使用 beangle-data 的应用生成 GraalVM native-image 配置。
 *
 * 思路与 Quarkus 的 build-time processing 一致：在构建 JVM 上执行 `Mappings.autobind()`，
 * 枚举出全部实体/组件/值类型/枚举类，以及库自身会被反射实例化的类，
 * 输出 reflect-config.json / resource-config.json / proxy-config.json / serialization-config.json
 * 和推荐的 native-image 参数。
 *
 * 用法：
 * {{{
 *   java -cp <应用+beangle-data 全部 classpath> org.beangle.data.hibernate.nativeimage.NativeImageConfigGen \
 *       --output target/native-image --config "classpath*:beangle.xml" \
 *       --engine PostgreSQL --dialect org.hibernate.dialect.PostgreSQLDialect \
 *       --jdbc-driver org.postgresql.Driver --cache-provider com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider
 * }}}
 */
object NativeImageConfigGen {

  final case class Options(
      output: File,
      config: String,
      engine: String,
      dialect: String,
      cacheProvider: String,
      jdbcDrivers: Seq[String])

  def main(args: Array[String]): Unit = {
    var output = new File("target/native-image")
    var config = "classpath*:beangle.xml"
    var engine = "PostgreSQL"
    var dialect = "org.hibernate.dialect.H2Dialect"
    var cacheProvider = "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider"
    val jdbcDrivers = new mutable.ListBuffer[String]
    jdbcDrivers += "org.h2.Driver"
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--output" => output = new File(args(i + 1)); i += 2
        case "--config" => config = args(i + 1); i += 2
        case "--engine" => engine = args(i + 1); i += 2
        case "--dialect" => dialect = args(i + 1); i += 2
        case "--cache-provider" => cacheProvider = args(i + 1); i += 2
        case "--jdbc-driver" => jdbcDrivers += args(i + 1); i += 2
        case other => throw new IllegalArgumentException("Unknown argument: " + other)
      }
    }
    run(Options(output, config, engine, dialect, cacheProvider, jdbcDrivers.toSeq))
  }

  def run(opts: Options): Unit = {
    val mappings = buildMappings(opts.config, opts.engine)
    val classes = collectClasses(mappings)
    opts.jdbcDrivers foreach (d => add(classes, d))
    add(classes, opts.dialect)
    add(classes, opts.cacheProvider)
    // 库自身固定反射点已内嵌于 beangle-data 各 jar 的 META-INF/native-image（见 LibraryNativeImageConfig），
    // 这里只生成应用侧配置

    opts.output.mkdirs()
    write(opts.output, "reflect-config.json", reflectConfig(classes.toSeq))
    write(opts.output, "resource-config.json", resourceConfig)
    write(opts.output, "proxy-config.json", "[]\n")
    write(opts.output, "serialization-config.json", serializationConfig(classes.toSeq))
    write(opts.output, "native-image-args.txt", nativeImageArgs(opts))
    write(opts.output, "classes.txt", classes.toSeq.map(_.getName).sorted.mkString("\n") + "\n")
    println(s"Native-image configs generated to ${opts.output.getAbsolutePath}")
  }

  private def buildMappings(configPattern: String, engineName: String): Mappings = {
    val engine = Engines.forName(engineName)
    val config = XmlDocs.load(configPattern).getOrElse(throw new RuntimeException(s"Cannot find $configPattern"))
    val mappings = new Mappings(new Database(engine), new Profiles(config))
    mappings.autobind()
    mappings
  }

  /** 枚举 native-image 需要反射注册的类：实体/组件/值类型/枚举/自定义类型/模块/命名策略等
   */
  private def collectClasses(mappings: Mappings): mutable.LinkedHashSet[Class[_]] = {
    val out = new mutable.LinkedHashSet[Class[_]]
    def add(c: Class[_]): Unit = if (null != c && !isJdkClass(c)) out += c

    def walkStruct(st: OrmStructType): Unit = {
      st.properties.values foreach {
        case sp: OrmSingularProperty =>
          sp.propertyType match {
            case et: OrmEmbeddableType => add(et.clazz); walkStruct(et)
            case et: OrmEntityType => add(et.clazz)
            case bt: OrmBasicType => add(bt.clazz)
          }
        case pp: OrmPluralProperty =>
          pp.element match {
            case et: OrmEmbeddableType => add(et.clazz); walkStruct(et)
            case et: OrmEntityType => add(et.clazz)
            case bt: OrmBasicType => add(bt.clazz)
          }
          pp match {
            case mp: OrmMapProperty =>
              mp.key match {
                case et: OrmEntityType => add(et.clazz)
                case bt: OrmBasicType => add(bt.clazz)
                case _ =>
              }
            case _ =>
          }
      }
    }

    mappings.classTypes.values foreach { et => add(et.clazz); walkStruct(et) }
    mappings.valueTypes foreach add
    mappings.enumTypes foreach (n => tryLoad(out, n))
    mappings.typeDefs.values foreach (td => tryLoad(out, td.clazz))
    mappings.profiles.modules foreach (m => add(m.getClass))
    add(classOf[RailsNamingPolicy]); add(classOf[EJB3NamingPolicy])
    annotationClasses foreach add
    out
  }

  /** 是否需要反射注册：排除基本类型、数组与 JDK/Scala 标准类
   */
  private def isJdkClass(c: Class[_]): Boolean = {
    val n = c.getName
    c.isPrimitive || c.isArray ||
      n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("jakarta.") || n.startsWith("scala.")
  }

  private def tryLoad(out: mutable.LinkedHashSet[Class[_]], name: String): Unit = {
    try {
      val c = ClassLoaders.load(name)
      if (!isJdkClass(c)) out += c
    } catch {
      case _: Throwable => System.err.println(s"WARN: cannot load class $name")
    }
  }

  private def add(classes: mutable.LinkedHashSet[Class[_]], name: String): Unit = tryLoad(classes, name)

  private def annotationClasses: Seq[Class[_]] = Seq(
    // org.beangle.data.model.annotation.* 为库自身注解，已内嵌于 model jar（见 LibraryNativeImageConfig）
    Class.forName("jakarta.persistence.Entity"), Class.forName("jakarta.persistence.Embeddable"),
    Class.forName("org.beangle.commons.bean.component")
  )

  // 库自身固定反射点见 LibraryNativeImageConfig（内嵌于 jar 的 META-INF/native-image）

  // ---- JSON 输出（类名不含引号/反斜杠，直接拼接即可）----

  private[nativeimage] def reflectConfig(classes: Seq[Class[_]]): String = {
    val sb = new StringBuilder("[\n")
    classes.distinct.sortBy(_.getName) foreach { c =>
      sb.append("  {\"name\":\"").append(c.getName).append("\"");
      if (c.isAnnotation) sb.append(",\"allDeclaredMethods\":true");
      else sb.append(",\"allDeclaredConstructors\":true,\"allDeclaredMethods\":true,\"allDeclaredFields\":true,\"queryAllPublicMethods\":true");
      sb.append("},\n")
    }
    if (sb.toString.endsWith(",\n")) sb.setLength(sb.length - 2)
    sb.append("\n]\n")
    sb.toString
  }

  private def serializationConfig(classes: Seq[Class[_]]): String = {
    val sb = new StringBuilder("[\n")
    classes.distinct.sortBy(_.getName) foreach { c =>
      sb.append("  {\"name\":\"").append(c.getName).append("\"},\n")
    }
    if (sb.toString.endsWith(",\n")) sb.setLength(sb.length - 2)
    sb.append("\n]\n")
    sb.toString
  }

  private def resourceConfig: String = {
    // 库自身资源（META-INF/services、META-INF/beangle/ddl、*.zh_CN）已内嵌于各 jar
    resourcesConfig(Seq("beangle\\.xml", "logback\\.xml", "db\\.properties"))
  }

  private[nativeimage] def resourcesConfig(patterns: Seq[String]): String =
    "{\n  \"resources\": [\n" + patterns.map(p => s"    {\"pattern\":\"$p\"}").mkString(",\n") + "\n  ]\n}\n"

  private def nativeImageArgs(opts: Options): String = {
    val sb = new StringBuilder
    sb.append("# 推荐 native-image 参数（需按应用微调）\n");
    sb.append("-H:+ReportExceptionStackTraces\n");
    sb.append("--enable-url-protocols=jar,resource\n");
    sb.append("--initialize-at-build-time=org.hibernate,org.beangle\n");
    sb.append("--initialize-at-run-time=org.h2.Driver\n");
    sb.append("-Dhibernate.bytecode.use_reflection_optimizer=false\n");
    sb.append("# 反射/资源/序列化配置（由本工具生成）\n");
    sb.append("-H:ReflectionConfigurationFiles=").append(new File(opts.output, "reflect-config.json").getAbsolutePath).append("\n");
    sb.append("-H:ResourceConfigurationFiles=").append(new File(opts.output, "resource-config.json").getAbsolutePath).append("\n");
    sb.append("-H:SerializationConfigurationFiles=").append(new File(opts.output, "serialization-config.json").getAbsolutePath).append("\n");
    sb.append("-H:DynamicProxyConfigurationFiles=").append(new File(opts.output, "proxy-config.json").getAbsolutePath).append("\n");
    sb.toString
  }

  private def write(dir: File, name: String, content: String): Unit = {
    val pw = new PrintWriter(new File(dir, name), "UTF-8")
    try pw.print(content) finally pw.close()
  }
}