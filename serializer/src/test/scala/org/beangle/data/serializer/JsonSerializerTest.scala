package org.beangle.data.serializer

import java.io.File
import org.beangle.data.serializer.io.xml.DomDriver
import org.beangle.data.serializer.model.{ Address, Person }
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.data.serializer.io.json.JsonDriver

@RunWith(classOf[JUnitRunner])
class JsonSerializerTest extends FunSpec with Matchers {

  describe("JsonSerializer") {
    it("serializer json ") {
      val serializer = JsonSerializer(new JsonDriver)
      serializer.alias("person", classOf[Person])
      serializer.alias("address", classOf[Address])
      serializer.alias("list", classOf[::[_]])
      println(serializer.serialize(List(Some(new Person("002", "admin2")), new Person("001", "admin"))))
    }
  }
}
