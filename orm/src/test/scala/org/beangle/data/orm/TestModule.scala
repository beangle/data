package org.beangle.data.orm

class TestModule extends MappingModule {

  override def binding(): Unit = {
    autoIncrement()
    bind[TestUser].on(e => declare(
      e.properties is depends("user"),
      e.friends is eleColumn("friend_user_id"),
      e.tags is (table("users_tags"), eleColumn("value2"), eleLength(200))))
    bind[TestRole].on(e => declare(
      e.name is unique))
    bind[UserProperty]
  }
}