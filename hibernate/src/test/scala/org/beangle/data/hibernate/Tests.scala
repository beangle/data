package org.beangle.data.hibernate

import java.{ util => ju }
import org.hibernate.cfg.AvailableSettings
import org.beangle.data.hibernate.cfg.OverrideConfiguration
import org.hibernate.dialect.H2Dialect
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.io.IOs
import org.beangle.data.jdbc.ds.DataSourceUtils
import javax.sql.DataSource
import org.beangle.data.hibernate.cfg.RailsNamingStrategy
import org.beangle.data.hibernate.naming.RailsNamingPolicy

object Tests {

  def buildProperties(): ju.Properties = {
    val properties = new ju.Properties
    properties.put(AvailableSettings.DIALECT, classOf[H2Dialect].getName)
    properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory")
    properties.put("hibernate.hbm2ddl.auto", "create")
    properties.put("hibernate.show_sql", "false")
    properties.put(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, classOf[SimpleCurrentSessionContext].getName())
    properties
  }

  def buildConfig(): OverrideConfiguration = {
    val configuration = new OverrideConfiguration
    configuration.setNamingStrategy(new RailsNamingStrategy(new RailsNamingPolicy))
    configuration
  }

  def buildDs(): DataSource = {
    val properties = IOs.readJavaProperties(ClassLoaders.getResource("db.properties", getClass))
    DataSourceUtils.build("h2", properties("h2.username"), properties("h2.password"), Map("url" -> properties("h2.url")))
  }
}
