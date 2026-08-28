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

package org.beangle.data.samples.nativeapp

import org.beangle.commons.aot.AotHintRegistrar

/** sample 应用自身的 GraalVM 反射提示。
 *
 * 实体类（Department/Employee/Role/Course）由 AotPlugin 依据 beangle.xml 的 jpa mapping
 * 自动注册（allPublic*），SampleMapping 本体也由 beangle.xml 扫描处理；代理类由
 * ProxyPlugin 生成。实体属性中的 Scala 3 枚举（EmpLevel）由 MetaRegistrar.addMetas
 * 遍历属性自动注册（含伴生对象）。这里只补插件不覆盖的应用面：jcache/caffeine
 * 二级缓存实现（sample 自身声明的依赖）。
 */
class SampleAotHints extends AotHintRegistrar {
  override def registering(): Unit = {
    // jcache/caffeine 二级缓存实现（sample 自身声明的依赖）
    hints.registerType(
      classOf[com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider],
      classOf[com.github.benmanes.caffeine.jcache.copy.JavaSerializationCopier])
  }
}
