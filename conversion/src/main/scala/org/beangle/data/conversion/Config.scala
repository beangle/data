package org.beangle.data.conversion

import org.beangle.commons.lang.Numbers
import org.beangle.commons.lang.Strings
import org.beangle.data.conversion.impl.DefaultTableFilter
import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.data.jdbc.util.DbConfig
import org.beangle.data.jdbc.util.PoolingDataSourceFactory
import javax.sql.DataSource

import Config._
import org.beangle.data.conversion.db.DatabaseWrapper

object Config {

  def apply(xml: scala.xml.Elem): Config = {
    new Config(Config.source(xml), Config.target(xml), Config.maxtheads(xml))
  }

  private def maxtheads(xml: scala.xml.Elem): Int = {
    val mt = (xml \ "@maxthreads").text.trim
    val maxthreads = Numbers.toInt(mt, 5)
    if (maxthreads > 0) maxthreads else 5
  }

  private def source(xml: scala.xml.Elem): Source = {
    val dbconf = DbConfig.build((xml \\ "source").head)

    val ds = new PoolingDataSourceFactory(dbconf.driver,
      dbconf.url, dbconf.user, dbconf.password, dbconf.props).getObject
    val source = new Source(dbconf.dialect, ds)
    source.schema = dbconf.schema
    source.catalog = dbconf.catalog
    source.lowercase = "true" == (xml \\ "tables" \ "@lowercase").text
    source.index = "false" != (xml \\ "tables" \ "@index").text
    source.constraint = "false" != (xml \\ "tables" \ "@constraint").text
    source.includes = Strings.split((xml \\ "source" \\ "includes").text.trim)
    source.excludes = Strings.split((xml \\ "source" \\ "excludes").text.trim)
    source
  }

  private def target(xml: scala.xml.Elem): Target = {
    val dbconf = DbConfig.build((xml \\ "target" \\ "db").head)

    val ds = new PoolingDataSourceFactory(dbconf.driver,
      dbconf.url, dbconf.user, dbconf.password, dbconf.props).getObject
    val target = new Target(dbconf.dialect, ds)
    target.schema = dbconf.schema
    target.catalog = dbconf.catalog
    target
  }

  final class Source(val dialect: Dialect, val dataSource: DataSource) {
    var schema: String = _
    var catalog: String = _
    var includes: Seq[String] = _
    var excludes: Seq[String] = _
    var lowercase: Boolean = false
    var index: Boolean = true
    var constraint: Boolean = true

    def buildWrapper(): DatabaseWrapper = {
      if (null == schema) schema = dialect.defaultSchema
      new DatabaseWrapper(dataSource, dialect, catalog, schema)
    }

    def filter(finalTables: collection.Set[String]): Seq[String] = {
      val filter = new DefaultTableFilter()
      if (null != includes) {
        for (include <- includes)
          filter.include(include)
      }
      if (null != excludes) {
        for (exclude <- excludes)
          filter.exclude(exclude)
      }
      return filter.filter(finalTables)
    }
  }

  final class Target(val dialect: Dialect, val dataSource: DataSource) {
    var schema: String = _
    var catalog: String = _

    def buildWrapper(): DatabaseWrapper = {
      if (null == schema) schema = dialect.defaultSchema
      new DatabaseWrapper(dataSource, dialect, catalog, schema)
    }

  }
}

import Config._
class Config(val source: Source, val target: Target, val maxthreads: Int) {
}