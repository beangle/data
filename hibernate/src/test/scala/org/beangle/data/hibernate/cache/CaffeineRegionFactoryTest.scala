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

package org.beangle.data.hibernate.cache

import com.github.benmanes.caffeine.jcache.configuration.{CaffeineConfiguration, TypesafeConfigurator}
import com.typesafe.config.ConfigFactory
import org.beangle.data.dao.OqlBuilder
import org.beangle.data.hibernate.model.{Skill, SkillType}
import org.beangle.data.hibernate.{HibernateEntityDao, LocalSessionFactoryBean, Tests}
import org.hibernate.SessionFactory
import org.hibernate.cache.jcache.ConfigSettings
import org.hibernate.cache.spi.RegionFactory
import org.hibernate.dialect.H2Dialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.net.URI
import java.util.concurrent.TimeUnit
import javax.cache.Caching
import javax.cache.expiry.Duration

/** 验证缺失区域(映射里用到、caffeine.jcache 里没写的区域)是否继承 caffeine.jcache.default。
  *
  * 见 docs/jcache.md。
  */
class CaffeineRegionFactoryTest extends AnyFunSpec, Matchers {

  it("Create missing regions from caffeine.jcache.default") {
    withQueryRegion(None) { (factory, config) =>
      factory.shouldBe(a[CaffeineRegionFactory])
      config.getMaximumSize.getAsLong.shouldBe(33L)
      config.getExpireAfterWrite.getAsLong.shouldBe(TimeUnit.MINUTES.toNanos(5))
      config.getExpiryPolicyFactory.create().getExpiryForCreation.shouldBe(new Duration(TimeUnit.SECONDS, 3))
    }
  }

  it("Use the region factory by its full class name") {
    withQueryRegion(Some(classOf[CaffeineRegionFactory].getName)) { (factory, config) =>
      factory.getClass.shouldBe(classOf[CaffeineRegionFactory])
      config.getMaximumSize.getAsLong.shouldBe(33L)
    }
  }

  it("Hibernate jcache region factory loses lazy-expiration of default") {
    withQueryRegion(Some("jcache")) { (factory, config) =>
      //容量和eager过期仍然来自default
      config.getMaximumSize.getAsLong.shouldBe(33L)
      config.getExpireAfterWrite.getAsLong.shouldBe(TimeUnit.MINUTES.toNanos(5))
      //lazy-expiration被MutableConfiguration的EternalExpiryPolicy覆盖，于是default中的过期设置失效
      config.getExpiryPolicyFactory.create().getExpiryForCreation.shouldBe(Duration.ETERNAL)
    }
  }

  /** 用给定的区域工厂跑一次可缓存查询，取出查询结果区域的配置交给 check。
    *
    * 用自定义的 caffeine.jcache.default 覆盖 TypesafeConfigurator 的配置来源，免得依赖
    * 测试 classpath 上的 application.conf；另外自建一个专用 URI 的 CacheManager 交给
    * Hibernate（caffeine 按 URI+classloader 缓存 CacheManager），避免与其它用例共用
    * 缓存；用完随会话工厂一起关闭。
    */
  private def withQueryRegion(regionFactory: Option[String])(
      check: (RegionFactory, CaffeineConfiguration[Object, Object]) => Unit): Unit = {
    val source = TypesafeConfigurator.configSource()
    val config = ConfigFactory
      .parseString(
        """caffeine.jcache.default.policy {
          |  maximum.size = 33
          |  lazy-expiration { creation = 3s, update = 3s, access = 3s }
          |  eager-expiration { after-write = 5m }
          |}""".stripMargin)
      .withFallback(ConfigFactory.defaultReference())
      .resolve()
    TypesafeConfigurator.setConfigSource((uri: URI, loader: ClassLoader) => config)
    var sf: SessionFactory = null
    try {
      val provider = Caching.getCachingProvider("com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider")
      val cacheManager = provider.getCacheManager(URI.create("beangle-jcache-test-" + regionFactory.getOrElse("default")),
        getClass.getClassLoader)
      val builder = new LocalSessionFactoryBean(Tests.buildTestH2())
      builder.ormLocation = "classpath*:beangle.xml"
      builder.properties.put("hibernate.dialect", classOf[H2Dialect].getName)
      builder.properties.put("hibernate.hbm2ddl.auto", "create")
      builder.properties.put(ConfigSettings.CACHE_MANAGER, cacheManager)
      regionFactory.foreach(name => builder.properties.put("hibernate.cache.region.factory_class", name))
      builder.init()
      sf = builder.getObject
      val entityDao = new HibernateEntityDao(sf)
      entityDao.init()
      val session = sf.getCurrentSession
      val transaction = session.beginTransaction()
      val skillType = new SkillType
      skillType.name = "play basketball"
      val skill = new Skill
      skill.skillType = skillType
      skill.name = "national basketball champion"
      entityDao.saveOrUpdate(skillType, skill)

      val q = OqlBuilder.from(classOf[Skill], "s")
      q.where("s.name like :name", "%basket%")
      q.cacheable(true)
      assert(entityDao.search(q).size == 1)
      session.flush()
      transaction.commit()

      val factory = sf.asInstanceOf[SessionFactoryImplementor].getSessionFactoryOptions.getServiceRegistry
        .requireService(classOf[RegionFactory])
      val cache = cacheManager.getCache[Object, Object](RegionFactory.DEFAULT_QUERY_RESULTS_REGION_UNQUALIFIED_NAME)
      check(factory, cache.getConfiguration(classOf[CaffeineConfiguration[Object, Object]]))
    } finally {
      if (null != sf) sf.close()
      TypesafeConfigurator.setConfigSource(source)
    }
  }
}
