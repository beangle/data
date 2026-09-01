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

import org.beangle.commons.aot.{AotHintRegistrar, AotPolicy}
import org.beangle.data.hibernate.*
import org.beangle.data.hibernate.cfg.{BindMetadataBuilderFactory, BindSourceProcessor, MappingService}
import org.beangle.data.hibernate.format.{BeangleJsonFormatMapper, BeangleXmlFormatMapper}
import org.beangle.data.hibernate.id.{AutoIncrementGenerator, CodeStyleGenerator, DateStyleGenerator, DateTimeStyleGenerator}
import org.beangle.data.hibernate.jdbc.{JsonAccessor, NativeJsonJdbcType, NullableIntJdbcType, StringJsonJdbcType}
import org.beangle.data.hibernate.proxy.PrebuiltProxyProvider
import org.beangle.data.hibernate.tx.HibernateTransactionManager
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
 *  - Hibernate 查询缓存序列化值类与 BasicType 枚举注册
 *  - 资源：`META-INF/beangle/ddl/.*`、`.*.zh_CN` message bundle、`META-INF/services/.*`
 *
 * 应用侧实体/方言/驱动等配置由应用自身定义 `AotHintRegistrar`/`MetaRegistrar`
 * 子类并启用 `AotPlugin` 生成，与本类互不干扰。
 */
class BeangleAotHints extends AotHintRegistrar {

  /** 容器 bean 类：BindingRegistry 按类型自动注入未显式声明的属性（如
   *  HibernateEntityDao.domain、LocalSessionFactoryBean.container、
   *  HibernateTransactionManager.dataSource）依赖 BeanInfos -> MetaLoader
   *  运行期反射 dig，属性识别需要 getDeclaredFields（Scala var 的私有字段）
   *  与 getDeclaredMethods（getter/setter），默认策略只有 public 成员。
   */
  private val beanPolicy = AotPolicy(Set(
    AotPolicy.Category.PublicConstructors,
    AotPolicy.Category.DeclaredMethods,
    AotPolicy.Category.DeclaredFields))

  override def registering(): Unit = {
    registerBeanTypes()
    registerQueryCacheTypes()
  }

  private def registerBeanTypes(): Unit = {
    hints.registerType(classOf[LocalSessionFactoryBean], beanPolicy)
    hints.registerType(classOf[HibernateEntityDao], beanPolicy)
    hints.registerType(classOf[HibernateTransactionManager], beanPolicy)
    hints.registerType(classOf[SessionCleaner], beanPolicy)
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
      classOf[ScalaPersistentBag], classOf[ScalaPersistentSeq], classOf[ScalaPersistentSet], classOf[ScalaPersistentMap])
    // TransactionProxyFactoryBean 对 DAO/服务目标按 JdkDynamicAopProxy 创建
    // JDK 动态代理：目标接口 + TransactionalProxy + Advised + DecoratingProxy，
    // 接口顺序与运行期一致（ProxyFactory 依此构造并缓存代理类）
    hints.registerProxy(
      classOf[org.beangle.data.dao.EntityDao],
      classOf[org.springframework.transaction.interceptor.TransactionalProxy],
      classOf[org.springframework.aop.framework.Advised],
      classOf[org.springframework.core.DecoratingProxy])
  }

  /** 注册 Hibernate 查询缓存序列化涉及的值类。
   *
   * beangle 默认开启查询缓存（USE_QUERY_CACHE=true，HibernateEntityDao.getAll
   * 固定 setCacheable(true)），查询结果经 jcache 的 JavaSerializationCopier 序列化
   * 往返。与实体区不同，查询缓存存的是反组装后的 JDBC 行值（Object[]）与列元数据
   * CachedJdbcValuesMetadata（含 BasicType[]）：键 QueryKey 及其参数绑定 memento、
   * 值 CacheItem、元数据类显式按名注册；BasicType 实现（NamedBasicTypeImpl）的
   * 字段图覆盖 JavaType/JdbcType/MutabilityPlan/ValueBinder 等大量具体类，组合无法
   * 静态推导，构建期枚举 hibernate-core jar 中 org.hibernate.type 包全部具体类注册。
   * 实体类与 Hibernate 代理不进该链路（缓存的是反组装状态），无需注册。
   */
  private def registerQueryCacheTypes(): Unit = {
    val loader = getClass.getClassLoader
    List(
      "org.hibernate.cache.spi.QueryKey",
      "org.hibernate.query.internal.QueryParameterBindingsImpl$ParameterBindingsMementoImpl",
      "org.hibernate.cache.internal.QueryResultsCacheImpl$CacheItem",
      "org.hibernate.sql.results.jdbc.internal.CachedJdbcValuesMetadata"
    ) foreach { n =>
      try hints.registerSerializable(Class.forName(n, false, loader))
      catch { case _: Throwable => () }
    }
    val codeSource = Class.forName("org.hibernate.type.BasicType", false, loader).getProtectionDomain.getCodeSource
    if (codeSource == null) return
    try {
      val jar = new java.util.jar.JarFile(new java.io.File(codeSource.getLocation.toURI))
      try {
        val entries = jar.entries()
        while entries.hasMoreElements do
          val name = entries.nextElement().getName
          if name.startsWith("org/hibernate/type/") && name.endsWith(".class") then
            val className = name.substring(0, name.length - 6).replace('/', '.')
            try {
              val clazz = Class.forName(className, false, loader)
              if !clazz.isInterface && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers) then
                hints.registerSerializable(clazz)
            } catch {
              case _: Throwable => ()
            }
      } finally jar.close()
    } catch {
      case _: Throwable => ()
    }
  }
}
