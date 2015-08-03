package org.beangle.data.jpa.hibernate

import scala.reflect.runtime.universe

import org.beangle.commons.lang.annotation.beta
import org.beangle.data.jpa.model.{ CodedEntity, Department, ExtendRole, Menu, Role, StringIdCodedEntity }
import org.beangle.data.model.bind.Mapping

class TestMapping2 extends Mapping {

  def binding(): Unit = {
    defaultIdGenerator("table_sequence")
    defaultCache("test_cache_region", "read-write")

    bind[Role].on(r => declare(
      r.name is (notnull, length(112), unique),
      r.children is (one2many("parent"), cacheable))).generator("assigned")

    bind[ExtendRole](classOf[Role].getName)

    bind[CodedEntity].on(c => declare(
      c.code is (length(22))))

    bind[StringIdCodedEntity].on(c => declare(
      c.code is (length(28)))).generator("native")

    bind[Menu]
    cache().add(collection[Role]("children"))

    bind[Department].on(e => declare(
      e.children is (one2many("parent"))))
  }

}