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

import java.io.{File, PrintWriter}

/** 生成 beangle-data 各 jar 内嵌的 META-INF/native-image 元数据。
 *
 * 库自身固定的反射点/资源与应用环境无关，应随 jar 内嵌（GraalVM 构建时自动发现并合并）：
 *  - beangle-data-model.jar      : META-INF/native-image/org/beangle/data/beangle-data-model/
 *  - beangle-data-hibernate.jar : META-INF/native-image/org/beangle/data/beangle-data-hibernate/
 *
 * 应用侧实体/方言/驱动等配置仍由 [[NativeImageConfigGen]] 在可执行项目构建期生成。
 *
 * 用法（在项目根目录）：
 * {{{
 *   sbt 'hibernate/Compile/runMain org.beangle.data.hibernate.nativeimage.LibraryNativeImageConfig'
 * }}}
 * 产物写入 model/src/main/resources 与 hibernate/src/main/resources 的 META-INF/native-image 目录。
 */
object LibraryNativeImageConfig {

  /** model 模块自身的固定反射点：绑定期反射实例化 + 库自带注解 */
  private def modelReflectionClasses: Seq[(String, Boolean)] = Seq(
    "org.beangle.data.orm.MappingModule" -> false,
    "org.beangle.data.model.annotation.archive" -> true,
    "org.beangle.data.model.annotation.code" -> true,
    "org.beangle.data.model.annotation.config" -> true,
    "org.beangle.data.model.annotation.flash" -> true,
    "org.beangle.data.model.annotation.flow" -> true,
    "org.beangle.data.model.annotation.log" -> true,
    "org.beangle.data.model.annotation.shard" -> true,
    "org.beangle.data.model.annotation.temp" -> true
  )

  /** hibernate 模块自身的固定反射点：Hibernate/运行时按名反射实例化或检查的类 */
  private def hibernateReflectionClasses: Seq[String] = Seq(
    "org.beangle.data.hibernate.ScalaPropertyAccessStrategy",
    "org.beangle.data.hibernate.ScalaPropertyAccessor$BasicGetter",
    "org.beangle.data.hibernate.ScalaPropertyAccessor$BasicSetter",
    "org.beangle.data.hibernate.SpringSessionContext",
    "org.beangle.data.hibernate.cfg.BindMetadataBuilderFactory",
    "org.beangle.data.hibernate.cfg.BindSourceProcessor",
    "org.beangle.data.hibernate.cfg.MappingService",
    "org.beangle.data.hibernate.LocalSessionFactoryBean",
    "org.beangle.data.hibernate.ConfigurationBuilder",
    "org.beangle.data.hibernate.HibernateEntityDao",
    "org.beangle.data.hibernate.SessionHelper",
    "org.beangle.data.hibernate.format.BeangleJsonFormatMapper",
    "org.beangle.data.hibernate.format.BeangleXmlFormatMapper",
    "org.beangle.data.hibernate.jdbc.JsonAccessor",
    "org.beangle.data.hibernate.jdbc.NativeJsonJdbcType",
    "org.beangle.data.hibernate.jdbc.StringJsonJdbcType",
    "org.beangle.data.hibernate.jdbc.NullableIntJdbcType",
    "org.beangle.data.hibernate.id.AutoIncrementGenerator",
    "org.beangle.data.hibernate.id.CodeStyleGenerator",
    "org.beangle.data.hibernate.id.DateStyleGenerator",
    "org.beangle.data.hibernate.id.DateTimeStyleGenerator",
    "org.beangle.data.hibernate.udt.ValueType",
    "org.beangle.data.hibernate.udt.EnumType",
    "org.beangle.data.hibernate.udt.JsonType",
    "org.beangle.data.hibernate.udt.YearMonthType",
    "org.beangle.data.hibernate.udt.Decimal5Type",
    "org.beangle.data.hibernate.udt.TinyDecimal5Type",
    "org.beangle.data.hibernate.udt.BagType",
    "org.beangle.data.hibernate.udt.SeqType",
    "org.beangle.data.hibernate.udt.SetType",
    "org.beangle.data.hibernate.udt.MapType",
    "org.beangle.data.hibernate.udt.ScalaPersistentBag",
    "org.beangle.data.hibernate.udt.ScalaPersistentSeq",
    "org.beangle.data.hibernate.udt.ScalaPersistentSet",
    "org.beangle.data.hibernate.udt.ScalaPersistentMap",
    "org.beangle.data.hibernate.udt.ScalaCollectionType"
  );

  def main(args: Array[String]): Unit = {
    val root = new File(if (args.length > 0) args(0) else ".")
    generate(root)
    println(s"Library native-image configs generated under ${root.getAbsolutePath}")
  }

  def generate(root: File): Unit = {
    // model jar
    val modelDir = new File(root, "model/src/main/resources/META-INF/native-image/org/beangle/data/beangle-data-model")
    write(modelDir, "reflect-config.json", nameReflectConfig(modelReflectionClasses))
    write(modelDir, "resource-config.json", NativeImageConfigGen.resourcesConfig(Seq("META-INF/beangle/ddl/.*", ".*\\.zh_CN")))
    // hibernate jar
    val hibDir = new File(root, "hibernate/src/main/resources/META-INF/native-image/org/beangle/data/beangle-data-hibernate")
    write(hibDir, "reflect-config.json", nameReflectConfig(hibernateReflectionClasses.map(_ -> false)))
    write(hibDir, "resource-config.json", NativeImageConfigGen.resourcesConfig(Seq("META-INF/services/.*")))
  }

  /** 按类名生成 reflect-config（annotation 仅注册方法，其余注册构造器/方法/字段） */
  private def nameReflectConfig(classes: Seq[(String, Boolean)]): String = {
    val sb = new StringBuilder("[\n")
    classes.distinct.sortBy(_._1) foreach { case (name, isAnnotation) =>
      sb.append("  {\"name\":\"").append(name).append("\"");
      if (isAnnotation) sb.append(",\"allDeclaredMethods\":true");
      else sb.append(",\"allDeclaredConstructors\":true,\"allDeclaredMethods\":true,\"allDeclaredFields\":true,\"queryAllPublicMethods\":true");
      sb.append("},\n")
    }
    if (sb.toString.endsWith(",\n")) sb.setLength(sb.length - 2)
    sb.append("\n]\n")
    sb.toString
  }

  private def write(dir: File, name: String, content: String): Unit = {
    dir.mkdirs()
    val pw = new PrintWriter(new File(dir, name), "UTF-8")
    try pw.print(content) finally pw.close()
  }
}
