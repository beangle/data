package org.beangle.data.jpa.dao

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers
import org.scalatest.FunSpec
import org.beangle.data.jpa.mapping.TestBean

@RunWith(classOf[JUnitRunner])
class OqlBuilderTest extends FunSpec with Matchers {

  describe("OqlBuilder") {
    it("GenStatement") {
      val builder = OqlBuilder.from(classOf[TestBean], "test")
      builder.groupBy("test.name").having("count(*)>1").select("test.name")

      val query1 = builder.build()
      assert(query1.statement == "select test.name from " + classOf[TestBean].getName + " test group by test.name having count(*)>1")

      val query2 = builder.orderBy("test.name").build()
      assert(query2.statement == "select test.name from " + classOf[TestBean].getName + " test group by test.name having count(*)>1 order by test.name")
    }
  }
}