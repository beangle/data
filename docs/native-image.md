# beangle/data 与 GraalVM native-image 可行性分析

> 目标：让使用 `beangle-data`（`beangle-data-model` + `beangle-data-hibernate`）的应用能够在本地编译，并最终以
> GraalVM native-image 方式构建、运行。
>
> 本文回答两个问题：
> 1. 本项目（一个**库**，且 ORM 绑定是**代码声明式**而非注解）相比 Quarkus 这类框架，native-image 化会不会被"加大阻碍"？
> 2. 需要做哪些必要的更改？

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
  所以每个使用方都要跑一次我们提供的生成器、并把产物打包进应用。这是一次性成本，可以文档化 + 样例工程化来降低。
- **好消息：** 本项目使用**自研 Hibernate fork**（`org.beangle.hibernate:beangle-hibernate-core`），
  可以直接在 fork 的 jar 里内嵌 `META-INF/native-image/` 元数据，把 hibernate-core 自身的反射/资源注册问题一次解决。

---

## 0.1 当前进展（已实现并验证）

> 本仓库 `develop` 分支已落地 P0 与 P1 的主体改造，JVM 行为不变、全部测试通过：

| 项 | 状态 | 说明 |
|---|---|---|
| 全面 scala.Dynamic 化（P0 增量） | ✅ | `OqlBuilder` 用 `Prop`、`declare` 用 `DeclareProp`（`model/.../orm/DeclareProp.scala`），运行期零类生成、零反射；**`AccessTracker`/`ByteBuddyHelper`/`AccessTrackerGenerator` 及全部 tracker 预生成链路已删除**（`byte_buddy` 依赖移除） |
| `NativeImageConfigGen`（P1） | ✅ | 生成 reflect/resource/proxy/serialization 配置 + 推荐 native-image 参数（不再生成 tracker 类/注册 `$Tracker` 反射） |
| 回归测试 | ✅ | `model` 34、`hibernate` 22 全部通过（`testOnly *` 强制全量运行） |

### 两个构建期工具的使用方法（已接入 sbt 任务）

```bash
sbt 'nativeImageConfig --output target/native-image --engine PostgreSQL \
  --dialect org.hibernate.dialect.PostgreSQLDialect \
  --cache-provider com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider \
  --jdbc-driver org.postgresql.Driver'
```

（`--config` 默认 `classpath*:beangle.xml`，应用可自行覆盖。）

产物：`reflect-config.json` / `resource-config.json` / `proxy-config.json` / `serialization-config.json` /
`native-image-args.txt` / `classes.txt`。生成目录需加入应用 classpath（或打进应用 jar）。

**尚未实现（P2/P3）：** Hibernate 懒加载代理的构建期预生成与 `BytecodeProvider` 原生实现、
`beangle-hibernate-core` fork 的 `META-INF/native-image/` 元数据、Metadata 快照（FastBoot 可选优化）、
samples/native 示例工程与 CI 冒烟。

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
| `DomainFactory` | `Reflections.getField(pf.getClass,"proxyClass")` | Hibernate 代理工厂类（依赖 3.1 预生成） |
| `ConvertPopulator`/meta `Type`/Domain | `Reflections.newInstance` | 实体类（应用运行期使用） |
| `JsonAPI` | `getter.invoke` 序列化 | 实体类方法 |
| `Jpas` | `Class.forName("jakarta.persistence.*")` | 注解类 |
| Hibernate 自身 | Dialect、JCache、类型等（`Class.forName` 按名加载） | 见 4.3（走 fork 元数据） |

### 3.3 资源与 SPI

- `beangle.xml`（`classpath*:`）——使用方资源，需注册；
- `META-INF/services/org.hibernate.boot.spi.MetadataBuilderFactory`（本库）、Hibernate 自身的 SPI 文件、JDBC 驱动
  `META-INF/services/java.sql.Driver`、JCache `CachingProvider` SPI —— 需注册；
- `META-INF/beangle/ddl/{oracle,postgresql}/*.sql`（DdlGenerator）——需注册；
- `org/beangle/data/model/package.zh_CN` 等 message bundle（`Messages`）——需注册；
- 日志（logback）配置与类——需注册。

### 3.4 类初始化（build-time vs run-time）

- Hibernate 的静态注册表、Scala 的 `Enum`/反射缓存等需要 `--initialize-at-build-time`（beangle-data 库侧已无 ByteBuddy）；
- `org.hibernate` 大部分可 build-time 初始化（Quarkus 已证明）；JDBC 驱动、JCache 通常 run-time 初始化即可。

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
| 序列化 Metadata 运行期加载 | 可把 `Mappings`/Hibernate `Metadata` 序列化为快照（P2 可选优化） |
| 预生成代理 + BytecodeProvider | 构建期预生成 Hibernate 代理类 + `BytecodeProvider` 原生实现（P2） |
| 构建期增强 | 可选：构建期运行 Hibernate enhancement（P2/P3） |
| 生成 reflect/proxy/resource/serialization 配置 | **`NativeImageConfigGen`（本计划 P1 交付）** |
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
- **`NativeImageConfigGen`（P1 交付）**：构建期枚举实体/组件/值类型/枚举等，生成
  `reflect-config.json` / `resource-config.json` / `proxy-config.json` / `serialization-config.json`
  / `native-image-args.txt`；已接入 sbt 任务 `nativeImageConfig`；
- 文档：本文件 + [dynamic-oql.md](dynamic-oql.md)。

> 库侧已无运行期动态行为，剩余阻塞全部在 **Hibernate 侧**（fork + 懒加载代理）与应用集成验证。

### 5.1 P2：Hibernate 侧（前置：无；涉及仓库：beangle/hibernate fork）

**P2.1 fork 可达性元数据**
- 任务：在 `beangle-hibernate-core` jar 内嵌 `META-INF/native-image/org/beangle/hibernate/
  beangle-hibernate-core/*.json`（reflect/resource/proxy/serialization），解决 hibernate-core
  自身的反射（Dialect/JCache/类型注册）、资源（`META-INF/services` 等）与 ServiceLoader 注册；
- 依据：上游 `graalvm-reachability-metadata` 仓库 `org.hibernate.orm:hibernate-core` 条目，按 fork 版本适配；
- 验收：native 构建时 Hibernate 初始化不再因缺反射/资源报错。

**P2.2 Hibernate 懒加载代理（HHH-16013）**
- 任务：
  1. 构建期（构建 JVM）用 ByteBuddy 为实体生成懒加载代理类，随应用打包；
  2. 提供 native 模式的 `BytecodeProvider`/代理工厂实现（运行期按名加载预生成类），并注册 `META-INF/services`；
  3. `hibernate.bytecode.use_reflection_optimizer=false`、增强相关设置；
- 参考：Quarkus `PreGeneratedProxies`；Spring Boot 3 native 同款思路；
- 验收：懒加载实体/集合在 native 二进制下正常工作。

**P2.3（可选）Metadata 快照（FastBoot）**
- 任务：构建期序列化 `Mappings`/Hibernate `Metadata`，运行期反序列化加载，
  跳过 `BindSourceProcessor`/`Mappings.autobind` 的反射路径；
- 收益：启动更快、绑定期反射面进一步收窄；
- 验收：native 启动时间对比有可量化收益。

### 5.2 P3：样例应用与验证

**P3.1 samples/native 示例工程**
- MappingModule + H2 + `OqlBuilder`/`declare` + GraalVM 构建脚本（或 Makefile/CI 片段）；

**P3.2 native-image 冒烟测试**
- 最小应用 native 构建并跑通：`Mappings.autobind()`（绑定）→ 建库建表 → `declare` 声明 → `OqlBuilder` 查询；
- 纳入 CI。

**P3.3 按实测补齐配置**
- 真实 GraalVM 环境跑一轮，按报错补齐：类初始化清单（`--initialize-at-build-time`/`-run-time`）、
  JDBC 驱动、缓存 Provider、URL 协议、反射遗漏项等。

### 5.3 可选优化（P4，非阻塞）

- `Mappings.autobind` 中 `Reflections.newInstance` 的"采样默认值"逻辑改为可关闭（native 用配置/快照替代）；
- 组件属性元信息尽量走编译期 `BeanInfoDigger`（`MappingMacro.bind` 已对 `bind[T]` 这么做，扩展到组件）；
- 配置生成器与 fork 版本升级联动验证（版本升级时同步重跑）。

### 5.4 前置条件与风险

- **GraalVM + native-image 环境**：当前开发环境未安装，P3 需要真实环境执行与迭代；
- **fork 元数据耦合**：P2.1 与 Hibernate 版本绑定，fork 升级需同步验证；
- **最大不确定项**：Hibernate 懒加载代理（P2.2）——需在真实 native 构建中验证代理预生成与
  `BytecodeProvider` 替换的可行性。
## 6. 使用方（应用）需要做什么

1. 构建期：运行 `nativeImageConfig`（P1 工具），产物（configs）打进应用 jar（Hibernate 代理类预生成见 P2）；
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