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

import org.beangle.data.hibernate.model.{ExtendRole, Role}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** 预生成懒加载代理的运行期回归：session 清除后访问 to-one 关联，
 * 由 [[org.beangle.data.hibernate.bytecode.BeangleBytecodeProvider]] 按名实例化
 * `<Entity>$HibernateProxy` 并初始化（JVM 与 native 同一条代码路径）。
 */
class LazyProxyTest extends AnyFunSpec, Matchers {

  val ds = Tests.buildTestH2()
  val builder = new LocalSessionFactoryBean(ds)
  builder.ormLocation = "classpath*:beangle.xml"
  builder.properties.put("hibernate.show_sql", "true")
  builder.properties.put("hibernate.hbm2ddl.auto", "create")
  builder.init()
  val sf = builder.getObject
  val entityDao = new HibernateEntityDao(sf)
  entityDao.init()
  SessionHelper.openSession(sf)

  it("initializes a pre-generated lazy proxy after session clear") {
    val session = SessionHelper.currentSession(sf)
    val r1 = new ExtendRole(1)
    r1.name = "parent"
    val r2 = new ExtendRole(2)
    r2.parent = Some(r1)
    val tx = session.beginTransaction()
    entityDao.saveOrUpdate(r1)
    entityDao.saveOrUpdate(r2)
    session.flush()
    tx.commit()
    session.clear()

    val loaded = entityDao.get(classOf[Role], r2.id)
    loaded.parent shouldBe defined
    loaded.parent.get.id shouldBe r1.id
    loaded.parent.get.name shouldBe "parent"
  }
}
