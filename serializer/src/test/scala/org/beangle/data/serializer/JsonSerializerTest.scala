package org.beangle.data.serializer

import java.io.File
import org.beangle.data.serializer.io.xml.DomDriver
import org.beangle.data.serializer.model.{ Address, Person }
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.data.serializer.io.json.JsonDriver
import org.beangle.data.serializer.model.Member

@RunWith(classOf[JUnitRunner])
class JsonSerializerTest extends FunSpec with Matchers {

  describe("JsonSerializer") {
    it("serializer json ") {
      val json = JsonSerializer()
      json.alias("person", classOf[Person])
      json.alias("address", classOf[Address])
      json.alias("list", classOf[::[_]])
      //      println(json.serialize(List(Some(new Person("002", "admin2")), new Person("001", "admin"))))
      //      println(json.serialize(Array("a","b")))
      //      println(json.serialize(new Member))
    }
  }
}
