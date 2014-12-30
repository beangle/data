package org.beangle.data.serialize

import org.beangle.data.serialize.model.{ Address, Person }
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.collection.page.SinglePage

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
    it("serializer page ignore collection") {
      val csv = CsvSerializer()
      val params = Map("properties" -> List(
        classOf[Person] -> List("code", "address", "sidekick", "skills"),
        classOf[Address] -> List("street", "name")))
      //println(csv.serialize(new SinglePage(1, 2, 100, List(new Person("002", "admin2"), new Person("001", "admin"))), params))
    }
  }
}
