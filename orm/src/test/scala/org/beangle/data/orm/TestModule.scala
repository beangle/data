package org.beangle.data.orm

class TestModule extends MappingModule {

  override def binding(): Unit = {
    bind[TestUser]
  }
}