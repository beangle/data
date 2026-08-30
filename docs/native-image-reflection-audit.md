# 反射注册面验证与收紧（native-image-agent 证据驱动）

> 记录 2026-08 对 beangle 生态（`beangle-hibernate-core` fork + `beangle-data` sample）
> 做的一次反射注册审计：用 GraalVM `native-image-agent` 在 JVM 上采集真实反射轨迹，
> 逐类确定“是否需要注册、注册到什么范围”，把宽口径的手写清单收紧为 Quarkus 式按需注册。
>
> 结论速览：fork `reflect-config.json` 268 → 335 项（63 项内部类轨迹证据 + 88 项
> `jakarta.persistence.*` + 186 项 `org.hibernate.annotations.*`）；sample 的手写清单
> **已整体删除**，应用面改为声明式（`SampleAotHints` + `beangle.xml` 扫描，见 §9）；
> 收紧后 native 构建 + 运行回归通过，二进制 ~130M → ~121M。

---

## 1. 背景与目标

早前的 hibernate-core 反射清单是“native 构建/运行失败 → 补注册”迭代攒出来的，
结果是 268 项几乎全部是 `allDeclaredConstructors + allDeclaredMethods + allDeclaredFields`
的宽口径。问题：

- 无法回答“每个类到底为什么需要注册、需要什么范围”；
- 大量条目可能已随代码演进失效（例如 BytecodeProvider 替换、事件监听器实现变更）；
- 宽口径会掩盖真实的反射依赖，也让镜像体积和审计成本上升。

目标：借鉴 Quarkus 的“按需精准注册”方法论——类集合与每类 flag 都以运行时证据为准，
而不是照搬 Quarkus 的类清单（Quarkus 基于 Hibernate 6，我们是 fork 的 7.4.6.Final，
类名与内部行为有差异；用自身 fork 的运行轨迹天然规避版本差异）。

---

## 2. 方法总览

三步：

1. **采集**：用 `native-image-agent` 在 JVM 上运行目标应用，记录运行期全部反射调用；
2. **对照**：按类解析轨迹，与现有清单交叉对照——有证据 → 按证据收紧范围；无证据 → 删除；
3. **回归**：重建 native 镜像并运行；失败则按错误信息把缺失的类补回（最小范围），重复直到通过。

---

## 3. 第一步：采集反射轨迹

`native-image-agent` 是 GraalVM 自带工具，以 `-agentlib:native-image-agent=...` 挂在 java 上即可，
会在 JVM 运行期拦截 `java.lang.reflect` 的全部调用并落盘。

```bash
# 1) 取 sample 的运行时 classpath（sbt 2 的虚拟路径展开同 build-native.sh 的做法）
sbt -batch "show Compile/fullClasspath" ... > /tmp/native-cp.txt   # 在独立 sample 工程（beangle/sample）内

# 2) 用 agent 跑一次完整冒烟（增删改查 + OQL + 二级缓存）
java -agentlib:native-image-agent=config-output-dir=/tmp/agent-config \
     -cp "$(paste -sd: /tmp/native-cp.txt)" \
     org.beangle.data.samples.nativeapp.NativeApp
```

产物（都在 `config-output-dir` 下）：

| 文件 | 内容 |
|---|---|
| `reflect-config.json` | 每个被反射访问的类的精确成员/批量标记（本次审计的核心） |
| `resource-config.json` | 运行期按名定位的资源 |
| `proxy-config.json` | 动态代理接口 |
| `serialization-config.json` | 序列化注册 |
| `jni-config.json` | JNI 访问的类/方法/字段 |
| `agent-extracted-predefined-classes/` | 运行期动态生成的类（本次为空，说明没有运行期字节码生成） |

---

## 4. 第二步：逐类证据分析

agent 的 `reflect-config.json` 本身就是 GraalVM 配置 schema，条目可直接作为“最小配置”。
把每个条目解析成“观察到的操作集合”，再映射到 GraalVM flag：

| agent 观察到的操作 | 含义 | 对应注册范围 |
|---|---|---|
| `methods` 中 `<init>` | 反射调用构造器实例化 | 该构造器（或 `allDeclaredConstructors`） |
| `methods` 中具名方法 | 反射调用方法（invoke） | 该方法（或 `allDeclaredMethods`） |
| `queriedMethods` | 只查询（`getMethod`/`getDeclaredMethod`，不调用） | `queryAll*Methods` |
| `fields` / `queriedFields` | 读写字段 / 查询字段 | `allDeclaredFields` / 具体字段 |
| `allDeclaredMethods: true` 等批量标记 | 代码调用了 `getDeclaredMethods()` 等 | 直接保留该标记 |
| 条目仅有 `name` | 只发生按名定位（如数组类、`Class.forName`） | 仅注册类名 |

交叉对照规则：

- 现有清单中的类 **在轨迹中有条目** → 保留，且范围改为 agent 记录的精确范围；
- 现有清单中的类 **在轨迹中无条目** → 删除（本次运行的证据表明它没有被反射使用）；
- 轨迹中有、但不在现有清单中的类 → **不新增**：native 回归已证明缺它们也能运行
  （多为只查询、代码可容忍空结果，或由其他机制覆盖）。

> 注意：删除动作只针对“失败驱动攒出来的手写清单”（fork 的 268 项、sample 的 122 项）。
> `ModelAotHints`/`BeangleAotHints`/`LogbackAotHints` 经 `AotPlugin` **生成**的库面配置
> （data-model 13 项、data-hibernate 40 项、commons 9 项）是库的**声明面**，为所有下游应用服务，
> 不能凭一个应用的轨迹删除，需逐类论证。

---

## 5. 第三步：native 回归验证

```bash
# 1) fork 配置内嵌在 jar 的 META-INF/native-image 中，native-image 构建时自动发现
cd /home/chaostone/workspace/beangle/hibernate && mvn -q -DskipTests install

# 2) 重建 native 镜像（sample 侧配置全部由 AotPlugin/ProxyPlugin 构建期生成，无手写清单）
cd /home/chaostone/workspace/beangle/sample && ./build-native.sh

# 3) 运行，判定 exit=0 且关键日志完整
target/native/sample-native
```

失败时的迭代：native 运行抛出的 `ClassNotFoundException`/`NoSuchMethodException` 会指名缺失的类，
把该类以最小范围（通常是仅构造器，或按用途加 `queryAll*`）补回清单，再重复构建+运行。
这既验证了“删除”正确性，也为后续补注册保留了最小增量。

---

## 6. 本次审计结果

| 清单 | 审计前 | 审计后 | 说明 |
|---|---|---|---|
| fork `org.hibernate.orm/hibernate-core/reflect-config.json` | 268 | 335 | 63 项内部类（轨迹证据）+ 88 项 `jakarta.persistence.*` + 186 项 `org.hibernate.annotations.*`（注解注册表扫描面，`queryAllDeclaredMethods`） |
| sample `native-image/reflect-config.json` | 122 | 0（已删除） | 应用面改为声明式：实体由 `beangle.xml` 扫描的 `SampleMapping` 注册，枚举/caffeine 由 `SampleAotHints` 注册，`jakarta.persistence.*` 已移入 fork |

---

## 9. 声明式改造（手写清单的替代）

审计后的 sample 手写清单随后整体删除，由两条自动路径取代：

- **registrar 类自注册**：`AotHintGenerator` 对清单/`beangle.xml` 声明的每个
  `AotHintRegistrar` 类自动注册其类自身——普通类注册构造器，Scala object 同时注册
  伴生类的 public 字段（覆盖 `MODULE$`）。运行期 `Profiles`/`EnumConverters` 经
  `Reflections.getInstance` 按名实例化模块/枚举，native 镜像只收录静态可达类，
  不显式登记则 `Class.forName` 找不到（实测 `ClassNotFoundException`）。
- **枚举字段支持**：`AotHints.registerType` 对 enum 类型（Scala 3 enum、Java enum、
  枚举伴生 `scala.deriving.Mirror.Sum`）自动补 `PublicFields`（`allPublicFields`）；
  Scala 3 enum 的伴生对象还会**自动增量注册**——应用只需注册枚举类型本身
  （`registerType(classOf[EmpLevel])`），无需写 `classOf[EmpLevel.type]`；
  `MetaRegistrar.addMetas` 进一步**遍历实体属性树**（集合元素、递归 `@component`
  值类型），`bind`/`register` 实体后枚举属性零手工注册（sample 的 `EmpLevel`
  即由 `SampleMapping` 绑定自动带出）；
  `Reflections.getInstance`/`tryGetInstance` 取 Scala object 单例由
  `getDeclaredField("MODULE$")` 改为 `getField("MODULE$")`（`MODULE$` 是
  public static，`allPublicFields` 即覆盖，实测 `NoSuchFieldException: MODULE$` 消除），
  应用无需为枚举伴生逐类定制策略。

分层：**fork 承担库面**（hibernate 内部反射点 + JPA/Hibernate 注解注册表），**sample 只留应用面**
（本应用实体等，由 AotPlugin/MetaPlugin 声明生成的方向一致）；JPA/Hibernate 注解是 Hibernate
对每个实体都扫描的通用 API，属于库面而非应用面，故从 sample 移入 fork。

fork 内部类部分（63 项，全部有轨迹证据）构成：

- 20 项事件监听器数组类（`[Lorg.hibernate.event.spi.*EventListener;`，仅类名）；
- 32 项构造器/具体方法（jboss-logging 生成的 `*_$logger` 的 `<init>(Logger)`、
  `ImplicitNamingStrategyJpaCompliantImpl`、`JCacheRegionFactory` 3 个构造器、
  `BasicCollectionPersister`/`OneToManyPersister`、`DataSourceConnectionProvider` 等）；
- 6 项注解类（`queryAllDeclaredMethods` + 被访问的注解元素，如 `GenericGenerator`）；
- 3 项仅 `queryAllPublicMethods`（`SqlStatementLogger`、`DefaultSessionFactoryBuilderService`、
  `JdbcResourceLocalTransactionCoordinatorBuilderImpl`）；
- 1 项 `EventType`（`allDeclaredFields`，其 `static{}` 用 `getDeclaredFields`+`Field.get` 建 map）；

验证（扩宽后的 sample：关联/集合/枚举/UDT/Code 生成器/merge/evict/refresh/2LC/OQL join/
分页/in/count/bulk update/native SQL）：`mvn install` 通过；native 构建 2m26s 成功；
运行 `exit=0`、`All operations completed successfully`；二进制 ~123M。

---

## 7. 边界与注意事项

- **轨迹只覆盖“本次运行覆盖的路径”**：不同事件（merge/evict/replicate）、不同方言、不同功能
  不会命中。因此审计结论对当前应用有效；应用换了功能组合后，缺失项按第 5 节的失败驱动方式补齐。
- **agent 不追踪所有反射形态**：构建期静态初始化里的反射、`MethodHandle`、JNI
  （需单独的 `jni-config`）不在默认轨迹内。若担心，可在 `--initialize-at-run-time` 类上
  单独核对。
- **库声明面与应用清单要区别对待**：库（fork、beangle-data-hibernate、commons）的注册为所有
  下游服务，收紧只应依据“该类在库内的真实反射用途”；应用清单才适合“一次运行轨迹 + 删除”。
- **收紧后其他应用的补注册路径**：应用侧定义 `AotHintRegistrar` 子类（锚定
  `aot-registrars.txt`），由 `AotPlugin` 自动生成其 `META-INF/native-image/beangle` 配置——
  与 Quarkus“扩展注册固定面、应用注册自己面”的模式一致。
- **版本差异**：方法以自身 fork 的运行时行为为准，不依赖上游（Hibernate 6）的类清单，
  升级 Hibernate 版本后建议重跑一次审计。

---

## 8. 与 Quarkus 的对照

| 维度 | Quarkus | 本方案 |
|---|---|---|
| 证据来源 | 构建期 Jandex 索引 + 扩展内硬编码注册点 | 运行期 agent 轨迹（一次性审计）+ 失败驱动补齐 |
| 实体/注解面 | 扩展在构建期批量注册（methods+fields+constructors） | 应用侧 `AotHintRegistrar` 声明，`AotPlugin` 生成 |
| 框架内部面 | 扩展内逐个注册（~20-30 个固定类） | fork jar 内嵌审计后清单（63 项） |
| 精确度 | 按类设置 methods/fields/constructors/query 开关 | 按类设置，且成员级精确（具体方法/构造器） |
| 版本耦合 | 与所支持 Hibernate 版本绑定 | 与自身 fork 版本绑定，升级后重跑审计即可 |
