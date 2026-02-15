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

package org.beangle.data.hibernate.tx

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.transaction.TransactionDefinition

import java.util as ju

/** Java 风格接口，便于 getMethod 反射 */
trait SampleService {

  def getById(id: Long): String

  def getAll(): ju.List[String]

  def findByName(name: String): String

  def save(entity: String): Unit

  def update(entity: String): Unit

  def delete(id: Long): Unit
}

/** 具体实现类，用于测试 Object 方法（equals 等）被排除 */
class SampleServiceImpl extends SampleService {

  override def getById(id: Long): String = ???
  override def getAll(): ju.List[String] = ???
  override def findByName(name: String): String = ???
  override def save(entity: String): Unit = ()
  override def update(entity: String): Unit = ()
  override def delete(id: Long): Unit = ()
}

class BuildAttributeSourceTest extends AnyFunSpec with Matchers {

  def props(pairs: (String, String)*): ju.Properties = {
    val p = new ju.Properties
    pairs.foreach { case (k, v) => p.setProperty(k, v) }
    p
  }

  describe("TransactionalAdvisor.buildAttributeSource") {

    it("should match exact method name") {
      val props = this.props("save" -> "PROPAGATION_REQUIRED", "delete" -> "PROPAGATION_REQUIRES_NEW")
      val source = TransactionalAdvisor.buildAttributeSource(classOf[SampleService], props)

      val saveMethod = classOf[SampleService].getMethod("save", classOf[String])
      val attr = source.getTransactionAttribute(saveMethod, classOf[SampleService])
      attr should not be null
      attr.getPropagationBehavior shouldBe TransactionDefinition.PROPAGATION_REQUIRED

      val deleteMethod = classOf[SampleService].getMethod("delete", java.lang.Long.TYPE)
      val deleteAttr = source.getTransactionAttribute(deleteMethod, classOf[SampleService])
      deleteAttr should not be null
      deleteAttr.getPropagationBehavior shouldBe TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    it("should match get* pattern") {
      val props = this.props("get*" -> "PROPAGATION_REQUIRED,readOnly")
      val source = TransactionalAdvisor.buildAttributeSource(classOf[SampleService], props)

      val getByIdMethod = classOf[SampleService].getMethod("getById", java.lang.Long.TYPE)
      val attr = source.getTransactionAttribute(getByIdMethod, classOf[SampleService])
      attr should not be null
      attr.isReadOnly shouldBe true

      val getAllMethod = classOf[SampleService].getMethod("getAll")
      val getAllAttr = source.getTransactionAttribute(getAllMethod, classOf[SampleService])
      getAllAttr should not be null
      getAllAttr.isReadOnly shouldBe true
    }

    it("should match *ById pattern") {
      val props = this.props("*ById" -> "PROPAGATION_REQUIRED,readOnly")
      val source = TransactionalAdvisor.buildAttributeSource(classOf[SampleService], props)

      val getByIdMethod = classOf[SampleService].getMethod("getById", java.lang.Long.TYPE)
      val attr = source.getTransactionAttribute(getByIdMethod, classOf[SampleService])
      attr should not be null
      attr.isReadOnly shouldBe true
    }

    it("should prefer longer pattern when multiple match") {
      val props = this.props(
        "get*" -> "PROPAGATION_SUPPORTS,readOnly",
        "*ById" -> "PROPAGATION_REQUIRED,readOnly"
      )
      val source = TransactionalAdvisor.buildAttributeSource(classOf[SampleService], props)

      val getByIdMethod = classOf[SampleService].getMethod("getById", java.lang.Long.TYPE)
      val attr = source.getTransactionAttribute(getByIdMethod, classOf[SampleService])
      attr should not be null
      attr.getPropagationBehavior shouldBe TransactionDefinition.PROPAGATION_REQUIRED
      attr.isReadOnly shouldBe true
    }

    it("should return null for methods without matching pattern") {
      val props = this.props("save*" -> "PROPAGATION_REQUIRED")
      val source = TransactionalAdvisor.buildAttributeSource(classOf[SampleService], props)

      val deleteMethod = classOf[SampleService].getMethod("delete", java.lang.Long.TYPE)
      val attr = source.getTransactionAttribute(deleteMethod, classOf[SampleService])
      attr shouldBe null
    }

    it("should support timeout and isolation") {
      val props = this.props("save" -> "PROPAGATION_REQUIRED,timeout_30,ISOLATION_READ_COMMITTED")
      val source = TransactionalAdvisor.buildAttributeSource(classOf[SampleService], props)

      val saveMethod = classOf[SampleService].getMethod("save", classOf[String])
      val attr = source.getTransactionAttribute(saveMethod, classOf[SampleService])
      attr should not be null
      attr.getTimeout shouldBe 30
      attr.getIsolationLevel shouldBe TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    it("should not include Object methods") {
      val props = this.props("get*" -> "PROPAGATION_REQUIRED,readOnly")
      val source = TransactionalAdvisor.buildAttributeSource(classOf[SampleServiceImpl], props)

      val equalsMethod = classOf[SampleServiceImpl].getMethod("equals", classOf[Object])
      val attr = source.getTransactionAttribute(equalsMethod, classOf[SampleServiceImpl])
      attr shouldBe null
    }
  }
}
