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

package org.beangle.data.hibernate.proxy

import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.reflect.Reflections
import org.beangle.data.orm.MappingModule
import org.hibernate.engine.spi.PrimeAmongSecondarySupertypes
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.ProxyConfiguration

import java.io.File
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import net.bytebuddy.{ByteBuddy, ClassFileVersion, NamingStrategy}
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.dynamic.scaffold.TypeValidation
import net.bytebuddy.implementation.bytecode.assign.Assigner
import net.bytebuddy.implementation.{FieldAccessor, Implementation, MethodDelegation, SuperMethodCall}
import net.bytebuddy.matcher.ElementMatchers.{isDeclaredBy, isFinalizer, isSynthetic, isVirtual, named, nameStartsWith, not, returns, takesNoArguments}
import net.bytebuddy.pool.TypePool
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** 为 beangle.xml 声明的 MappingModule 所绑定的实体预生成 Hibernate 懒加载代理类。
 *
 * 构建期在构建 JVM 上运行（ByteBuddy 可用）：对每个可代理实体复刻 hibernate
 * `ByteBuddyProxyHelper` 的代理结构（子类化实体、实现 HibernateProxy、方法委托
 * `ProxyConfiguration.InterceptorDispatcher`）生成代理字节码，并输出 GraalVM 25+ 的
 * `reachability-metadata.json` 片段（`META-INF/native-image/beangle/data/`，按固定命名约定
 * 在 `reflection` 数组注册 `<Entity>$HibernateProxy` 的无参构造器、`writeReplace` 与
 * `allPublicMethods`）一起写入输出目录
 * （sbt 插件传入 `Compile / resourceManaged`，随 jar 打包；
 * `.class` 作为资源也在运行期 classpath 上，可按名加载），
 * 供运行期自定义 BytecodeProvider 使用（见 fork 的 PrebuiltProxyProvider）。
 *
 * Usage:
 * {{{
 * java -cp <classpath> org.beangle.data.hibernate.proxy.BeangleProxyGenerator \
 *   --registrars mappings.txt -o <resourceDir>
 * }}}
 *
 * 退出码与 MetaGenerator 一致：0 成功；1 确定性违约（类已存在但不是 MappingModule、
 * 绑定/生成失败）；2 声明类未找到（编译可能仍在进行，由调用方退避重试）。
 */
object BeangleProxyGenerator {

  private val ReachabilityMetadataFile = "META-INF/native-image/beangle/data/reachability-metadata.json"
  private val ProxyNamingSuffix = "HibernateProxy"
  private val PersistentFieldReaderPrefix = "$$_hibernate_read_"
  private val PersistentFieldWriterPrefix = "$$_hibernate_write_"

  def main(args: Array[String]): Unit = {
    val (registrarsFile, outDir) = parseArgs(args)
    val classNames = readLines(registrarsFile)
    if (classNames.isEmpty) {
      System.out.println(s"No mappings declared in $registrarsFile; proxy generation skipped")
      return
    }

    val modules = new mutable.ArrayBuffer[MappingModule]
    val failures = new mutable.ListBuffer[String]
    val missing = new mutable.ListBuffer[String]
    classNames foreach { name =>
      Reflections.tryGetInstance[MappingModule](name, null) match {
        case Some(module) =>
          try {
            module.registering()
            modules += module
            System.out.println(s"Registered ${module.getClass.getName}")
          } catch {
            // 编译未完成/引用类缺失：静默归类为缺失，交由调用方决定是否重试
            case _: ClassNotFoundException | _: LinkageError => missing += name
            case e: Throwable =>
              failures += s"$name failed while registering(): ${e.getClass.getName}: ${e.getMessage}"
          }
        case None =>
          // 伴生类存在才确定是"不是 MappingModule"；否则视为编译未完成，可重试
          if (ClassLoaders.exists(name + "$", null)) failures += s"$name is not a MappingModule"
          else missing += name
      }
    }
    if (failures.nonEmpty) {
      System.err.println("Failed to load declared MappingModule implementations:\n" + failures.mkString("\n"))
      System.exit(1)
    }
    if (missing.nonEmpty) {
      System.err.println(s"${missing.size} of ${classNames.size} declared MappingModule classes not found" +
        " (compilation may still be in progress):\n" + missing.mkString("\n"))
      System.exit(2)
    }

    val entities = mutable.LinkedHashMap.empty[String, Class[_]]
    val skipped = mutable.ListBuffer.empty[(String, String)]
    modules foreach { m =>
      m.entityTypes.values foreach { et =>
        val c = et.clazz
        if (!entities.contains(c.getName)) {
          proxiable(c) match {
            case Some(reason) => skipped += ((c.getName, reason))
            case None => entities.put(c.getName, c)
          }
        }
      }
    }
    skipped foreach { (name, reason) => System.out.println(s"Skipped proxy for $name: $reason") }

    val proxyNames = generate(entities, outDir, failures, missing)
    if (failures.nonEmpty) {
      System.err.println("Failed to generate Hibernate proxies:\n" + failures.mkString("\n"))
      System.exit(1)
    }
    if (missing.nonEmpty) {
      System.err.println(s"${missing.size} entity classes not found (compilation may still be in progress):\n" +
        missing.mkString("\n"))
      System.exit(2)
    }

    // 代理类名遵循 Hibernate ByteBuddy 命名约定（<Entity>$HibernateProxy），
    // 按约定直接写 reachability-metadata，无需回读已生成的字节码/遍历文件系统。
    if (proxyNames.nonEmpty) {
      writeReachabilityMetadata(proxyNames.keys.toSeq, outDir)
      System.out.println(s"Generated ${proxyNames.size} Hibernate proxy classes")
    } else {
      // 实体集合收缩为空：清掉历史产物，避免把过期的配置/字节码打包进 jar
      new File(outDir, ReachabilityMetadataFile).delete()
      System.out.println("No Hibernate proxy classes generated")
    }
  }

  /** 为每个可代理实体生成代理字节码（写输出目录，随资源打包），返回 实体类名→代理类名 映射。 */
  private def generate(entities: mutable.LinkedHashMap[String, Class[_]], outDir: File,
      failures: mutable.ListBuffer[String], missing: mutable.ListBuffer[String]): mutable.LinkedHashMap[String, String] = {
    val proxyNames = mutable.LinkedHashMap.empty[String, String]
    if (entities.isEmpty) return proxyNames
    val typePool = TypePool.Default.of(getClass.getClassLoader)
    val interfaces = java.util.List.of(TypeDescription.ForLoadedType.of(classOf[HibernateProxy]))
    entities foreach { (name, clazz) =>
      try {
        val resolution = typePool.describe(name)
        if (!resolution.isResolved) missing += name
        else {
          val unloaded = buildProxy(typePool, resolution.resolve(), interfaces)
          val mainName = unloaded.getTypeDescription().getName()
          unloaded.getAllTypes().asScala foreach { (td, bytes) =>
            val out = new File(outDir, td.getName.replace('.', '/') + ".class")
            out.getParentFile.mkdirs()
            Files.write(out.toPath, bytes)
          }
          proxyNames.put(name, mainName)
          System.out.println(s"Generated proxy $mainName for $name")
        }
      } catch {
        case _: ClassNotFoundException | _: LinkageError => missing += name
        case e: Throwable =>
          failures += s"proxy generation failed for $name: ${e.getClass.getName}: ${e.getMessage}"
      }
    }
    proxyNames
  }

  /** 复刻 hibernate `ByteBuddyProxyHelper`/`ByteBuddyState` 的代理结构（fork 已剔除这些实现）：
   * 子类化实体、实现 HibernateProxy、虚拟方法委托 `ProxyConfiguration.InterceptorDispatcher`、
   * 定义 `$$_hibernate_interceptor` 字段并实现 `ProxyConfiguration`，命名固定 `<Entity>$HibernateProxy`。
   */
  private def buildProxy(typePool: TypePool, persistentClass: TypeDescription,
      interfaces: java.util.List[TypeDescription]): DynamicType.Unloaded[?] = {
    val namingStrategy = new NamingStrategy.Suffixing(ProxyNamingSuffix,
      new NamingStrategy.Suffixing.BaseNameResolver.ForFixedValue(persistentClass.getTypeName))
    val builder: DynamicType.Builder[?] = new ByteBuddy(ClassFileVersion.JAVA_V21)
      .`with`(TypeValidation.DISABLED)
      .`with`(new Implementation.Context.Default.Factory.WithFixedSuffix("hibernate"))
      .ignore(isSynthetic().and(named("getMetaClass")).and(returns(td => "groovy.lang.MetaClass" == td.getName)))
      .`with`(namingStrategy)
      .subclass(if interfaces.size() == 1 then persistentClass else TypeDescription.ForLoadedType.of(classOf[Object]),
        ConstructorStrategy.Default.IMITATE_SUPER_CLASS_OPENING)
      .implement(interfaces)
      .method(isVirtual().and(not(isFinalizer())))
        .intercept(MethodDelegation.to(classOf[ProxyConfiguration.InterceptorDispatcher]))
      .method(nameStartsWith("$$_hibernate_").and(isVirtual())
          .and(not(nameStartsWith(PersistentFieldReaderPrefix)))
          .and(not(nameStartsWith(PersistentFieldWriterPrefix))))
        .intercept(SuperMethodCall.INSTANCE)
      .defineField(ProxyConfiguration.INTERCEPTOR_FIELD_NAME, classOf[ProxyConfiguration.Interceptor], Modifier.PRIVATE)
      .implement(classOf[ProxyConfiguration])
        .intercept(FieldAccessor.ofField(ProxyConfiguration.INTERCEPTOR_FIELD_NAME)
          .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC))
    // 忽略 PrimeAmongSecondarySupertypes 各 default 方法（asProxyConfiguration/asHibernateProxy 等），
    // 避免被拦截器接管，保留接口默认实现返回 this/null 的语义
    var finalBuilder = builder
    classOf[PrimeAmongSecondarySupertypes].getMethods.foreach { method =>
      finalBuilder = finalBuilder.ignoreAlso(
        isDeclaredBy(classOf[PrimeAmongSecondarySupertypes]).and(named(method.getName)).and(takesNoArguments()))
      finalBuilder = finalBuilder.ignoreAlso(
        isDeclaredBy(method.getReturnType).and(named(method.getName)).and(takesNoArguments()))
    }
    finalBuilder.make(typePool)
  }

  /** 可代理性检查：接口/抽象/final/无公开无参构造器 均不可代理（Quarkus 同款规则）。 */
  private def proxiable(clazz: Class[_]): Option[String] = {
    if (clazz.isInterface) Some("interface")
    else if (Modifier.isAbstract(clazz.getModifiers)) Some("abstract")
    else if (Modifier.isFinal(clazz.getModifiers)) Some("final")
    else if (!hasPublicNoArgConstructor(clazz)) Some("no public no-arg constructor")
    else None
  }

  private def hasPublicNoArgConstructor(clazz: Class[_]): Boolean =
    clazz.getDeclaredConstructors.exists(c => c.getParameterCount == 0 && Modifier.isPublic(c.getModifiers))

  /** 代理类名固定为 `<Entity>$HibernateProxy`（fork 的 PrebuiltProxyProvider 与
   * 生成器共用 Suffixing 命名策略），按约定直接输出 GraalVM 25+ 的
   * reachability-metadata.json（写 `outDir` 下的
   * `META-INF/native-image/beangle/data/reachability-metadata.json`）：
   * 注册无参构造器（实例化）、writeReplace（序列化钩子）与全部公开方法
   * （运行期 `BeanInfo.from` 需 `getMethods` 查询，见 `BeanInfos.get` 的父类回退）；
   * 不开放字段（proxy 无自有 bean 字段）。
   * 格式为顶层对象（`{"reflection": [...]}`）；native-image 会把本片段与终端项目其他
   * `META-INF/native-image` 下各子目录的 `reachability-metadata.json` 片段合并读取。
   */
  private def writeReachabilityMetadata(entityNames: Seq[String], outDir: File): Unit = {
    val out = new File(outDir, ReachabilityMetadataFile)
    out.getParentFile.mkdirs()
    val entries = entityNames.map { n =>
      s"""    { "type": "${n}$$HibernateProxy", "allPublicMethods": true, "methods": [{ "name": "<init>", "parameterTypes": [] }, { "name": "writeReplace", "parameterTypes": [] }] }"""
    }
    val json = "{\n  \"reflection\": [\n" + entries.mkString(",\n") + "\n  ]\n}\n"
    Files.write(out.toPath, json.getBytes(StandardCharsets.UTF_8))
  }

  private def readLines(file: File): Seq[String] = {
    if (!file.isFile) {
      System.err.println(s"Registrars file does not exist: $file")
      System.exit(1)
    }
    val lines = Files.readAllLines(file.toPath, StandardCharsets.UTF_8)
    val result = new mutable.ListBuffer[String]
    val it = lines.iterator()
    while (it.hasNext) {
      val line = it.next().trim
      if (line.nonEmpty && !line.startsWith("#")) result += line
    }
    result.toSeq
  }

  private def parseArgs(args: Array[String]): (File, File) = {
    var registrars = Option.empty[File]
    var outDir = Option.empty[File]
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "-r" | "--registrars" =>
          i += 1
          if (i < args.length) registrars = Some(new File(args(i)))
          else { System.err.println("Missing value for --registrars"); printUsage(); System.exit(1) }
        case "-o" | "--output" =>
          i += 1
          if (i < args.length) outDir = Some(new File(args(i)))
          else { System.err.println("Missing value for --output"); printUsage(); System.exit(1) }
        case "-h" | "--help" =>
          printUsage()
          System.exit(0)
        case arg if arg.startsWith("-") =>
          System.err.println(s"Unknown option: $arg")
          printUsage()
          System.exit(1)
        case _ =>
      }
      i += 1
    }
    val missing = Seq("--registrars" -> registrars, "--output" -> outDir)
      .filter(_._2.isEmpty).map(_._1)
    if (missing.nonEmpty) {
      System.err.println(s"Missing required option(s): ${missing.mkString(", ")}")
      printUsage()
      System.exit(1)
    }
    (registrars.get, outDir.get)
  }

  private def printUsage(): Unit = {
    println("""Usage: BeangleProxyGenerator [options]
              |
              |Generates Hibernate lazy-loading proxy classes for the entities bound by
              |the MappingModule subclasses listed in a registrars file.
              |
              |Options:
              |  -r, --registrars <file>   List of MappingModule class names, one per line
              |                            (# comments allowed). All listed classes must be
              |                            found, otherwise exit with a non-zero code.
              |  -o, --output <dir>        Output directory for the generated proxy .class
              |                            files and the GraalVM reachability-metadata.json
              |                            fragment (META-INF/native-image/beangle/data/;
              |                            registers <Entity>$HibernateProxy reflection:
              |                            <init>, writeReplace and allPublicMethods)
              |  -h, --help                Show this help
              |""".stripMargin)
  }
}
