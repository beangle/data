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

package org.beangle.data.hibernate.nativeimage

import org.beangle.commons.lang.reflect.BeanInfos
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** 预生成代理类（`<Entity>$HibernateProxy`）的 BeanInfo 复用实体 BeanMeta：
 * `BeanInfos.get` 对"父类 `$` 子类"回退到父类元数据（native 下无需代理类字段反射）。
 */
class ProxyBeanInfoTest extends AnyFunSpec, Matchers {

  it("BeanInfos.get falls back to the entity BeanMeta for generated proxy classes") {
    // 隔离 DomainFactory 等已注册的 proxy→实体 BeanInfo 缓存，确保走父类回退路径
    BeanInfos.clear()
    val proxyClazz = Class.forName("org.beangle.data.hibernate.model.User$HibernateProxy")
    val bi = BeanInfos.get(proxyClazz)
    bi.clazz shouldBe proxyClazz
    bi.properties.contains("id") shouldBe true
    bi.properties.contains("name") shouldBe true
    bi.ctors shouldBe empty
  }
}
