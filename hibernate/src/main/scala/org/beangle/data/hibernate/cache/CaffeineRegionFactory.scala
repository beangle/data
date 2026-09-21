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

import com.github.benmanes.caffeine.jcache.configuration.TypesafeConfigurator
import org.beangle.data.Logger
import org.hibernate.boot.spi.SessionFactoryOptions
import org.hibernate.cache.CacheException
import org.hibernate.cache.jcache.{ConfigSettings, MissingCacheStrategy}
import org.hibernate.cache.jcache.internal.JCacheRegionFactory

import java.util as ju
import javax.cache.Cache
import javax.cache.configuration.{Configuration, MutableConfiguration}

/** Hibernate 二级缓存的区域工厂：映射里声明过、但 `caffeine.jcache` 未显式配置的区域，
  * 也按 `caffeine.jcache.default` 建立。
  *
  * Hibernate 发现区域未定义时，会用 `MutableConfiguration` 现建缓存；caffeine 虽然会
  * 叠加 `caffeine.jcache.default` 模板，但最后又把过期策略换成 MutableConfiguration
  * 的 EternalExpiryPolicy（见 com.github.benmanes.caffeine.jcache.CacheFactory 的
  * resolveConfigurationFor），于是 default 里的 lazy-expiration 形同虚设
  * （maximum.size、eager-expiration 不受影响）。
  *
  * 这里覆写"现建缓存"的分支，直接用 TypesafeConfigurator.defaults 的结果建缓存，
  * 让 default 的配置完整生效。缓存实现不是 caffeine、或读不到配置时，退化为
  * Hibernate 自带行为（等价于 jcache）。
  *
  * 见 docs/jcache.md。
  */
class CaffeineRegionFactory extends JCacheRegionFactory {
  //start 之后各区域可能被并发创建，故与上游一致使用 volatile
  @volatile private var missingCacheStrategy: MissingCacheStrategy = MissingCacheStrategy.CREATE_WARN
  @volatile private var defaultConfiguration: Option[Configuration[Object, Object]] = None

  override protected def prepareForUse(settings: SessionFactoryOptions, configValues: ju.Map[String, Object]): Unit = {
    super.prepareForUse(settings, configValues)
    missingCacheStrategy =
      if (configValues == null) MissingCacheStrategy.CREATE_WARN
      else MissingCacheStrategy.interpretSetting(configValues.get(ConfigSettings.MISSING_CACHE_STRATEGY))
    defaultConfiguration = resolveDefaultConfiguration()
  }

  override protected def createCache(regionName: String): Cache[Object, Object] = {
    missingCacheStrategy match {
      case MissingCacheStrategy.FAIL =>
        throw new CacheException("On-the-fly creation of JCache Cache objects is not supported [" + regionName + "]")
      case MissingCacheStrategy.CREATE_WARN =>
        Logger.warn("Unable to find a JCache cache [" + regionName + "] but " +
          ConfigSettings.MISSING_CACHE_STRATEGY + "=" + MissingCacheStrategy.CREATE.getExternalRepresentation +
          ", creating it from caffeine.jcache.default")
      case _ => ()
    }
    getCacheManager.createCache(regionName, defaultConfiguration.getOrElse(new MutableConfiguration[Object, Object]()))
  }

  /** 读取 caffeine 使用的那份外部配置（与 caffeine 自身读取方式一致），取其中的
    * caffeine.jcache.default 作为缺失区域的默认配置。
    *
    * caffeine 的 reference.conf 自带 default 定义，所以只要 provider 是 caffeine，
    * 这里总能拿到一份配置：应用没写 default 时它等价于 MutableConfiguration
    * （永不过期、无容量上限）。
    */
  private def resolveDefaultConfiguration(): Option[Configuration[Object, Object]] = {
    val cacheManager = getCacheManager
    if (!cacheManager.getClass.getName.startsWith("com.github.benmanes.caffeine.jcache.")) None
    else {
      try {
        val config = TypesafeConfigurator.configSource().get(cacheManager.getURI, cacheManager.getClassLoader)
        if (config.hasPath("caffeine.jcache.default")) Some(TypesafeConfigurator.defaults[Object, Object](config))
        else None
      } catch {
        case e: Throwable =>
          Logger.warn("Cannot read caffeine.jcache.default, use the default JCache configuration instead", e)
          None
      }
    }
  }
}
