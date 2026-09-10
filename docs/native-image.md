# beangle/data 与 GraalVM native-image 可行性分析

> 目标：让使用 `beangle-data`（`beangle-data-model` + `beangle-data-hibernate`）的应用能够在本地编译，并最终以
> GraalVM native-image 方式构建、运行。
>
> 本文回答两个问题：
> 1. 本项目（一个**库**，且 ORM 绑定是**代码声明式**而非注解）相比 Quarkus 这类框架，native-image 化会不会被"加大阻碍"？
> 2. 需要做哪些必要的更改？

> **⚠️ 2026-09 设计演进（终端集中式，随 beangle/build 下一插件版本落地）**：AOT/Bean
> 元数据/懒加载代理的**生成**正从"库项目各自启用插件、把配置内嵌进自身 jar"迁移为
> **终端集中式**——库项目将不再启用 `AotPlugin`/`MetaPlugin`/`ProxyPlugin`，只携带声明
> 锚点（`aot-registrars.txt`、`beangle.xml`）与 registrar 类；由**终端应用**
> （war/native-image 项目）显式启用这三个插件，扫描整个运行时 classpath（本模块 +
> 依赖项目 + 外部依赖 jar）聚合声明，生成合并的 **GraalVM 25+
> `reachability-metadata.json`**、`beanmeta.idx` 与懒加载代理。
> 本文撰写于该迁移前：data 仓库当前仍按已发布插件（sbt-beangle-build 0.1.x）自动内嵌
> 运行，文中"自动启用""产物内嵌库 jar""各模块 compile 生成 reflect-config"等字样反映
> 迁移前行为（技术审计结论仍有效）；工作树内 `BeangleProxyGenerator` 已先行改输出
> `reachability-metadata.json` 并清理旧 `reflect-config.json`。**最新行为与格式以
> beangle/build 仓库 `docs/aot.md` / `docs/meta.md` / `docs/proxy.md` 与
> `docs/graalvm-reachability-metadata.md` 为准。**

---

## 0. 结论摘要

- **可行性：可行，但成本中等偏高。** 库本身无法"自动"支持 native-image，必须由库提供**构建期工具** + 库侧**运行时代码改造**，
  并由使用方在应用构建流水线里执行。这和 Quarkus/Spring Boot 的"扩展/AOT 引擎"思路一致，只是这里由我们自己承担那部分工作。
- **代码声明式绑定反而是优势，不是阻碍：** 实体集合是"确定、可枚举"的（由 `MappingModule` 的 `bind[X]` 决定），
  天然适合 native-image 的"封闭世界"分析——比注解扫描（Quarkus 需要在 classpath 上做 Jandex 索引）更可控。
- **真正的阻碍来自三个"运行期动态行为"：**
  1. ~~库侧运行期字节码生成~~：OQL 与 `declare` 均已改用 scala.Dynamic（`Prop`/`DeclareProp`），`AccessTracker`/ByteBuddy 已删除——**库侧不再有运行期字节码生成**；
  2. **Hibernate 懒加载代理（ByteBuddy 运行期生成类）**——Hibernate 原生问题（HHH-16013），需构建期预生成代理类；
  3. **反射面过宽**——实体/组件/值类型/枚举的反射注册、Hibernate 内部反射、Spring AOP 代理、JDBC/缓存驱动等，
     需要构建期枚举并生成 GraalVM 配置文件。
- **库 vs 框架（Quarkus）的差异：** 库没有"构建步骤扩展点"，无法在用户的 native-image 构建里自动注入上述工作。
  `AotPlugin`/`MetaPlugin` 在 sbt 中自动启用，由锚定文件（`aot-registrars.txt`/`beangle.xml`）驱动：
  有锚定文件的项目在编译期自动生成配置并打进应用，没有的项目静默跳过，使用方无需显式配置。
- **好消息：** 本项目使用**自研 Hibernate fork**（`org.beangle.hibernate:beangle-hibernate-core`），
  可以直接在 fork 的 jar 里内嵌 `META-INF/native-image/` 元数据，把 hibernate-core 自身的反射/资源注册问题一次解决。

---

## 0.1 当前进展（已实现并验证）

> 本仓库 `develop` 分支已落地 P0 与 P1 的主体改造，JVM 行为不变、全部测试通过：

| 项 | 状态 | 说明 |
|---|---|---|
| 全面 scala.Dynamic 化（P0 增量） | ✅ | `OqlBuilder` 用 `Prop`、`declare` 用 `DeclareProp`（`model/.../orm/DeclareProp.scala`），运行期零类生成、零反射；**`AccessTracker`/`ByteBuddyHelper`/`AccessTrackerGenerator` 及全部 tracker 预生成链路已删除**（`byte_buddy` 依赖移除） |
| AOT 提示统一接入（P1） | ✅ | `ModelAotHints`（model 模块，实体/组件/值类型/库注解）与 `BeangleAotHints`（hibernate 模块，Hibernate 按名反射类/资源）声明库自身固定反射点/资源；hibernate-core 自身的反射元数据已内嵌进 fork jar（P2.1）；构建期经 `AotPlugin` 自动生成 `META-INF/native-image/beangle` 配置并随 beangle-data-model.jar / beangle-data-hibernate.jar 内嵌（GraalVM 构建时自动发现并合并）——库清单、fork 清单与应用清单三方拆分 |
| Bean 元数据静态化（beanmeta.idx） | ✅ | 构建期 `MetaPlugin`（自动启用）读取 `beangle.xml` 声明的 `MetaRegistrar`（MappingModule/BindModule 等），经 `MetaGenerator` 生成二进制 `META-INF/beangle/beanmeta.idx`（编译期 dig 的精确类型）；运行期 `MetaModels` 启动时加载，`MappingModule.bind` 走 `BeanInfos.get` 查询，反射仅作无 idx 时的回退（详见 §1.5） |
| native-image 冒烟（P3） | ✅ | `sample` 工程（独立仓库 beangle/sample）：`MinimalTest` 与 `NativeApp`（MappingModule+H2+OQL+二级缓存/JCache）均完成 native 构建并运行成功；实测补齐项见 P2.1 与 P3.3 |
| 懒加载代理构建期预生成（P2.2 主路线） | ✅ | `ProxyPlugin` 读取 `beangle.xml` 的 jpa/orm mapping，经 `BeangleProxyGenerator` 用 ByteBuddy（构建期仅需）生成 `<Entity>$HibernateProxy.class`，注册进 GraalVM 25+ 的 `beangle/data/reachability-metadata.json`（reflection 条目：无参构造器、`writeReplace` 与 `allPublicMethods`，不开放字段）；运行期由 fork 的 `PrebuiltProxyProvider` 按约定按名加载，`BeanInfos.get` 对代理类自动复用实体 BeanMeta（JVM 与 native 同路径，测试即覆盖）。*本行按迁移前（自动启用、随 jar 内嵌 `reflect-config.json`）撰写；工作树生成器输出已改新格式，集中式改造随下一插件版本落地，见文首注* |
| 回归测试 | ✅ | `model` 35、`hibernate` 24（含 LazyProxyTest）全部通过（`testOnly`）；`sbt clean compile` 全绿 |

### AOT 配置生成（方案：build 插件 AotPlugin + AotHintRegistrar，已接入 sbt）

commons 侧：
- `LogbackAotHints`（`org.beangle.commons.logging`）统一注册 logback/slf4j 的 native 反射
  （Joran appender/encoder/layout、`ch.qos.logback.classic.Logger`、`org.slf4j.spi.LocationAwareLogger`），
  随 beangle-commons.jar 内嵌 `META-INF/native-image/beangle`，使用方无需手写 logback 反射项。

库侧（beangle-data 自身）：
- `ModelAotHints`（`org.beangle.data.model.aot`，`AotHintRegistrar` 子类，随 beangle-data-model.jar
  内嵌）声明映射期反射查询的注解：`jakarta.persistence.Entity`/`Embeddable`、`commons` 的
  `component`/`value` 与 model 自身的 `archive`/`code`/`config`/`flash`/`flow`/`log`/`shard`/`temp`；
- `BeangleAotHints`（`org.beangle.data.hibernate.aot`，`AotHintRegistrar` 子类）声明库自身固定
  反射点与资源 pattern（MappingModule、Hibernate 按名反射类、DDL/zh_CN/services 资源）；
- hibernate-core 自身的反射元数据（`EventType` 声明字段、监听器数组、jboss-logging logger、
  注解类等 63 项，基于 native-image-agent 证据审计，见
  [native-image-reflection-audit.md](native-image-reflection-audit.md)）与
  `UuidVersion6/7Strategy.Holder` 的 `--initialize-at-run-time`
  （SecureRandom）已直接内嵌进 `beangle-hibernate-core` fork jar 的
  `META-INF/native-image/org.hibernate.orm/hibernate-core/`（reflect-config.json +
  native-image.properties），随 jar 自动发现应用——库侧不再需要 `HibernateAotHints`
  （已删除，也不再依赖 hibernate-graalvm）。

`model`/`hibernate` 项目（`AotPlugin` 自动启用）每次 `compile` 由 `AotHintGenerator` 依据各自
`META-INF/beangle/aot-registrars.txt` 清单加载上述子类并生成 `reflect-config.json` /
`resource-config.json`（写入 `Compile / resourceManaged` 的 `META-INF/native-image/beangle`），
分别随 beangle-data-model.jar / beangle-data-hibernate.jar 内嵌发布。

`MappingModule.bind` 的运行期分支通过 `BeanInfos.get` 查询精确 BeanMeta（不依赖编译期挖掘）：
精确类型来自构建期生成的 `beanmeta.idx`（`MetaModels` 启动时加载 `classpath*:META-INF/beangle/beanmeta.idx`），
反射只是无 idx 时的回退。库自身在测试 scope 由 `MetaPlugin`（自动启用）读取测试 `beangle.xml`
声明模块生成 idx（`Test / metaIndex`），应用则在主 scope 由锚定的 `beangle.xml` 把 idx 内嵌进应用 jar。

应用侧（可执行项目）：应用定义自己的 `AotHintRegistrar`/`MetaRegistrar` 子类（实体、方言、驱动等）
并放置锚定文件（`aot-registrars.txt`/`beangle.xml`），插件自动启用后产物同样落盘到
`META-INF/native-image` 并打进应用 jar。`AotHintGenerator` 会对清单/`beangle.xml` 声明的每个
registrar 类**自动注册其类自身**（普通类注册构造器，Scala object 同时注册伴生类 `MODULE$`
字段），保证运行期按名实例化（`Reflections.getInstance`）在 native 镜像中可用；注册枚举类型时
自动补 public 字段（`MODULE$`/`$VALUES` 均为 public static），且 Scala 3 enum 的**伴生对象
自动增量注册**；`MetaRegistrar.addMetas` 还会**遍历实体属性树**（集合元素、递归
`@component` 值类型），实体 `bind`/`register` 后其 Scala 3 枚举属性自动注册——应用
完全无需为枚举写注册代码。`Reflections.getInstance` 经 `getField` 取伴生单例，覆盖
`EnumConverters` 等运行期枚举反射路径，应用无需为这些"机制面"逐类定制。详见
beangle-commons 的 `docs/aot-usage.md` 与 `MetaPlugin`/`AotPlugin` 的 scaladoc。

**尚未实现（P2/P3）：** 仅剩 CI 冒烟（native 构建 + 运行接入 CI，本地已验证可重复执行）；
Metadata 快照（**列定义/Mappings 部分的序列化**，Bean 元数据部分已落地为 `beanmeta.idx`，见 §1.5）
与多方言目标互斥，默认不投入，见 P2.3。

---
## 1. 本库如何构建 ORM 元数据（代码声明式绑定）

不使用 JPA 注解扫描，而是：

1. 应用编写 `MappingModule`（继承 `org.beangle.data.orm.MappingModule`），在 `binding()` 里以
   `bind[T].declare{...}` 声明实体、属性约束、索引、缓存、生成器等（示例见
   `hibernate/src/test/scala/org/beangle/data/hibernate/model/TestMapping1.scala`）。
2. `Mappings.autobind()`（`model/.../orm/Mappings.scala`）执行所有模块，得到纯数据结构的 `OrmEntityType`
   （表、列、外键、属性类型、生成器）。
3. Hibernate 侧通过 `META-INF/services/org.hibernate.boot.spi.MetadataBuilderFactory` 注册的
   `BindMetadataBuilderFactory` 接管 `MetadataSources -> Metadata` 的构建，由 `BindSourceProcessor`
   把 `Mappings` 翻译成 Hibernate 的 `PersistentClass`/值类型/集合绑定。
4. 运行期 Hibernate 再通过 `ScalaPropertyAccessor`（基于反射的 `Method.invoke`）读写属性，用 ByteBuddy 生成懒加载代理。

这条链路里，**第 2、3 步是"确定性计算"**——同样的输入（MappingModule + 配置）必然产出同样的元数据，
因此**可以在构建期（JVM 上）完整执行一遍并固化成产物**，这正是 Quarkus "build time processing" 的思路。

---

## 1.5 Bean 元数据静态化（beanmeta.idx）

BeanInfo（`org.beangle.commons.lang.reflect.BeanInfo`：属性、`TypeInfo`、getter/setter 签名、方法）是
Spring/CDI 集成、ORM 元数据构建（`Mappings.autobind`）以及 native 下"注册一次、全量复用"的公共基础。
构建期把它固化为**二进制索引** `META-INF/beangle/beanmeta.idx`（commons 的 `MetaIndex`/`MetaCodec` 格式），
一个 idx 可容纳多个类的 BeanMeta，并带类名→偏移目录：

- **生成**：`MetaPlugin`（sbt 插件，自动启用）读取 `beangle.xml` 声明的 `MetaRegistrar`
  （`MappingModule`/`BindModule` 等，`<jpa>/<orm><mapping>` 与 `<cdi><module>`），生成类名清单后 fork
  `MetaGenerator`（commons）：实例化各 registrar 触发 `registering()`，收集其 `bind[T]` 宏在**编译期 dig**
  出的 `BeanMeta`（精确类型，`Long`/`Int` 而非 `Object`），写入 `resourceManaged` 的 idx 并随 jar 打包。
  运行期只需注册一个资源 `META-INF/beangle/beanmeta.idx` 即可全量加载。
- **读取**：`MetaModels` 启动时惰性加载 `classpath*:META-INF/beangle/beanmeta.idx` 建缓存；
  `BeanInfos.get(clazz)` 先查缓存/MetaModels，未命中时对"父类 `$` 子类"（如懒加载代理
  `<Entity>$HibernateProxy`）复用父类 BeanMeta（`BeanInfos.parentOf`，native 下依赖代理类
  `allPublicMethods` 注册），最后才回退运行时反射（`MetaLoader`）。
- **MappingModule 集成**：`MappingModule.bind` 宏的运行期分支走 `BeanInfos.get`（精确类型来自 idx），
  构建期分支（`buildTime`）才用编译期 dig 的结果并 `addMetas` 收集进 idx —— 对应用透明
  （JVM 与 native 行为一致）。

> ⚠️ **精度约束（重要）**：若运行期拿不到 idx（如测试 jar 未打包进去）而回退反射，
> 会丢失 Scala 泛型/继承的精度——JVM 签名把 `NumId[Long].id` 擦除为 `java.lang.Object`，
> 导致 `Mappings.bindId` 报 `Cannot find sqltype for java.lang.Object`。
> 因此**用于 ORM 的描述必须由编译期 digger 生成**：构建期 `MetaGenerator` 从 registrar 的
> `bind[T]` 宏收集的正是编译期 dig 结果；测试 scope 由 `Test / metaIndex` 在 `Test / compile` 后生成
> 测试实体 idx，保证测试运行期同样拿到精确类型。

---

## 2. native-image 的核心约束（为什么不能直接跑）

GraalVM native-image 是"封闭世界（closed world）"分析：

| 约束 | 影响 |
|---|---|
| 运行时不能生成/定义新类 | ByteBuddy（Hibernate 懒加载代理、Spring CGLIB）全部失效（beangle-data 库侧已无 ByteBuddy） |
| 反射默认不可用 | `Class.forName`、`getMethod/invoke`、`newInstance` 必须预先注册（reflect-config） |
| 资源默认不打包 | classpath 资源（beangle.xml、META-INF/services、.sql、message bundle）需注册（resource-config） |
| JDK 动态代理需声明 | Spring AOP 代理、Hibernate 部分接口代理需注册（proxy-config） |
| Java 序列化需声明 | 实体/值类型若被序列化需注册（serialization-config） |
| ServiceLoader 需可见 | Hibernate 的 `MetadataBuilderFactory`/Dialect/JCache Provider 等 SPI 文件需打入镜像 |
| 类初始化时机需规划 | `--initialize-at-build-time` / `--initialize-at-run-time` 选择 |

---

## 3. 逐项审计：本库的 native-image 阻塞点

### 3.1 运行期字节码生成（致命，必须先解决）

| 位置 | 机制 | 说明 |
|---|---|---|
| Hibernate 懒加载代理 | Hibernate `BytecodeProviderImpl`（ByteBuddy） | 原生问题 HHH-16013：native 下默认禁用运行期代理生成，需构建期预生成代理类并注入 `BytecodeProvider`（Quarkus 的 `PreGeneratedProxies` 同款思路）。**beangle-data 库侧已无 ByteBuddy**（OQL 与 `declare` 均为 scala.Dynamic，见 [dynamic-oql.md](dynamic-oql.md)） |
| Spring `TransactionalProxy`/AOP | `ProxyFactory` JDK/CGLIB 代理 | 使用方服务接口需注册 proxy-config；或 native 模式改用非代理事务方案 |

### 3.2 运行期反射（需要构建期注册）

> 注：OQL 与 `declare` 路径经 scala.Dynamic 改造后零反射（`Prop`/`DeclareProp` 为普通对象、路径字符串累积），
> 原先每查询的 `AccessTracker.of`（`getConstructor(Context).newInstance`）与 `$Tracker` 反射注册均已不存在；
> 下表剩余反射点主要服务于绑定期（`Mappings.autobind`）、Hibernate 运行期与工具类。

| 位置 | 反射内容 | 注册对象 |
|---|---|---|
| `Mappings.autobind` | `Reflections.newInstance`（实体/组件实例化采样）、`BeanInfos.get`（属性元信息） | 所有实体/组件类（构造器、字段、方法） |
| `ScalaPropertyAccessor` | `Method.invoke`（getter/setter） | 所有实体/组件类的 declared/public 方法 |
| `ValueType`（hibernate/.../udt） | `getDeclaredFields`、`getConstructor`、`setAccessible` | 所有 `@value` 值类型类 |
| `BindMetadataBuilderFactory` | `Class.forName(enumTypeName)`、类型注册 | 所有枚举类 |
| `Profiles` | `Reflections.getInstance[MappingModule]`、命名策略构造器反射 | MappingModule 类、自定义 NamingPolicy |
| `DomainFactory` | 仅收集 MappingService 实体类型，无反射 | — |
| `ConvertPopulator`/meta `Type`/Domain | `Reflections.newInstance` | 实体类（应用运行期使用） |
| `JsonAPI` | `getter.invoke` 序列化 | 实体类方法 |
| `Jpas` | `clazz.getAnnotation(Entity/Embeddable)`（`findEntityName`/`isEntity`/`isComponent`，`OqlBuilder.from` 运行期调用） | `jakarta.persistence.Entity`/`Embeddable`（已由 `ModelAotHints` 注册，model 模块） |
| Hibernate 自身 | Dialect、JCache、类型等（`Class.forName` 按名加载） | 见 4.3（走 fork 元数据） |

### 3.3 资源与 SPI

- `beangle.xml`（`classpath*:`）——使用方资源，需注册；
- `META-INF/services/org.hibernate.boot.spi.MetadataBuilderFactory`（本库）、Hibernate 自身的 SPI 文件、JDBC 驱动
  `META-INF/services/java.sql.Driver`、JCache `CachingProvider` SPI —— 需注册；
- `META-INF/beangle/ddl/{oracle,postgresql}/*.sql`（DdlGenerator）——需注册；
- `org/beangle/data/model/package.zh_CN` 等 message bundle（`Messages`）——需注册；
- 日志（logback）——反射注册已由 commons 统一提供（`LogbackAotHints`，随 beangle-commons.jar
  内嵌 `META-INF/native-image/beangle` 自动合并），应用无需手写。覆盖三类：
  - Joran 按 `class=` 属性反射实例化的 appender/encoder/layout（`ConsoleAppender`、
    `PatternLayoutEncoder`、`LayoutWrappingEncoder`）与 `DefaultJoranConfigurator`/`BasicConfigurator`
    （`AotHints.registerType` 递归注册父类/接口，native 冒烟已验证）；
  - jboss-logging 探测日志后端用的 `ch.qos.logback.classic.Logger`（`Class.forName` 探测）；
  - **`org.slf4j.spi.LocationAwareLogger`**——jboss-logging 选定 SLF4J 后调用
    `LocationAwareLogger.class.getDeclaredMethods()` 反射查找 `log` 方法，native 下漏注册会抛
    `NoSuchMethodError`，被 jboss-logging 静默捕获后回退 JUL（`JDKLoggerProvider`）。
    **这是 Hibernate 日志在 native 下"意外走了 java.util.logging"的最常见根因**；
    注册该接口后 Hibernate 日志正确走 logback（格式/级别/输出均由 logback.xml 控制）。

### 3.4 类初始化（build-time vs run-time）

- 冒烟实测（P3.3）：**除两个 UUID Holder 外不需要任何 `--initialize-at-*` 参数**。svm-subs
  （Scala 标准库运行期初始化）与整包/整库 build-time 初始化互相冲突（报
  "Classes that should be initialized at run time got initialized..."），因此不采用
  `--initialize-at-build-time=org.hibernate/org.beangle` 之类的整包参数；仅 fork 内嵌的
  `--initialize-at-run-time=org.hibernate.id.uuid.UuidVersion6Strategy$Holder,org.hibernate.id.uuid.UuidVersion7Strategy$Holder`
  （SecureRandom）生效；
- JDBC 驱动（H2）与 JCache（caffeine）无需 run-time 初始化参数，注册 reflect/resource 即可工作。

---

## 4. Quarkus 是怎么做的（对照）

Quarkus 的 `quarkus-hibernate-orm` 扩展在**构建期**（JVM 上，属于 Maven/Gradle 插件阶段）完成：

1. **构建期建模**：Jandex 扫描实体注解 → 在构建 JVM 上跑 Hibernate 元数据构建；
2. **元数据序列化**：把构建好的 `Metadata`（`PersistentClass` 图）序列化保存，运行期直接反序列化加载（FastBoot），
   跳过反射式重扫描；
3. **代理预生成**：构建期用 ByteBuddy（构建 JVM 上可用）生成所有懒加载代理类，注册为 `PreGeneratedProxies`，
   运行期 `hibernate.bytecode.provider` 走预生成实现；
4. **增强（enhancement）**：构建期对实体做 dirty-checking/懒属性增强（对应 Hibernate 的 enhancement 插件）；
5. **可达性元数据**：扩展的 build steps 为每个实体/资源生成 GraalVM 的 reflect/resource/proxy/serialization 配置；
6. **hibernate-core 自身的元数据**：来自 `graalvm-reachability-metadata` 仓库（`org.hibernate.orm:hibernate-core` 条目），
   或内嵌在 jar 的 `META-INF/native-image/` 里。

**对本库的映射：**

| Quarkus 做的事 | 本库的对应物 |
|---|---|
| 构建期 Jandex 扫描实体 | `Mappings.autobind()`（更简单：实体集合来自 MappingModule，无需扫描） |
| 序列化 Metadata 运行期加载 | **不采用**：快照必然冻结方言（建模自始依赖 engine，见 P2.3）；已落地的中性层是 `beanmeta.idx`（BeanInfo/属性类型静态化，见 §1.5） |
| 预生成代理 + BytecodeProvider | 构建期预生成 Hibernate 代理类 + `BytecodeProvider` 原生实现（P2） |
| 构建期增强 | 非阻塞、列为 P4：beangle dirty-checking 走快照比较，未增强也可正确工作（详见 §5.1 P2.2） |
| 生成 reflect/proxy/resource/serialization 配置 | **`AotHintRegistrar` 子类 + `AotPlugin`（beangle AOT 机制，P1 交付）** |
| hibernate-core 可达性元数据 | 在 `beangle-hibernate-core` fork 的 jar 内嵌 `META-INF/native-image/`（P2） |

---

## 4.5 演进：scala.Dynamic 替代逐类 $Tracker（OQL 路径已采用）

曾评估用 `scala.Dynamic`（`selectDynamic`）用一个通用类替代逐实体 `$Tracker` 子类生成。结论：

- **运行期机制可行**：DSL 参数静态类型为 Dynamic 子类时，`e.name.first` 被编译器改写为
  `e.selectDynamic("name").selectDynamic("first")`，一个通用类即可记录任意属性路径，零类生成。
  注意 Scala 3 的 `scala.Dynamic` 是空标记 trait，`selectDynamic` 必须定义在具体类上。
- **运行效率**（微基准，1M 次/轮，7 轮取最优，模拟一条 `where` 条件的路径记录）：
  - 现状（ByteBuddy $Tracker）：~207-220 ns/op
  - Dynamic（selectDynamic 链）：~38-40 ns/op（约 5.4x 更快，且无共享可变状态）
  - 但查询构造相对 DB 往返（毫秒级）可忽略，绝对收益不显著。
- **代价**：静态类型检查丢失（`e.name` 拼写错误编译期不可见）。
  补偿方案：① 编译期宏校验（inline 宏提取 lambda 中 selectDynamic 字面量并对照实体成员校验）——
  实测发现 Scala 3.3 会把传给 inline 宏的非捕获 lambda 提升为静态方法引用（`Ident("f$proxyN")`），
  宏拿不到 lambda AST，需解析 `DefDef.rhs` 绕过，实现复杂度高；② 运行期路径校验——绑定/查询构造期报错。
- **决策更新（2026-08）**：`OqlBuilder.where/on` 改用 `Prop`、`MappingModule.declare` 改用 `DeclareProp`
  （均基于 scala.Dynamic），**`AccessTracker`/ByteBuddy 已全部删除**，运行期零类生成、零反射，
  native 下无需任何 tracker 预生成。宏校验与运行期校验均不采用（接受路径书写错误的运行期暴露）。
- **额外收益**：Dynamic 的 `applyDynamic` 使 `where { u => u.lower(u.name).equal("x") }`、
  `u.count(u.roles).gt(0)` 等数据库函数/聚合可直接书写（类型化 tracker 无法表达）。
- **注意**：`select/groupBy/orderBy` 在 `AbstractQueryBuilder` 层改为 Any* 渲染（Prop/Var/String 混用），
  单字符串调用不受影响。

---
## 5. native-image 路线图（基于当前 scala.Dynamic 状态）

### 5.0 现状（已完成，作为路线图基线）

- **库侧全面 scala.Dynamic 化**：`OqlBuilder` 用 `Prop`、`MappingModule.declare` 用 `DeclareProp`，
  运行期零类生成、零反射；`AccessTracker`/ByteBuddy 已删除，`byte_buddy` 依赖移除；
- **beangle AOT 机制（P1 交付，P2.1 收口）**：库侧 model 的 `ModelAotHints` + hibernate 的
  `BeangleAotHints` + `AotPlugin`（自动启用）内嵌库清单；hibernate-core 反射元数据内嵌 fork jar
  （P2.1），`HibernateAotHints` 已删除，不再依赖 hibernate-graalvm；
  应用侧定义 `AotHintRegistrar`/`MetaRegistrar` 子类并放置锚定文件，`AotPlugin` 自动生成
  `reflect-config.json` / `resource-config.json` / `proxy-config.json` / `serialization-config.json`；
- 文档：本文件 + [dynamic-oql.md](dynamic-oql.md)。

> 库侧已无运行期动态行为，剩余阻塞全部在 **Hibernate 侧**（fork + 懒加载代理）与应用集成验证。

### 5.1 P2：Hibernate 侧（前置：无；涉及仓库：beangle/hibernate fork）

**P2.1 fork 可达性元数据 ✅（已落地并冒烟验证）**
- 内容：`beangle-hibernate-core` fork jar 内嵌 `META-INF/native-image/org.hibernate.orm/hibernate-core/`：
  - `reflect-config.json`——63 项 hibernate-core 反射注册（native-image-agent 证据审计：
    有轨迹证据的类保留并按证据收紧范围，无证据的删除；方法详见
    [native-image-reflection-audit.md](native-image-reflection-audit.md)）；
  - `native-image.properties`——`--initialize-at-run-time=...UuidVersion6/7Strategy$Holder`（SecureRandom）；
- 关键实测：`org.hibernate.event.spi.EventType` 的 `static{}` 用 `getDeclaredFields` + `Field.get`
  反射构建 `STANDARD_TYPE_BY_NAME_MAP`（`values()` 的数据源），**必须注册 `EventType` 声明字段**，
  否则 native 下 map 为空 → `EventListenerRegistryImpl.getEventListenerGroup` 抛
  "Unable to find listeners for type [auto-flush/post-insert]"（2LC 开启与否只是暴露顺序不同）；
- 依据：上游 `graalvm-reachability-metadata` 仓库 `org.hibernate.orm:hibernate-core` 条目 + 冒烟实测；
- 验收：✅ native 构建/运行不再因 hibernate 反射/资源报错（含 2LC/JCache 路径）。

**P2.2 Hibernate 懒加载代理（HHH-16013）——主路线：构建期一律预生成 + fork 内无条件预生成 BytecodeProvider**

> 结论先行：**proxy（懒加载代理）是唯一硬阻塞，必须做**；**enhancement（dirty-checking/懒属性增强）非阻塞
> （beangle 的 dirty-checking 走快照比较，见 §5.1 末注），列为 P4 可选**。
> **主路线（简化）：不区分 JVM/native，构建期一律预生成代理，运行期自定义 `BytecodeProvider` 无条件使用预生成类**
> ——这正是 Quarkus 的模型（`RuntimeBytecodeProvider` 是唯一运行期实现，JVM 与 native 同路径，没有模式检测/委托分支）。
> 收益：bytebuddy 可彻底退出运行期 classpath（JVM + native）；本库 JVM 测试与 native 走同一代码路径，回归即覆盖。
> 运行期 `BytecodeProvider` 必须落在 fork 里：Hibernate 7.4 的 `BytecodeProviderInitiator` 纯 ServiceLoader，
> 且发现多个注册直接抛 `IllegalStateException`——sample 工程原有的 `patchHibernateJar` 后门正是为此而设，本方案可将其删除。
>
> Quarkus 机制（已核实源码）：构建期 `ProxyBuildingHelper` 用 hibernate 自带 `ByteBuddyProxyHelper.buildUnloadedProxy`
> 为每个可代理实体生成代理字节码并作为应用类打入产物，映射（实体类名→代理类名）存入 `PreGeneratedProxies`；
> 运行期自定义 `BytecodeProvider`（`RuntimeBytecodeProvider`）→ `getProxyFactoryFactory` 返回
> `QuarkusRuntimeProxyFactoryFactory` → 每个实体一个 `QuarkusProxyFactory`：`postInstantiate` 时按映射查出预生成类
> 与默认构造器，`getProxy` 时 `new ByteBuddyInterceptor(...)` + `constructor.newInstance()` + `$$_hibernate_set_interceptor`；
> `getReflectionOptimizer`/`getEnhancer` 返回 null（运行期零字节码生成）。

**P2.2a fork 侧：`PrebuiltProxyProvider`（无条件预生成模式，beangle/hibernate）**
- 新增 `org.beangle.data.hibernate.proxy.PrebuiltProxyProvider implements BytecodeProvider`：
  - `getProxyFactoryFactory` **恒返回** `BeangleProxyFactoryFactory`：
    - 懒加载映射（实体→代理类名）→ 每个实体 `Class.forName` 查出预生成类 + `BeangleInterceptor` +
      默认构造器（复刻 QuarkusProxyFactory ~120 行；`buildBasicProxyFactory` 返回 null，集合代理走
      PersistentCollection 自带类）；
    - 按实体查不到（构建期跳过 final/无默认构造器，或应用未跑构建插件）→ 抛 `HibernateException`（Quarkus 同款，
      Hibernate 捕获后 warning 并为该实体退回 eager），**不依赖映射文件存在与否做分支**；
  - `getReflectionOptimizer`（两个重载）恒返回 null → `hibernate.bytecode.use_reflection_optimizer` 属性不再生效；
  - `getEnhancer` 恒返回 null（运行期零字节码生成/增强）；
  - **不引用任何 net.bytebuddy 类**（`BeangleInterceptor` 是本库在 `org.beangle.data.hibernate.proxy` 包的
    Scala 实现，fork 已剔除全部 bytebuddy 类）→ bytebuddy 可彻底退出运行期 classpath；
  - fork 的 `META-INF/services/org.hibernate.bytecode.spi.BytecodeProvider` 改为指向它（保持唯一注册）。

**P2.2b data/build 侧：`ProxyPlugin`（sbt 构建期一律预生成）**
- 新 AutoPlugin（同 `AotPlugin`/`MetaPlugin` 模式：锚定 `beangle.xml`、fork `java -cp` 跑生成器、无锚定静默跳过）：
  - 契约：`beangle.xml` 中 `<jpa>/<orm><mapping class="...">` 元素（`GeneratorSupport.extractMappingClasses`，
    与 `metaIndex` 同源声明）；**有 mapping 即生成，JVM 与 native 一致**；
  - 实体集合：直接实例化 mapping 声明的 `MappingModule` 子类、`registering()` 后取 `entityTypes`
    （`MappingModule.entityTypes` 构建期接口，不依赖 beanmeta.idx/编译器挖掘）；
  - 生成器 main `org.beangle.data.hibernate.proxy.BeangleProxyGenerator`（随 beangle-data-hibernate 发布）在
    构建 JVM 上对每个可代理实体（跳过 interface/abstract/final/无公开无参构造器）
    复刻 `ByteBuddyProxyHelper` 的代理结构（原生 net.bytebuddy，fork 已剔除 hibernate 实现）产出字节码
    → 全部写入 `Compile / resourceManaged`
    （`.class` 作为资源随 jar 打包，构建/运行期 classpath 均可按名加载，JVM 与 native 同一路径）：
    - **`META-INF/native-image/beangle/data/reachability-metadata.json`**（GraalVM 25+ 统一格式，
      顶层 `{"reflection": [...]}`，代理类注册：
      类名按约定固定为 `<Entity>$HibernateProxy`，注册无参构造器、`writeReplace` 与
      `allPublicMethods`（供 `BeanInfo.from` 的 `getMethods` 查询），不开放字段；生成器按约定直接
      输出、不回读文件系统，native-image 自动发现，无需 aotHints 合并）；
      *迁移前产物为同布局的 `reflect-config.json`；生成器工作树已改输出本新格式，
      集中式触发改造见文首注与 beangle/build `docs/graalvm-reachability-metadata.md`*；
  - 锚定与门控：仅当 `beangle.xml` 有 mapping 且 classpath 含 beangle-data-hibernate 才生成；
    声明类未找到（编译进行中）退出码 2，`GeneratorSupport.retryGenerator` 退避重试（与 metaIndex/aotHints 同机制）；
  - bytebuddy **仅构建期需要**：插件自带 `net.bytebuddy:byte-buddy` 依赖并追加进生成器 classpath，
    应用运行期（JVM + native）都可排除；
  - 类名契约：代理类名固定为 `<Entity>$HibernateProxy`（fork 的 `PrebuiltProxyProvider` 与生成器共用
    Suffixing 命名策略，两参构造无随机后缀），metadata 按该约定输出、不回读文件系统，跨构建稳定。

**P2.2c sample 工程集成 ✅（后门清理 + 懒加载 native 用例）**
- ✅ 已删除 `patchHibernateJar` 任务、bytebuddy exclusion 与 `build-native.sh` 的 patch 步骤；
- ✅ 已清理参数冲突：`use_reflection_optimizer` 相关参数移除（provider 恒返回 null 后不再生效），
  `--initialize-at-*` 整包参数全部删除（实测结论见 §3.4）；
- ✅ 懒加载 native 用例已落地：`NativeApp` 扩充用例第 10 段（`LAZY PROXY + COLLECTION`）保存
  `Employee.department`（多对一，Hibernate 默认 lazy proxy）后重新 `get`，在**新实体上访问
  关联并打印** `dept via lazy proxy`/`roles`/`tags`，native 二进制下由 `PrebuiltProxyProvider`
  按名加载构建期预生成代理并触发初始化，构建 + 运行验证通过；
  JVM 侧同一路径由 `LazyProxyTest` 覆盖。

**P2.2d 验收**
- JVM 回归：`hibernate` 模块 24 测试全绿——测试资源自带 `beangle.xml`，**测试本身就走预生成代理路径**
  （与 native 同一条代码路径，回归即覆盖）；
- 边界验证：无 `beangle.xml` 的项目不生成（空映射，无实体即无代理请求）；final/case class 实体构建期跳过、
  运行期 warning 退回 eager（Quarkus 同款行为）；
- native 冒烟：`Department.parent` 懒加载在 native 二进制下返回真实对象、无 `HibernateException`。

**P4（本阶段不做）enhancement 路线（借鉴 Quarkus `HibernateEntityEnhancer`）**
- Quarkus 做法：构建期字节码变换（ASM 桥接 Hibernate `Enhancer` + `QuarkusEnhancementContext`），
  **先增强后生成代理**（共享 TypePool，代理覆盖增强后的 getter）；
- beangle 判定：dirty-checking 走 `persister.findDirty(getValues, loadedState)` 快照比较（`HibernateEntityDao`），
  未增强实体功能正确；enhancement 仅带来性能收益与懒属性支持（beangle 无懒属性需求）；
- 若做：先在 JVM 用 `hibernate-enhance-maven-plugin` 对 Scala 样例验证（私有字段 + accessor、`Option` 泛型擦除后的
  字段类型是主要兼容风险），再在 ProxyPlugin 里加 transformer 阶段；不建议与 P2.2 并行。

**P2.3 Metadata 快照（FastBoot）——与多方言目标结构性互斥，默认不投入**
- 任务（原设想）：构建期序列化 `Mappings`/Hibernate `Metadata`，运行期反序列化加载，
  跳过 `BindSourceProcessor`/`Mappings.autobind` 的反射路径；
- **结论：不做。** 建模从第一行就依赖方言，快照没有"方言无关版"：
  - `Column.sqlType` 是引擎已解析的具名 `SqlType`（`engine.toType(code, precision, scale)`，
    beangle-jdbc `Relation.scala`/`TypeNames`）——H2 得 `varchar`、Oracle 得 `varchar2`；
  - 属性类 → 类型映射走 `SqlTypeMapping = new DefaultSqlTypeMapping(database.engine)`
    （`Mappings.scala`）；标识符命名/引用规则走 `engine.toIdentifier`（建列、建索引）。
  - 因此序列化 `OrmEntityType`/`Table`/`Column` 必然选定某方言；反序列化后切方言，
    sqlType 名不合法（如 H2 上的 `varchar2`），仍需按新方言重渲染；
- 中性层边界：只有 `beanmeta.idx`（BeanInfo/属性类型，与方言无关）已落地（§1.5）；
  列定义层若要中性化，需重设计中间表示（列存 `java.sql.Types code + length/precision/scale`，
  运行期按实际 engine 重渲染），且标识符规则也要中性化——工作量接近重写建模层；
- 决策条件（满足其一才重新评估）：
  - native 启动 profile 实测"运行期绑定/建模"成为启动或运行瓶颈（预计不是：绑定是纯数据结构计算，
    反射/BeanInfo 层已静态化）；
  - 出现明确单方言应用，可接受"换库 = 重新构建"（Quarkus 模型）；
- 验收（若做）：native 启动时间对比有可量化收益，且不破坏多方言（或明确接受单方言）。

### 5.2 P3：样例应用与验证

**P3.1 sample 示例工程（独立仓库 beangle/sample，由 data 的 `samples/native` 迁出）**
- MappingModule + H2 + `OqlBuilder`/`declare` + GraalVM 构建脚本（或 Makefile/CI 片段）；

**P3.2 native-image 冒烟测试 ✅**
- `MinimalTest` 与 `NativeApp`（`Mappings.autobind()` → 建库建表 → OQL 查询 → 增删改 → 2LC/JCache）
  均完成 native 构建（`sample/build-native.sh`）与运行；
- 纳入 CI：待办（本地已可重复执行）。

**P3.3 按实测补齐配置 ✅（本轮冒烟）**
- `build-native.sh`/`native-image-args.txt`/`build.sbt` 已统一：删除 `patchHibernateJar`、
  `use_reflection_optimizer`、byte-buddy exclusion 与全部 `--initialize-at-*` 整包参数；
- 资源 pattern 已收敛：`logback.xml`/`META-INF/beangle/beanmeta.idx` 由 commons
  （`LogbackAotHints`/`MetaAotHints`，随 beangle-commons.jar 内嵌注册），实体/组件/库注解由
  data-model（`ModelAotHints`）注册，`META-INF/services`、`META-INF/beangle/ddl`、`*.zh_CN`
  由 data-hibernate（`BeangleAotHints`）注册；
  sample 的 `resource-config.json` 仅保留 jdbc engine keywords、`beangle.xml`、caffeine 与
  `reference.conf`/`application.conf`（后两者 caffeine/TypeSafe Config 资源，无库侧归属）；
- sample 不再手写 `reflect-config.json`：应用面全部走声明式（`SampleAotHints` +
  `aot-registrars.txt`，实体由 `beangle.xml` 扫描自动注册，`SampleMapping` 本体由
  `AotHintGenerator` 自动注册），`sample/src/main/resources/native-image/reflect-config.json` 已删除；
- logback/slf4j 反射已收敛到 commons（`LogbackAotHints`，随 beangle-commons.jar 内嵌发布）；
- 库/fork 级（随 jar 内嵌）：hibernate-core 反射（P2.1）、`EventType` 声明字段（P2.1）。
- logback 实测注意点：
  - 启动日志出现 `logback 版本 ?` 是 jar MANIFEST 缺 `Implementation-Version`，功能无碍；
  - `hibernate.show_sql=true` 时 SQL 双行输出（`DEBUG org.hibernate.SQL` 来自 jboss-logging，
    另有 show_sql 直打的 `Hibernate: ...`），是 `SqlStatementLogger` 的正常双路径，不是重复日志；
  - `TRACE org.hibernate.type.descriptor.sql.BasicBinder` 的绑定参数输出只在语句真的带 `?`
    占位符时出现；sample 的无参查询没有该日志属正常。

### 5.3 可选优化（P4，非阻塞）

- `Mappings.autobind` 中 `Reflections.newInstance` 的"采样默认值"逻辑改为可关闭（native 用配置替代）；
- 组件属性元信息尽量走编译期 `BeanInfoDigger`（`MappingMacro.bind` 已对 `bind[T]` 这么做，扩展到组件）；
- `AotHintGenerator`/`AotPlugin` 与 fork 版本升级联动验证（版本升级时同步重跑）。

### 5.4 前置条件与风险

- **GraalVM + native-image 环境**：已安装（`/home/chaostone/local/graalvm-jdk-21`），P3 冒烟已执行并通过（MinimalTest + NativeApp）；
- **fork 元数据耦合**：P2.1 与 Hibernate 版本绑定，fork 升级需同步验证；
- **sbt 2 插件 publishLocal 竞态**：`sbt-beangle-build` 发布偶发 jar 类不全（曾仅 13/145 类），
  导致 data 构建报 `NoClassDefFoundError: CompileHookPlugin$autoImport$`；发布后需核对 jar 类数
  与 `classes` 目录一致，必要时重跑 `publishLocal`；
- **最大不确定项**：Hibernate 懒加载代理（P2.2）——机制已由 Quarkus 源码验证可行（构建期 ByteBuddy 预生成 +
  运行期按名加载；Quarkus 在 JVM 生产模式同样使用预生成代理，语义等价性已被证明），剩余风险集中在
  **Scala 实体与 ByteBuddy 代理生成的兼容性**（私有字段 + accessor 模式、`Option`/集合关联的 getter 覆盖），
  由 P2.2d 的 JVM 回归（同路径）+ P2.2c 懒加载用例在真实 native 构建中验证。
## 6. 使用方（应用）需要做什么

1. 构建期：应用定义 `AotHintRegistrar`/`MetaRegistrar` 子类（实体、方言、驱动等）并放置
   锚定文件（`aot-registrars.txt`/`beangle.xml`），`AotPlugin`/`MetaPlugin` 自动启用后产物
   （configs/beanmeta.idx）随编译自动生成并打进应用 jar（Hibernate 代理类预生成见 P2）；
2. native-image 参数：引用生成的配置文件、注册 JDBC 驱动与缓存 Provider、`--initialize-at-build-time` 清单；
3. 运行期：`beangle.xml` 与 DDL 资源在 resource-config 中；事务代理接口在 proxy-config 中。

---

## 7. 风险与取舍

- **性能**：预生成代理 + 反射注册比 JVM 模式略重，但 native 的启动/内存优势远超此开销；
- **Scala 生态**：Scala 3 标准库在 native-image 下有已知注意点（`scala.reflect`、Enum 反射），本项目宏（`MappingMacro`）
  在编译期生成 `BeanInfo`，已经避开了一部分 `TypeTag` 运行期问题，是加分项；
- **维护成本**：新增的构建期工具与 Hibernate 版本耦合，需随 fork 升级同步验证；
- **不回退风险**：所有 P0 改造保持 JVM 行为不变（预生成类优先、缺省回退 ByteBuddy），现有测试必须继续通过。

---

## 8. 相关资源

- Hibernate ORM 原生问题：HHH-16013（native 下运行期禁止生成 HibernateProxy）
- GraalVM reachability metadata 仓库：`oracle/graalvm-reachability-metadata`（hibernate-core 条目）
- Quarkus 实现参考：`io.quarkus.hibernate.orm.runtime.proxies.PreGeneratedProxies`、
  `PersistenceUnitsHolder`（序列化 Metadata 加载）
- Hibernate 官方：Hibernate 6 起依赖构建期 enhancement（`hibernate-enhance-maven-plugin`）而非运行期字节码
