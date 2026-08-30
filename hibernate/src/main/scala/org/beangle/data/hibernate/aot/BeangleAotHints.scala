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

package org.beangle.data.hibernate.aot

import org.beangle.commons.aot.AotHintRegistrar
import org.beangle.data.hibernate.*
import org.beangle.data.hibernate.cfg.{BindMetadataBuilderFactory, BindSourceProcessor, MappingService}
import org.beangle.data.hibernate.format.{BeangleJsonFormatMapper, BeangleXmlFormatMapper}
import org.beangle.data.hibernate.id.{AutoIncrementGenerator, CodeStyleGenerator, DateStyleGenerator, DateTimeStyleGenerator}
import org.beangle.data.hibernate.jdbc.{JsonAccessor, NativeJsonJdbcType, NullableIntJdbcType, StringJsonJdbcType}
import org.beangle.data.hibernate.proxy.PrebuiltProxyProvider
import org.beangle.data.hibernate.udt.*
import org.beangle.data.orm.MappingModule

/** beangle-data 库自身的 GraalVM native-image 反射/资源提示。
 *
 * 构建期由 [[org.beangle.commons.aot.AotHintGenerator]] 扫描并生成
 * `META-INF/native-image` 配置，随 beangle-data-hibernate.jar 内嵌发布
 * （GraalVM 构建时自动发现并合并）。涵盖：
 *  - model 模块：`MappingModule`（实体注解的反射提示由 model 侧
 *    [[org.beangle.data.model.aot.ModelAotHints]] 独立发布，见 beangle-data-model.jar）
 *  - hibernate 模块：被 Hibernate/运行时按名反射实例化或检查的类
 *  - 资源：`META-INF/beangle/ddl/.*`、`.*.zh_CN` message bundle、`META-INF/services/.*`
 *
 * 应用侧实体/方言/驱动等配置由应用自身定义 `AotHintRegistrar`/`MetaRegistrar`
 * 子类并启用 `AotPlugin` 生成，与本类互不干扰。
 */
class BeangleAotHints extends AotHintRegistrar {
  override def registering(): Unit = {

    hints.registerType(
      classOf[PrebuiltProxyProvider],
      classOf[MappingModule], classOf[ScalaPropertyAccessStrategy],
      classOf[ScalaPropertyAccessor.BasicGetter], classOf[ScalaPropertyAccessor.BasicSetter],
      classOf[SpringSessionContext],
      classOf[BindMetadataBuilderFactory], classOf[BindSourceProcessor], classOf[MappingService],
      classOf[LocalSessionFactoryBean], classOf[ConfigurationBuilder],
      classOf[HibernateEntityDao], classOf[SessionHelper.type],
      classOf[BeangleJsonFormatMapper], classOf[BeangleXmlFormatMapper],
      classOf[JsonAccessor.type], classOf[NativeJsonJdbcType], classOf[StringJsonJdbcType], classOf[NullableIntJdbcType.type],
      classOf[AutoIncrementGenerator], classOf[CodeStyleGenerator], classOf[DateStyleGenerator], classOf[DateTimeStyleGenerator],
      classOf[ValueType[_]], classOf[EnumType[_]], classOf[JsonType[_]], classOf[YearMonthType],
      classOf[Decimal5Type], classOf[TinyDecimal5Type], classOf[BagType], classOf[SeqType],
      classOf[SetType], classOf[MapType],
      classOf[ScalaPersistentBag], classOf[ScalaPersistentSeq], classOf[ScalaPersistentSet], classOf[ScalaPersistentMap]
    )
  }
}
