package org.beangle.data.jpa.hibernate

import scala.reflect.runtime.universe
import org.beangle.data.jpa.model.{ IntIdResource, LongDateIdResource, LongIdResource, Role }
import org.beangle.data.model.bind.PersistModule
import org.beangle.data.jpa.model.UserAb
import org.beangle.data.jpa.model.{ Coded, Menu, CodedEntity, StringIdCodedEntity }
import org.beangle.data.model.bind.PersistModule

class TestPersistModule1 extends PersistModule {

  protected def binding(): Unit = {
    defaultIdGenerator("table_sequence")
    defaultCache("test_cache_region", "read-write")

    bind[LongIdResource]
    bind[LongDateIdResource]
    bind[IntIdResource]

    bind[Coded].on(c => declare(
      c.code is (notnull, length(20))))
    bind[UserAb].generator("native")
  }

}

