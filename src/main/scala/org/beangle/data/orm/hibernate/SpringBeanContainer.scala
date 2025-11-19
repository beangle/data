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

package org.beangle.data.orm.hibernate

import org.hibernate.`type`.spi.TypeBootstrapContext
import org.hibernate.resource.beans.container.spi.{BeanContainer, ContainedBean}
import org.hibernate.resource.beans.spi.BeanInstanceProducer
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.config.{AutowireCapableBeanFactory, ConfigurableListableBeanFactory}
import org.springframework.util.ConcurrentReferenceHashMap

object SpringBeanContainer {

  final class SpringContainedBean[B](val beanInstance: B, val destructionCallback: B => Unit = null)
    extends ContainedBean[B] {
    override def getBeanInstance: B = this.beanInstance

    override def getBeanClass: Class[B] = this.beanInstance.getClass.asInstanceOf[Class[B]]

    def destroyIfNecessary(): Unit = {
      if (this.destructionCallback != null) this.destructionCallback(this.beanInstance)
    }
  }
}

import org.beangle.data.orm.hibernate.SpringBeanContainer.SpringContainedBean

/**
 * Instantiate a new SpringBeanContainer for the given bean factory.
 *
 * @param beanFactory the Spring bean factory to delegate to
 */
final class SpringBeanContainer(private val beanFactory: ConfigurableListableBeanFactory) extends BeanContainer {
  final private val beanCache = new ConcurrentReferenceHashMap[AnyRef, SpringContainedBean[_]]

  override def getBean[B](beanType: Class[B], lifecycleOptions: BeanContainer.LifecycleOptions, fallbackProducer: BeanInstanceProducer): ContainedBean[B] = {
    var bean: SpringContainedBean[_] = null
    if (lifecycleOptions.canUseCachedReferences) {
      bean = this.beanCache.get(beanType)
      if (bean == null) {
        bean = createBean(beanType, lifecycleOptions, fallbackProducer)
        this.beanCache.put(beanType, bean)
      }
    }
    else bean = createBean(beanType, lifecycleOptions, fallbackProducer)
    bean.asInstanceOf[SpringContainedBean[B]]
  }

  override def getBean[B](name: String, beanType: Class[B], lifecycleOptions: BeanContainer.LifecycleOptions, fallbackProducer: BeanInstanceProducer): ContainedBean[B] = {
    var bean: SpringContainedBean[_] = null
    if (lifecycleOptions.canUseCachedReferences) {
      bean = this.beanCache.get(name)
      if (bean == null) {
        bean = createBean(name, beanType, lifecycleOptions, fallbackProducer)
        this.beanCache.put(name, bean)
      }
    }
    else bean = createBean(name, beanType, lifecycleOptions, fallbackProducer)
    bean.asInstanceOf[SpringContainedBean[B]]
  }

  override def stop(): Unit = {
    import scala.jdk.CollectionConverters.*
    this.beanCache.values.asScala foreach (_.destroyIfNecessary())
    this.beanCache.clear()
  }

  private def createBean(beanType: Class[_], lifecycleOptions: BeanContainer.LifecycleOptions, fallbackProducer: BeanInstanceProducer): SpringContainedBean[_] = {
    try {
      if (lifecycleOptions.useJpaCompliantCreation)
        new SpringContainedBean(this.beanFactory.createBean(beanType), this.beanFactory.destroyBean)
      else
        new SpringContainedBean(this.beanFactory.getBean(beanType))
    } catch {
      case ex: BeansException =>
        try new SpringContainedBean(fallbackProducer.produceBeanInstance(beanType))
        catch {
          case ex2: RuntimeException =>
            if (ex.isInstanceOf[BeanCreationException]) {
              throw ex
            } else {
              throw ex2
            }
        }
    }
  }

  private def createBean(name: String, beanType: Class[_], lifecycleOptions: BeanContainer.LifecycleOptions, fallbackProducer: BeanInstanceProducer): SpringContainedBean[_] =
    try {
      if (lifecycleOptions.useJpaCompliantCreation) {
        var bean: AnyRef = null
        if (fallbackProducer.isInstanceOf[TypeBootstrapContext]) {
          // Special Hibernate type construction rules, including TypeBootstrapContext resolution.
          bean = fallbackProducer.produceBeanInstance(name, beanType)
        }
        if (this.beanFactory.containsBean(name)) {
          if (bean == null) bean = this.beanFactory.autowire(beanType, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false)
          this.beanFactory.autowireBeanProperties(bean, AutowireCapableBeanFactory.AUTOWIRE_NO, false)
          this.beanFactory.applyBeanPropertyValues(bean, name)
          bean = this.beanFactory.initializeBean(bean, name)
          new SpringContainedBean[AnyRef](bean, (beanInstance: AnyRef) => this.beanFactory.destroyBean(name, beanInstance))
        } else if (bean != null) {
          // No bean found by name but constructed with TypeBootstrapContext rules
          this.beanFactory.autowireBeanProperties(bean, AutowireCapableBeanFactory.AUTOWIRE_NO, false)
          bean = this.beanFactory.initializeBean(bean, name)
          new SpringContainedBean[AnyRef](bean, this.beanFactory.destroyBean)
        } else {
          // No bean found by name -> construct by type using createBean
          new SpringContainedBean(this.beanFactory.createBean(beanType), this.beanFactory.destroyBean)
        }
      } else {
        if (this.beanFactory.containsBean(name)) new SpringContainedBean(this.beanFactory.getBean(name, beanType))
        else new SpringContainedBean(this.beanFactory.getBean(beanType))
      }
    } catch {
      case ex: BeansException =>
        try new SpringContainedBean(fallbackProducer.produceBeanInstance(name, beanType))
        catch {
          case ex2: RuntimeException => if ex.isInstanceOf[BeanCreationException] then throw ex else throw ex2
        }
    }
}
