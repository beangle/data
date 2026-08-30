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

package org.beangle.data.model.aot

import jakarta.persistence.{Embeddable, Entity}
import org.beangle.commons.aot.AotHintRegistrar
import org.beangle.commons.bean.component
import org.beangle.commons.lang.annotation.value
import org.beangle.data.model.annotation.*

/** beangle-data-model 库自身的 GraalVM native-image 反射提示。
 *
 * 构建期由 [[org.beangle.commons.aot.AotHintGenerator]] 扫描并生成
 * `META-INF/native-image` 配置，随 beangle-data-model.jar 内嵌发布
 * （GraalVM 构建时自动发现并合并）。涵盖映射期 `getAnnotation`/`isAnnotationPresent`
 * 查询的 `jakarta.persistence.Entity`/`Embeddable`、`commons` 的
 * `org.beangle.commons.bean.component`/`org.beangle.commons.lang.annotation.value`
 * 以及 model 自身的 `archive`/`code`/`config`/`flash`/`flow`/`log`/`shard`/`temp` 注解。
 */
class ModelAotHints extends AotHintRegistrar {
  override def registering(): Unit = {
    hints.registerType(
      classOf[Entity], classOf[Embeddable],
      classOf[component], classOf[value],
      classOf[archive], classOf[code], classOf[config], classOf[flash],
      classOf[flow], classOf[log], classOf[shard], classOf[temp],
    )
    // beangle-data-model 的 id 访问基类：运行期 BeanInfo.from 经实体 getMethods 拿到
    // 继承的 id/id_=/persisted/equals（均 public），默认 allPublicMethods 即覆盖。
    // NumId 覆盖 LongId/IntId/ShortId 家族（它们不声明新方法）；StringId 自声明 id 访问器
    hints.registerType(classOf[org.beangle.data.model.NumId[_]], classOf[org.beangle.data.model.StringId])
    hints.registerPattern("META-INF/beangle/ddl/.*", ".*\\.zh_CN")
  }
}
