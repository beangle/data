package org.beangle.data.jpa.hibernate

import org.beangle.data.model.bind.AbstractPersistModule
import org.beangle.data.jpa.model.LongIdResource
import org.beangle.data.jpa.model.LongDateIdResource
import org.beangle.data.jpa.model.IntIdResource
import org.beangle.data.jpa.model.UserAb
import org.beangle.data.jpa.model.Role

class TestPersistModule extends AbstractPersistModule {

  protected def binding(): Unit = {
    add(classOf[LongIdResource])
    add(classOf[LongDateIdResource])
    add(classOf[IntIdResource])
    add(classOf[UserAb])
    add(classOf[Role])
  }
}
