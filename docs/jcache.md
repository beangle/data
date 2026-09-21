# Hibernate 二级缓存与 caffeine.jcache 配置

> 本文说明 beangle-data-hibernate 二级缓存的分层、`application.conf` 里 `caffeine.jcache`
> 配置的写法（含区域名的点号陷阱），以及本库为什么自带区域工厂
> `org.beangle.data.hibernate.cache.CaffeineRegionFactory`：
> 映射里声明过、但没在配置里出现的区域（下称"缺失区域"），也要完整继承
> `caffeine.jcache.default` 的过期策略。
> 配套实现见 `hibernate/src/main/scala/org/beangle/data/hibernate/cache/`，
> 区域工厂的默认值见 `ConfigurationBuilder.addDefaultProperties`。

---

## 1. 三层结构

| 层 | 角色 | 关键类 |
|---|---|---|
| Hibernate ORM | 二级缓存 SPI：实体/集合/查询/时间戳四类区域 | `org.hibernate.cache.spi.RegionFactory` |
| hibernate-jcache | 把 JCache(JSR-107) 当作二级缓存实现 | `org.hibernate.cache.jcache.internal.JCacheRegionFactory` |
| caffeine-jcache | JCache provider（真正存数据的缓存） | `com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider` |

`ConfigurationBuilder` 打开二级缓存与查询缓存（`hibernate.cache.use_second_level_cache=true`、
`hibernate.cache.use_query_cache=true`），并在 caffeine-jcache 位于 classpath 时：

- `hibernate.javax.cache.provider` = `CaffeineCachingProvider`；
- `hibernate.cache.region.factory_class` = `org.beangle.data.hibernate.cache.CaffeineRegionFactory`
  （caffeine 不在时退回 Hibernate 自带的 `jcache`）；
- `hibernate.javax.cache.missing_cache_strategy` = `create`。

缓存策略（容量、过期、统计等）一律写在 `application.conf` 的 `caffeine.jcache` 下。

## 2. 区域名与配置写法：点号陷阱

区域名由 Hibernate 决定，**不是**随便起的名字：

| 区域 | 名字 |
|---|---|
| 实体/集合 | 默认是实体类全名，如 `org.beangle.ems.core.user.model.User`；声明过 `defaultCache("ems", ...)` 时用该名 |
| 查询缓存 | `default-query-results-region` |
| 时间戳 | `default-update-timestamps-region` |

caffeine 查配置用的是 `config.hasPath("caffeine.jcache." + cacheName)`（`TypesafeConfigurator.from`），
对点号区域名必须写成**嵌套对象**：

```hocon
caffeine.jcache {
  org { beangle { ems { core { user { model { User.policy { maximum.size = 1000 } } } } } } }
}
```

写成带引号的单键 `"org.beangle...User" { ... }` 是无效的：`TypesafeConfigurator.cacheNames()` 能列出它
（于是 caffeine 认为该区域"已由外部配置"，`CacheManagerImpl.getCache` 不会再临时建它），
但 `from()` 用点号路径查不到，最终 `createCache` 抛
`CacheException: Cache ... is configured externally`。

结论：**不要逐个实体配置**，只用 `default` 兜底，必要时再为少数区域单独覆盖。

## 3. `default` 与缺失区域：为什么 `lazy-expiration` 曾经不生效

缺失区域由 `hibernate.javax.cache.missing_cache_strategy` 决定：

| 取值 | 行为 |
|---|---|
| `fail` | 抛 `CacheException` |
| `create-warn` | 现建缓存并告警（Hibernate 未配置时的内置默认） |
| `create` | 静默现建缓存（Beangle 默认，便于只写 `default`） |

Hibernate 现建缓存时用的是 `new MutableConfiguration<>()`。caffeine 收到这份配置后，会先把
`caffeine.jcache.default` 叠加上去（`CacheFactory.resolveConfigurationFor`），但紧接着执行：

```java
template.setExpiryPolicyFactory( complete.getExpiryPolicyFactory() );
```

即用 `MutableConfiguration` 的 `EternalExpiryPolicy` **覆盖**了 `default` 的过期策略，于是：

| `default` 中的设置 | 缺失区域是否生效 |
|---|---|
| `policy.maximum.size` / `weight` | 生效 |
| `policy.eager-expiration.*` | 生效 |
| `policy.lazy-expiration.*` | **失效**（被 `EternalExpiryPolicy` 覆盖，等于永不过期） |
| `store-by-value`、`monitoring` 等 | 生效 |

所以只写 `default.lazy-expiration` 时，实体区域（已在配置里逐个写出、由 caffeine 自己按外部配置建的）
过期正常，而缺失区域却永不过期——这正是本库自带区域工厂要解决的问题。

## 4. 本库的做法：`CaffeineRegionFactory`

`CaffeineRegionFactory` 只覆写"现建缺失区域"这一条路径（`createCache`）：
改用 caffeine 的 `TypesafeConfigurator.defaults(config)`——也就是完整的
`caffeine.jcache.default`——建缓存，因此 `lazy-expiration` 与 `eager-expiration`、容量一并生效。
其余行为与上游 `JCacheRegionFactory` 一致：

- `fail` / `create-warn` / `create` 三种语义照旧（`create-warn` 会告警）；
- 已在配置里出现的区域仍走 caffeine 自己的外部配置路径（`CacheManagerImpl.getCache` →
  `CacheFactory.tryToCreateFromExternalSettings`，`default` 只是它的兜底）；
- 查询/时间戳区域若没单独配置，与其它缺失区域一样按 `default` 现建；
- provider 不是 caffeine（如 ehcache）、或读不到配置时，退回 `new MutableConfiguration<>()`，
  与上游行为完全相同。

要回到上游行为（缺失区域不继承 `default.lazy-expiration`），显式配置 `jcache` 即可：

```hocon
# application.conf：这里的键会被加上 hibernate. 前缀（见 ConfigurationBuilder.importSysProperties）
jpa.hibernate.settings {
  # 默认已是下面这个全类名；写 jcache 则回到上游实现
  cache.region.factory_class = org.beangle.data.hibernate.cache.CaffeineRegionFactory
  # javax.cache.missing_cache_strategy = create-warn
}
```

### 为什么没有把它注册成短名

本库**刻意不**通过 `META-INF/services/...StrategyRegistrationProvider` 给 `RegionFactory` 注册短名
（如 `caffeine`）：Hibernate 的 `RegionFactoryInitiator` 在没有显式配置 factory class 时，是按
"注册实现的数量"来挑默认实现的——

```java
final var implementors = selector.getRegisteredStrategyImplementors( RegionFactory.class );
if ( setting == null && implementors.size() != 1 ) {
  if ( useSecondLevelCache == TRUE || useQueryCache == TRUE ) {
    throw new CacheException( "Caching was explicitly requested, but no RegionFactory was defined ..." );
  }
}
```

`getRegisteredStrategyImplementors` 是按类去重的，多注册一个实现就让它从 1 变 2，于是绕过
`ConfigurationBuilder`、直接 `new Configuration()`（或 JPA）构建会话工厂且只打开二级缓存的应用，
会从"自动选中 `JCacheRegionFactory`"变成启动报 `CacheException`（不开二级缓存时则静默退化成
`NoCachingRegionFactory`）。实测对比：注册短名后 `count=2` → 裸配置启动失败；不注册时 `count=1`
→ 正常自动选中 `JCacheRegionFactory`（本库现在就是这个状态）。

全类名不需要注册（`StrategySelectorImpl.selectStrategyImplementor` 查不到名字时会
`classForName` 回退），`ConfigurationBuilder` 用的就是全类名，所以默认路径不依赖 ServiceLoader；
确实想要短名的话，由应用自己在 `applyStrategySelectors` 里注册即可。

启动日志中可以看到生效的区域工厂：
`HHH90001028: Second-level cache region factory [org.beangle.data.hibernate.cache.CaffeineRegionFactory]`。

## 5. `lazy-expiration` 与 `eager-expiration` 的区别

| | `lazy-expiration`（JSR-107 语义） | `eager-expiration`（Caffeine 原生） |
|---|---|---|
| 机制 | 给缓存装 `ExpiryPolicy`（caffeine 的 `JCacheExpiryPolicy`），每次读写算过期时间 | `expireAfterWrite/Access` / `Expiry`，由 Caffeine 维护并回收空间 |
| 过期后的条目 | 仍可能留在缓存里，直到被访问或维护时清理 | 达到阈值即可被回收（`estimatedSize` 随之下降） |
| 表达力 | `creation`/`update`/`access` 可分别设 `eternal`；`update = null` 表示更新不改动原过期时间 | `after-write`、`after-access`、`variable`（自定义 `Expiry` 类） |
| 建议用途 | 表达业务 TTL（"这条缓存最多活 5 分钟"） | 兜底，防止无限期缓存 / 限制内存 |

两者可以同时配置，谁先到期谁生效（例如 `lazy-expiration.creation = 1s` 配
`eager-expiration.after-write = 5m`，实际 1s 左右就查不到了）。只配了 `eager-expiration` 时
caffeine 不再额外安装 `ExpiryPolicy`（`CacheFactory.Builder.configureJCacheExpiry` 会跳过），略有性能收益。

## 6. 推荐配置

```hocon
caffeine.jcache {
  # 兜底：所有没单独配置的区域（实体类全名、查询/时间戳区域……）
  default.policy {
    maximum.size = 10000
    lazy-expiration {
      creation = 5m
      update = 5m
      access = 5m
    }
    # 有 Caffeine 原生 TTL 兜底，缓存不会无限期增长
    eager-expiration {
      after-write = 5m
    }
  }

  # 具体区域要写成嵌套对象（见 §2），这里给查询缓存单独收紧
  default-query-results-region.policy {
    maximum.size = 1000
    lazy-expiration {
      creation = 1m
      update = 1m
      access = 1m
    }
  }
}
```

要点：

- `maximum.size` 一定要设，否则缓存没有容量上限；
- 缺失区域要生效的过期策略写在 `default` 里（配合本库的区域工厂）；
- 时间戳区域（`default-update-timestamps-region`）被过期只会让 Hibernate 重查数据库（不会算错），
  但如果希望它更长寿，可为它单独配置（如只给 `eager-expiration`，或给较长的 `lazy-expiration`）；
- 排查时先看启动日志的区域工厂，再用 `CacheManager.getCache(区域名).getConfiguration(CaffeineConfiguration.class)`
  确认实际生效的容量与过期策略。

## 7. 相关源码与测试

- 本库区域工厂：`hibernate/src/main/scala/org/beangle/data/hibernate/cache/CaffeineRegionFactory.scala`
- 配置默认值：`hibernate/src/main/scala/org/beangle/data/hibernate/ConfigurationBuilder.scala`
- 回归测试：`hibernate/src/test/scala/org/beangle/data/hibernate/cache/CaffeineRegionFactoryTest.scala`
  （缺失区域继承 `default`；显式用 `jcache` 时 `lazy-expiration` 被覆盖为 eternal）
- caffeine 侧：`com.github.benmanes.caffeine.jcache.CacheFactory#resolveConfigurationFor`、`TypesafeConfigurator`
- Hibernate 侧：`JCacheRegionFactory#createCache`、`org.hibernate.cache.jcache.MissingCacheStrategy`
