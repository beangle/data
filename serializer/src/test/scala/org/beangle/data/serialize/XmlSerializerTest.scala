package org.beangle.data.serialize

import java.io.File
import org.beangle.data.serialize.io.xml.DomDriver
import org.beangle.data.serialize.model.{ Address, Person }
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.collection.page.SinglePage

@RunWith(classOf[JUnitRunner])
class XmlSerializerTest extends FunSpec with Matchers {

  describe("XmlSerializer") {
    it("serializer xml ") {
      val serializer = XmlSerializer()
      serializer.alias("person", classOf[Person])
      serializer.alias("address", classOf[Address])
      serializer.alias("list", classOf[::[_]])
      //println(serializer.serialize(List(Some(new Person("002", "admin2")), new Person("001", "admin"))))
      //println(serializer.serialize("3"))
      //println(serializer.serialize(new SinglePage(1, 2, 200, List(new Person("002", "admin2"), new Person("001", "admin")))))
    }
  }
}
