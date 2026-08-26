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

package org.beangle.data.hibernate.aot

import org.beangle.commons.aot.AotHintRegistrar
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl
import org.hibernate.event.spi.*
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.persister.collection.{BasicCollectionPersister, OneToManyPersister}
import org.hibernate.persister.entity.{JoinedSubclassEntityPersister, SingleTableEntityPersister, UnionSubclassEntityPersister}
import org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorBuilderImpl
import org.hibernate.resource.transaction.backend.jta.internal.JtaTransactionCoordinatorBuilderImpl
import org.hibernate.tool.schema.internal.script.MultiLineSqlScriptExtractor

/** 复刻 hibernate-graalvm `GraalVMStaticFeature` 的静态反射注册。
 *
 * 对应 `org.hibernate.orm:hibernate-graalvm:7.4.5.Final` 中
 * `org.hibernate.graalvm.internal.GraalVMStaticFeature#beforeAnalysis` 通过
 * `StaticClassLists` 注册的三组反射类（该 Feature 不注册任何资源）：
 *  - typesNeedingAllConstructorsAccessible：CoreMessageLogger 按名查找、Entity/Collection Persister
 *  - typesNeedingDefaultConstructorAccessible：事务协调器、SequenceStyleGenerator、命名策略等默认构造器实例化
 *  - typesNeedingArrayCopy：EventType 声明的监听器数组（`Array.newInstance` 需要）
 *
 * 以 beangle AOT 机制内嵌进 beangle-data-hibernate.jar，使用方无需再依赖 hibernate-graalvm；
 * `UuidVersion6Strategy.Holder`/`UuidVersion7Strategy.Holder` 在该 Feature 中走的是
 * `RuntimeClassInitialization.initializeAtRunTime`（SecureRandom），无法用 reflect/resource 配置表达，
 * 应用构建时需在 native-image 参数中补充 `--initialize-at-run-time`。
 */
class HibernateAotHints extends AotHintRegistrar {
  override def registering(): Unit = {
    hints.registerType(
      // typesNeedingAllConstructorsAccessible
      classOf[org.hibernate.internal.CoreMessageLogger_$logger],
      classOf[OneToManyPersister], classOf[BasicCollectionPersister],
      classOf[JoinedSubclassEntityPersister], classOf[UnionSubclassEntityPersister],
      classOf[SingleTableEntityPersister],
      // typesNeedingDefaultConstructorAccessible
      classOf[JdbcResourceLocalTransactionCoordinatorBuilderImpl], classOf[SequenceStyleGenerator],
      classOf[ImplicitNamingStrategyJpaCompliantImpl], classOf[JtaTransactionCoordinatorBuilderImpl],
      classOf[MultiLineSqlScriptExtractor],
      // typesNeedingArrayCopy —— 与 org.hibernate.event.spi.EventType 保持同步
      classOf[Array[LoadEventListener]], classOf[Array[InitializeCollectionEventListener]],
      classOf[Array[PersistEventListener]], classOf[Array[MergeEventListener]],
      classOf[Array[DeleteEventListener]], classOf[Array[ReplicateEventListener]],
      classOf[Array[FlushEventListener]], classOf[Array[AutoFlushEventListener]],
      classOf[Array[PreFlushEventListener]], classOf[Array[DirtyCheckEventListener]],
      classOf[Array[FlushEntityEventListener]], classOf[Array[ClearEventListener]],
      classOf[Array[EvictEventListener]], classOf[Array[LockEventListener]],
      classOf[Array[RefreshEventListener]], classOf[Array[PreLoadEventListener]],
      classOf[Array[PreDeleteEventListener]], classOf[Array[PreUpdateEventListener]],
      classOf[Array[PreUpsertEventListener]], classOf[Array[PreInsertEventListener]],
      classOf[Array[PostLoadEventListener]], classOf[Array[PostDeleteEventListener]],
      classOf[Array[PostUpdateEventListener]], classOf[Array[PostUpsertEventListener]],
      classOf[Array[PostInsertEventListener]],
      classOf[Array[PreCollectionRecreateEventListener]], classOf[Array[PreCollectionRemoveEventListener]],
      classOf[Array[PreCollectionUpdateEventListener]], classOf[Array[PostCollectionRecreateEventListener]],
      classOf[Array[PostCollectionRemoveEventListener]], classOf[Array[PostCollectionUpdateEventListener]]
    )
  }
}
