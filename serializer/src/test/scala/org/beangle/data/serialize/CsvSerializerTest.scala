package org.beangle.data.serialize

import org.beangle.data.serialize.model.{ Address, Person }
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CsvSerializerTest extends FunSpec with Matchers {

  describe("CsvSerializer") {
    it("serializer csv ") {
      val csv = CsvSerializer()
      val params = Map("properties" -> List(
        classOf[Person] -> List("code", "address", "sidekick", "skills"),
        classOf[Address] -> List("street", "name")))
      //println(csv.serialize(List(new Person("002", "admin2"), new Person("001", "admin")), params))
    }
  }
}
