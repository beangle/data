package org.beangle.data.jdbc

import org.beangle.commons.lang.annotation.value
import org.beangle.data.jdbc.meta.Engines
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import java.sql.Types

@RunWith(classOf[JUnitRunner])
class SqlTypeMappingTest extends FunSpec with Matchers {
  describe("SqlTypeMapping") {
    it("test value type") {
     val mapping = new DefaultSqlTypeMapping(Engines.forName("h2"))
     assert(mapping.sqlCode(classOf[Terms]) == Types.SMALLINT)
    }
  }
}

@value
class Terms(value: Short)