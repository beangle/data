package org.beangle.data.jdbc.ds

import org.junit.runner.RunWith
import org.scalatest.{ FlatSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import scala.xml.XML
import org.beangle.commons.lang.ClassLoaders

/**
 * @author chaostone
 */
@RunWith(classOf[JUnitRunner])
class DatasourceConfigTest extends FlatSpec with Matchers {
  "DatasourceConfig " should "build a correct orace datasource" in {
    (XML.load(ClassLoaders.getResource("datasources.xml")) \ "datasource") foreach { ds =>
      val config = DatasourceConfig.build(ds)
      if (config.name == "tigre") assert(config.props.contains("driverType"))
    }

  }
}