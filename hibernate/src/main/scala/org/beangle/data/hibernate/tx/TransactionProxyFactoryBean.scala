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

import org.springframework.aop.framework.{AbstractSingletonProxyFactoryBean, ProxyFactory}
import org.springframework.transaction.TransactionManager
import org.springframework.transaction.interceptor.{TransactionAttribute, TransactionAttributeSource, TransactionalProxy}

import java.util as ju

/**
 * 事务代理工厂：为目标对象创建事务代理。
 *
 * 配置 target、transactionManager、transactionAttributes 即可。
 */
class TransactionProxyFactoryBean extends AbstractSingletonProxyFactoryBean {

  var transactionManager: TransactionManager = _
  var transactionAttributes: ju.Properties = _

  override protected def createMainInterceptor: AnyRef = {
    val targetClass = getObjectType
    assert(null != targetClass, "Cannot find TransactionProxyFactoryBean target class")
    val source = buildAttributeSource(targetClass, transactionAttributes)
    new TransactionalAdvisor(transactionManager, source)
  }

  override protected def postProcessProxyFactory(proxyFactory: ProxyFactory): Unit = {
    proxyFactory.addInterface(classOf[TransactionalProxy])
  }

  protected def buildAttributeSource(targetClass: Class[_], attributes: ju.Properties): TransactionAttributeSource = {
    TransactionalAdvisor.buildAttributeSource(targetClass, attributes)
  }
}
