package org.beangle.data.jpa.hibernate

import scala.reflect.runtime.universe
import org.beangle.commons.lang.annotation.beta
import org.beangle.commons.lang.time.WeekState
import org.beangle.data.jpa.hibernate.udt.ValueType
import org.beangle.data.jpa.model.{ Coded, IntIdResource, LongDateIdResource, LongIdResource, Skill, SkillType, User }
import org.beangle.data.model.bind.Mapping
import org.beangle.commons.lang.time.WeekDay

object TestMapping1 extends Mapping {

  def binding(): Unit = {
    defaultIdGenerator("table_sequence")
    defaultCache("test_cache_region", "read-write")

    bind[LongIdResource]
    bind[LongDateIdResource]
    bind[IntIdResource]

    bind[Coded].on(c => declare(
      c.code is (notnull, length(20))))
    bind[User].on(e => declare(
      e.name.first is (unique, column("first_name")),
      e.name.first & e.name.last & e.createdOn are notnull,
      e.roleList is (ordered, table("role_list_xyz")),
      e.properties is (table("users_props"), eleColumn("value2"), eleLength(200)))).generator("native")

    bind[SkillType]
    bind[Skill].table("skill_list")
  }
}
