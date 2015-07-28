package org.beangle.data.jpa.hibernate

import scala.reflect.runtime.universe
import org.beangle.data.jpa.model.{ IntIdResource, LongDateIdResource, LongIdResource, Role }
import org.beangle.data.model.bind.PersistModule
import org.beangle.data.jpa.model.UserAb
import org.beangle.data.jpa.model.{ Coded, Menu, CodedEntity, StringIdCodedEntity }
import org.beangle.data.model.bind.PersistModule

class TestPersistModule extends PersistModule {

  protected def binding(): Unit = {
    defaultIdGenerator("table_sequence")
    defaultCache("test_cache_region", "read-write")
    
    bind[LongIdResource]
    bind[LongDateIdResource]
    bind[IntIdResource]

    bind[Coded].on(c => declare(
      c.code is (notnull, length(20))))

    bind[UserAb].generator("native")

    bind[Role].on(r => declare(
      r.name is (notnull, length(112), unique),
      r.childred is (one2many("parent"))
      )).generator("native")

    bind[CodedEntity].on(c => declare(
      c.code is (length(22))))

    bind[StringIdCodedEntity].on(c => declare(
      c.code is (length(28)))).generator("native")

    bind[Menu].on(e => declare(
      e.code is (length(30))))
  }

}

class Author(val id: Long,

  val firstName: String,

  val lastName: String,

  val email: Option[String]) {

  def this() = this(0, "", "", Some(""))

}
