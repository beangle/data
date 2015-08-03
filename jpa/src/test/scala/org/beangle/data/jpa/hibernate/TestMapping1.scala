package org.beangle.data.jpa.hibernate

import scala.reflect.runtime.universe
import org.beangle.data.jpa.model.{ IntIdResource, LongDateIdResource, LongIdResource, Role }
import org.beangle.data.model.bind.Mapping
import org.beangle.data.jpa.model.User
import org.beangle.data.jpa.model.{ Coded, Menu, CodedEntity, StringIdCodedEntity }
import org.beangle.data.jpa.hibernate.udt.ValueType
import org.beangle.commons.lang.time.WeekState

class TestMapping1 extends Mapping {

  override def registerTypes(): Unit = {
    typedef(classOf[WeekState], classOf[ValueType].getName, Map("valueClass" -> classOf[WeekState].getName))
  }

  def binding(): Unit = {
    defaultIdGenerator("table_sequence")
    defaultCache("test_cache_region", "read-write")

    bind[LongIdResource]
    bind[LongDateIdResource]
    bind[IntIdResource]

    bind[Coded].on(c => declare(
      c.code is (notnull, length(20))))
    bind[User].on(e => declare(
      e.name & e.createdOn are notnull)).generator("native")

    //issues:
    // 1.users_roles without primary key
  }

}

