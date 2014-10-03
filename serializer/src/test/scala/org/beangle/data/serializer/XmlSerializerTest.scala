package org.beangle.data.serializer

import java.io.File
import org.beangle.data.serializer.io.xml.DomDriver
import org.beangle.data.serializer.model.{ Address, Person }
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class XmlSerializerTest extends FunSpec with Matchers {

  describe("XmlSerializer") {
    it("serializer xml ") {
      val serializer = XmlSerializer()
      serializer.alias("person", classOf[Person])
      serializer.alias("address", classOf[Address])
      serializer.alias("list", classOf[::[_]])
      println(serializer.serialize(List(Some(new Person("002", "admin2")), new Person("001", "admin"))))

      println(serializer.serialize("3"))
    }
  }
}
