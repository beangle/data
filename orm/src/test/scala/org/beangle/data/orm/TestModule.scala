package org.beangle.data.orm

class TestModule extends MappingModule {

  override def binding(): Unit = {
    autoIncrement()
    bind[TestUser]
    .on(e =>
      declare(
        e.properties is depends("user")))

    bind[TestRole]
    bind[UserProperty]
  }
}