package org.beangle.data.serialize.marshal

import java.{ util => ju }
import org.beangle.data.serialize.mapper.DefaultMapper
import org.beangle.data.serialize.model.Skill
import org.junit.runner.RunWith
import org.scalatest.{Finders, FunSpec, Matchers}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DefaultMarshallerRegistryTest extends FunSpec with Matchers {

  describe("DefaultMarshallerRegistry") {
    it("lookup marshaller ") {
      val registry = new DefaultMarshallerRegistry(new DefaultMapper)
      val skills = Array(new Skill("Play Basketball Best"), new Skill("Play football"))
      val converter = registry.lookup(skills.getClass)
      val converter2 = registry.lookup(skills.getClass)
      assert(converter != null)
      assert(converter.support(skills.getClass))
      assert(converter.getClass.getName == "org.beangle.data.serialize.marshal.ArrayMarshaller")

      val dateMarshaller = registry.lookup(classOf[ju.Date])
      assert(dateMarshaller != null)
      assert(dateMarshaller.isInstanceOf[DateMarshaller])
    }
  }
}