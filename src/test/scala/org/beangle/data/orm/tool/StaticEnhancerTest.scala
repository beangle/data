package org.beangle.data.orm.tool

import org.beangle.commons.io.ResourcePatternResolver
import org.beangle.commons.lang.SystemInfo
import org.beangle.data.orm.Mappings
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.Database

import java.io.File
import java.util.Locale

object StaticEnhancerTest {
  def main(args: Array[String]): Unit = {
    val engine = Engines.forName("PostgreSQL")
    val ormLocations = ResourcePatternResolver.getResources("classpath*:META-INF/beangle/orm.xml")
    val database = new Database(engine)
    val mappings = new Mappings(database, ormLocations)
    mappings.locale = Locale.SIMPLIFIED_CHINESE
    mappings.autobind()

    val se = new StaticEnhancer
    se.enhance(new File(SystemInfo.tmpDir), mappings)
  }
}
