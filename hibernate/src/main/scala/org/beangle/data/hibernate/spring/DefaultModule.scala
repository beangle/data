/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.hibernate.spring

import org.beangle.commons.inject.bind.{ AbstractBindModule, profile }
import org.beangle.data.hibernate.{ HibernateEntityDao, HibernateMetadataFactory }
import org.beangle.data.hibernate.cfg.OverrideConfiguration
import org.beangle.data.hibernate.spring.web.OpenSessionInViewInterceptor
import org.springframework.beans.factory.config.PropertiesFactoryBean
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.lob.DefaultLobHandler
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean

object DefaultModule extends AbstractBindModule {

  protected override def binding(): Unit = {
    bind("DataSource.default", classOf[DriverManagerDataSource]).property("driverClassName", "org.h2.Driver")
      .property("url", "jdbc:h2:./target/beangle;AUTO_SERVER=TRUE").property("username", "sa")
      .property("password", "").primary

    bind("HibernateConfig.default", classOf[PropertiesFactoryBean]).property(
      "properties",
      props(
        // "hibernate.temp.use_jdbc_metadata_defaults=false",
        // "hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // "hibernate.hbm2ddl.auto=validate",
        "hibernate.max_fetch_depth=1", "hibernate.default_batch_fetch_size=500",
        "hibernate.jdbc.fetch_size=8", "hibernate.jdbc.batch_size=20",
        "hibernate.jdbc.batch_versioned_data=true", "hibernate.jdbc.use_streams_for_binary=true",
        "hibernate.jdbc.use_get_generated_keys=true",
        //net.sf.ehcache.configurationResourceName
        "hibernate.cache.region.factory_class=org.hibernate.cache.EhCacheRegionFactory",
        "hibernate.cache.use_second_level_cache=true", "hibernate.cache.use_query_cache=true",
        "hibernate.query.substitutions=true 1, false 0, yes 'Y', no 'N'", "hibernate.show_sql=false"))
      .description("Hibernate配置信息")

    bind("SessionFactory.default", classOf[LocalSessionFactoryBean])
      .property("configurationClass", classOf[OverrideConfiguration].getName)
      .property("hibernateProperties", ref("HibernateConfig.default"))
      .property("configLocations", "classpath*:META-INF/hibernate.cfg.xml")
      .property("ormLocations", "classpath*:META-INF/beangle/orm.xml").primary

    bind("HibernateTransactionManager.default", classOf[HibernateTransactionManager]).primary

    bind("TransactionProxy.template", classOf[TransactionProxyFactoryBean]).setAbstract().property(
      "transactionAttributes",
      props("save*=PROPAGATION_REQUIRED", "update*=PROPAGATION_REQUIRED", "delete*=PROPAGATION_REQUIRED",
        "batch*=PROPAGATION_REQUIRED", "execute*=PROPAGATION_REQUIRED", "remove*=PROPAGATION_REQUIRED",
        "create*=PROPAGATION_REQUIRED", "init*=PROPAGATION_REQUIRED", "authorize*=PROPAGATION_REQUIRED",
        "*=PROPAGATION_REQUIRED,readOnly")).primary

    bind("EntityMetadata.hibernate", classOf[HibernateMetadataFactory])

    bind("EntityDao.hibernate", classOf[TransactionProxyFactoryBean]).proxy("target", classOf[HibernateEntityDao])
      .parent("TransactionProxy.template").primary().description("基于Hibernate提供的通用DAO")

    bind("LobHandler.default", classOf[DefaultLobHandler]).description("Spring提供的大字段处理句柄")

    bind("web.Interceptor.hibernate", classOf[OpenSessionInViewInterceptor])
  }

}