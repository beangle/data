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
    bind[LongIdResource]
    bind[LongDateIdResource]
    bind[IntIdResource]

    bind[Coded].on(c => declare(
      c.code is (notnull, length(20))))

    bind[UserAb].generator("native")
    
    bind[Role].on(r => declare(
      r.name is (notnull, length(112), unique),
      r.id is (notnull))).generator("native")

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

import org.squeryl.PrimitiveTypeMode.{ optionString2ScalarString, string2ScalarString }
import org.squeryl.Schema
object Library extends Schema {

  val authors = table[Author]

  on(authors)(s => declare(
    s.email is (unique, indexed("idxEmailAddresses")), //indexes can be named explicitely
    s.firstName is (indexed),
    s.lastName is (indexed, dbType("varchar(255)")), // the default column type can be overriden     
    columns(s.firstName, s.lastName) are (indexed)))

  def a(): Unit = {

    println("a")
  }
}
   