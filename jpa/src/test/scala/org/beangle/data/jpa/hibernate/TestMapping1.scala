package org.beangle.data.jpa.hibernate

import scala.reflect.runtime.universe
import org.beangle.commons.lang.annotation.beta
import org.beangle.commons.lang.time.WeekState
import org.beangle.data.jpa.hibernate.udt.ValueType
import org.beangle.data.jpa.model.{ Coded, IntIdResource, LongDateIdResource, LongIdResource, Skill, SkillType, User }
import org.beangle.data.model.bind.Mapping
import org.beangle.commons.lang.time.WeekDay

class TestMapping1 extends Mapping {


  def binding(): Unit = {
    defaultIdGenerator("table_sequence")
    defaultCache("test_cache_region", "read-write")

    bind[LongIdResource]
    bind[LongDateIdResource]
    bind[IntIdResource]

    bind[Coded].on(c => declare(
      c.code is (notnull, length(20))))
    bind[User].on(e => declare(
      e.name.first is unique,
      e.name.first & e.name.last & e.createdOn are notnull,
      e.roleList is ordered)).generator("native")

    bind[SkillType]
    bind[Skill]
  }
}
