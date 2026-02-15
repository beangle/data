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

import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.TransactionManager
import org.springframework.transaction.interceptor.TransactionalProxy

import java.util as ju

/**
 * 编程式创建事务代理的入口。
 *
 * 无需 Spring 容器，一行代码即可得到带事务的代理对象：
 * {{{
 *   val service = TransactionalProxy.apply(myService, txManager, props)
 * }}}
 */
object TransactionalProxy {

  /**
   * @param target     目标对象
   * @param tm         事务管理器
   * @param properties 事务属性，如 "get*" -> "PROPAGATION_REQUIRED,readOnly", "save" -> "PROPAGATION_REQUIRED"
   * @return 事务代理对象（实现与 target 相同的接口）
   */
  def apply[T](target: T, tm: TransactionManager, properties: ju.Properties): T = {
    val factory = new ProxyFactory(target)
    val source = TransactionalAdvisor.buildAttributeSource(target.asInstanceOf[AnyRef].getClass, properties)
    factory.addAdvisor(new TransactionalAdvisor(tm, source))
    factory.addInterface(classOf[TransactionalProxy])
    factory.getProxy.asInstanceOf[T]
  }
}
