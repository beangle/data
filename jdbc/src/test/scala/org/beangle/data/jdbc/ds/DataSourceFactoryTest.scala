package org.beangle.data.jdbc.ds

import org.scalatest.Matchers
import org.scalatest.FlatSpec
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.lang.ClassLoaders

/**
 * @author chaostone
 */
@RunWith(classOf[JUnitRunner])
class DataSourceFactoryTest extends FlatSpec with Matchers {

  "DataSourceFactory " should "build a datasource" in {
    val factory = new DataSourceFactory()
    factory.url = ClassLoaders.getResource("datasources.xml").toString()
    factory.name = "h2"
    factory.init()
    factory.result.getConnection
    factory.destroy()
  }
}