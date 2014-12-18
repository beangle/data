package org.beangle.data.serialize.io.csv

import java.io.FileOutputStream
import java.util.Date
import org.beangle.data.serialize.{ AbstractSerializer, CsvSerializer }
import org.beangle.data.serialize.marshal.MarshallingContext
import org.beangle.data.serialize.model.{ Address, Person, Skill }
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DefaultCsvWriterTest extends FunSpec with Matchers {

  describe("DefaultCsvWriter") {
    it("getAttributes") {
      val serializer = CsvSerializer().asInstanceOf[AbstractSerializer]
      val os = new FileOutputStream("/tmp/a.csv")
      val writer = serializer.driver.createWriter(os).asInstanceOf[DefaultCsvWriter]
      val context = new MarshallingContext(serializer, writer, serializer.registry,
        Map(classOf[Person] -> List("code", "name", "accountMoney1", "bestSkill", "skills", "families", "sidekick", "address"), classOf[Skill] -> List("name")))

      val personProperties = context.getProperties(classOf[Person])
      assert(personProperties.isDefined)
      assert(personProperties.get.size == 8)
      assert(context.getProperties(classOf[java.util.Date]) == None)
      assert(context.getProperties(classOf[Address]).isDefined)
      assert(context.getProperties(classOf[Address]).get.size == 3)

      var csvProperties = writer.getProperties(context)
      assert(csvProperties.length == 0)
      context.beanType = classOf[Person]
      csvProperties = writer.getProperties(context)
      println(csvProperties.toList)
      assert(csvProperties.length == 10)
    }
  }
}
