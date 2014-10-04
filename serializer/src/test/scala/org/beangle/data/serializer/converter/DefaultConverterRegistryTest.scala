package org.beangle.data.serializer.converter

import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.data.serializer.model.Skill

@RunWith(classOf[JUnitRunner])
class DefaultConverterRegistryTest extends FunSpec with Matchers {

  describe("DefaultConverterRegistry") {
    it("lookup converter ") {
      val registry = new DefaultConverterRegistry()
      val skills = Array(new Skill("Play Basketball Best"), new Skill("Play football"))
      val converter = registry.lookup(skills.getClass)
      val converter2 = registry.lookup(skills.getClass)
      assert(converter != null)
      assert(converter.support(skills.getClass))
      assert(converter.getClass.getName == "org.beangle.data.serializer.converter.ArrayConverter")
    }
  }
}