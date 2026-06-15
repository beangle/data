/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.hibernate

import org.beangle.commons.json.{Json, JsonObject}
import org.beangle.data.hibernate.format.{BeangleJsonFormatMapper, BeangleXmlFormatMapper}
import org.beangle.data.hibernate.model.{Member, Name, User}
import org.beangle.data.hibernate.udt.JsonType
import org.hibernate.cfg.MappingSettings
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.`type`.descriptor.java.JavaType
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class FormatMapperTest extends AnyFunSpec, Matchers {

  private val CharactorJson = """{"favorite":"reading,skating","color":"red"}"""

  private val ds = Tests.buildTestH2()
  private val builder = new LocalSessionFactoryBean(ds)
  builder.ormLocation = "classpath*:beangle.xml"
  builder.properties.put("hibernate.show_sql", "false")
  builder.properties.put("hibernate.hbm2ddl.auto", "create")
  builder.init()
  private val sf = builder.getObject

  describe("BeangleJsonFormatMapper") {
    it("should round-trip JsonObject like User.charactor") {
      val mapper = new BeangleJsonFormatMapper()
      val javaType = new JsonType(classOf[JsonObject]).asInstanceOf[JavaType[JsonObject]]

      val parsed = mapper.fromString(CharactorJson, javaType, null)
      parsed shouldBe a[JsonObject]
      parsed.query("color") shouldBe Some("red")
      parsed.query("favorite").get.toString should include("reading")

      val serialized = mapper.toString(parsed, javaType, null)
      Json.parseObject(serialized).query("color") shouldBe Some("red")
    }

    it("should be registered on SessionFactory instead of Jackson/JsonB") {
      val options = sf.asInstanceOf[SessionFactoryImplementor].getSessionFactoryOptions
      val jsonMapper = options.getJsonFormatMapper
      val xmlMapper = options.getXmlFormatMapper

      jsonMapper shouldBe a[BeangleJsonFormatMapper]
      xmlMapper shouldBe a[BeangleXmlFormatMapper]

      jsonMapper.getClass.getName should include("BeangleJsonFormatMapper")
      jsonMapper.getClass.getName should not include "jackson"
      jsonMapper.getClass.getName should not include "JsonB"
    }

    it("should be configured via MappingSettings defaults") {
      builder.properties.get(MappingSettings.JSON_FORMAT_MAPPER) shouldBe classOf[BeangleJsonFormatMapper].getName
      builder.properties.get(MappingSettings.XML_FORMAT_MAPPER) shouldBe classOf[BeangleXmlFormatMapper].getName
    }
  }

  describe("User.charactor with FormatMapper-backed SessionFactory") {
    it("should persist and load JsonObject through Hibernate") {
      SessionHelper.openSession(sf)
      val session = sf.getCurrentSession
      val transaction = session.beginTransaction()
      val entityDao = new HibernateEntityDao(sf)
      entityDao.init()

      val user = new User(99)
      user.member = new Member
      user.name = new Name
      user.name.first = "Mapper"
      user.name.last = "Test"
      user.createdOn = new java.sql.Date(System.currentTimeMillis())
      user.charactor = Json.parseObject(CharactorJson)

      entityDao.saveOrUpdate(user)
      session.flush()
      session.clear()

      val loaded = entityDao.get(classOf[User], user.id)
      loaded.charactor shouldBe a[JsonObject]
      loaded.charactor.query("color") shouldBe Some("red")
      loaded.charactor.query("favorite").get.toString should include("skating")

      transaction.commit()
      session.clear()
    }
  }
}
