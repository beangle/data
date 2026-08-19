# OQL 查询 DSL 的 scala.Dynamic 设计

> 本文记录 `OqlBuilder` 类型安全查询 DSL 采用 `scala.Dynamic` 机制的设计目的、
> 设计取舍与使用方式。配套实现见 `model/src/main/scala/org/beangle/data/dao/Prop.scala` 与
> `OqlBuilder.scala`，native-image 语境下的整体评估见 [native-image.md](native-image.md)。

---

## 1. 背景：原有机制的代价

早期 `OqlBuilder.where/on` 的 lambda 参数是实体类型（`u: User`），拦截属性访问依赖
`AccessTracker` + ByteBuddy：**为每个实体/组件在运行期生成一个 `X$Tracker` 子类**，
重写全部 getter，把访问路径记入共享的 `Context/Names` 状态。这套机制带来三个问题：

1. **辅助类数量随实体规模线性增长**：实测每个 `$Tracker` 类约 1KB，千实体 ≈ 1-3MB 类字节，
   并需要配套的构建期预生成工具链（native-image 场景）；
2. **运行期字节码生成**：ByteBuddy 在 native-image 下不可用，且每次查询构造都要反射实例化 tracker；
3. **表达能力受限**：lambda 参数被钉死在实体类型上，`u.xxx` 只能是实体真实成员——
   数据库函数、聚合、非映射列、任意表达式都表达不了。

## 2. 设计目的

采用 `scala.Dynamic` 重新设计查询 DSL，目的有三：

### 2.1 防止生成过多的辅助类

把 lambda 参数类型改为 `Prop`（`extends scala.Dynamic`）。编译器把 `e.name.first` 改写为
`e.selectDynamic("name").selectDynamic("first")`，由**一个通用类**以字符串累积路径，
彻底替代 N 个 `X$Tracker` 子类：

- 零逐实体类生成；`Prop` 全库仅一个（88 行）；
- 无运行期字节码生成（ByteBuddy 从 OQL 路径移除，native-image 友好）；
- 每次查询构造从"反射 newInstance + 拦截链"变为普通对象创建（实测 ~40ns/条件，原 ~210ns，约 5.4x）；
- 无共享可变状态（原 `Context/Names`），并发更干净。

### 2.2 扩展设计用途：函数/聚合/集合/任意表达式

`scala.Dynamic` 同时改写方法调用：`u.func(a, b)` -> `u.applyDynamic("func")(a, b)`，
使 DSL 从"实体属性子集"升级为"表达式 DSL"：

- **数据库函数**：`e.lower(e.name.first).equal("bill")` -> `lower(u.name.first) = :v`；
- **聚合**：`e.count(e.roles).gt(0)`、`OqlBuilder.sum/avg/max/min/count/distinct(...)`；
- **集合查询**（HQL `elements`/`indices` 实证）：
  - `e.roleSet.contains(role)` -> `:v in elements(u.roleSet)`（实体集合，传实体对象）；
  - `e.properties.contains("x")` -> `:v in elements(u.properties)`（Map 的 **values**）；
  - `e.times.containsKey(1)` -> `:v in indices(u.times)`（Map 的 **keys**，独立语法）；
- **非映射列/计算路径**：`u.deletedAt` 等实体未映射但表存在的列可直接书写；
- **可组合过滤片段**：`def f(u: Prop) = u.lower(u.name).equal("x")` 作为普通函数自由组合；
- **select/groupBy/orderBy** 接受 `Prop/Var/String` 混用（`Any*` 渲染）。

### 2.3 接受静态检查缺失

`e.name` 对编译器是透明的 `selectDynamic` 调用，**属性路径/函数名不再编译期校验**：

- 拼写错误（`e.nmae`）在绑定（SessionFactory 启动）或查询构造期才暴露，不污染数据；
- 曾评估编译期宏校验与运行期路径校验两种补偿，均因复杂度高、收益有限而**不采用**；
  接受书写错误的运行期暴露，换取零类生成与表达自由。

## 3. 机制要点

### 3.1 scala.Dynamic 的 Scala 3 差异

Scala 3 的 `scala.Dynamic` 是**空标记 trait**（Scala 2 自带抽象方法），因此
`selectDynamic/applyDynamic` 必须定义在具体类（`Prop`）上，且接收者静态类型必须是
该具体类而非 `Dynamic` trait 本身——否则报 `selectDynamic is not a member of Dynamic`。

### 3.2 Prop 与渲染

- `selectDynamic(name)`：路径字符串累积（`member.granter.roles`），携带 `_.` 别名占位；
- `applyDynamic(name)(args*)`：函数/聚合，Prop 参数渲染为路径、字面量按值；
- 条件算子（`equal/gt/ge/lt/le/between/like/isNull/isNotNull/is/in`）委托给 `OqlBuilder.Var`，
  沿用其 SQL 渲染与 `? -> :vN` 参数绑定；
- `contains` 统一翻译 `? in elements(...)`（Map 值/集合元素/实体集合），`containsKey` 翻译
  `? in indices(...)`（Map 键）——均经 H2 实证（`elements(map)=values`，`indices(map)=keys`）。

## 4. 对原有代码的兼容程度

### 4.1 完全兼容（无需改动）

- **字符串形式查询**：`where("r.parent = :parent", role)`、`select("test.name")`、
  `groupBy("x")`、`orderBy("y")`、`having(...)`、`join(...)` 走 `AbstractQueryBuilder` 基类，行为不变；
- **lambda 形式**：`where { e => ... }` / `on { e => ... }` 的参数类型由 `T => ...` 改为 `Prop => ...`，
  无类型标注的 `{ e => ... }` 由编译器推断为 `Prop`，**源码级兼容**；
- **条件算子同名保留**：`equal/gt/ge/lt/le/between/like/isNull/isNotNull/is(exp, args*)` 全部可用；
- **聚合辅助函数**：`OqlBuilder.max(e.id)`、`count(distinct(e.role.name))`、`sum/avg/min` 改为接受
  `Any`（Prop/Var/String），原调用方式不变；
- **函数式包装**：`e.id.f("avg(_)")`、自定义函数 `e.id.is("bitand(_,?)>0", 123)` 保留；
- **`import q.given`**：原用于导入 `any2Var` 隐式，该隐式已移除；导入成为空导入——**保留不报错**，
  新代码可不写。

### 4.2 需要迁移（编译期可能报错）

- **lambda 内把参数当实体类型使用**：如 `e.id` 传给实体类型形参、调用实体方法——
  参数现在是 `Prop`，此类代码需改为直接用路径算子表达；
- **依赖 getter 返回真实值**：旧机制下 `u.name.first` 返回 String 值；现在返回 `Prop`（路径对象），
  若代码使用了返回值本身（如 `val s: String = u.name.first`）需调整。

### 4.3 行为变化（非破坏）

- 新增函数/聚合/集合/任意表达式能力（旧代码无法表达，纯增量）；
- 运行期不再生成 tracker 类、不再反射实例化，性能与并发更优。

## 5. 使用示例

以下示例假设实体 `User`：`name: Name(first, last)`、`member: Member(admin, granter)`、
`role: Option[Role]`、`roleSet: Set[Role]`、`properties: Map[String, String]`、
`times: Map[Int, WeekTime]`、`age: Option[Int]`、`createdOn: java.sql.Date`、`id: Long`。

### 5.1 基础条件

```scala
val q = OqlBuilder.from(classOf[User], "u")
q.where { e =>
  e.name.first.equal("Bill")
    .and(e.age.gt(18))
    .and(e.role.name.like("admin"))
    .and(e.createdOn.isNull or e.createdOn.between(d1, d2))
    .and(e.id.in(1, 2, 3))
}
// select u from ...User u where u.name.first = :v1 and u.age > :v2 and u.role.name like :v3
//   and (u.createdOn is null or u.createdOn between :v4 and :v5) and u.id in (:v6, :v7, :v8)
```

### 5.2 嵌套 and/or 组合

```scala
q.where { e =>
  (e.member.admin.equal(true) or e.name.last.isNull)
    .and(e.role.isNull or e.role.parent.name.like("x"))
}
// where (u.member.admin = :v1 or u.name.last is null) and (u.role is null or u.role.parent.name like :v2)
```

### 5.3 数据库函数与自定义函数

```scala
q.where { e =>
  e.lower(e.name.first).equal("bill")
    .and(e.coalesce(e.age, 0).gt(0))
    .and(e.id.is("bitand(_, ?) > 0", 8))
}
// where lower(u.name.first) = :v1 and coalesce(u.age, 0) > :v2 and bitand(u.id, :v3) > 0
```

### 5.4 集合查询

```scala
q.where { e =>
  e.roleSet.contains(adminRole)              // 实体集合：:v1 in elements(u.roleSet)
    .and(e.properties.contains("some street")) // Map 值：:v2 in elements(u.properties)
    .and(e.times.containsKey(1))               // Map 键：:v3 in indices(u.times)
}
```

### 5.5 聚合与 select/groupBy/having

```scala
q.on { e =>
  q.select(e.name.first, "count(*)")
  q.groupBy(e.name.first)
}
q.having("count(*) > 1")
// select u.name.first,count(*) from ...User u group by u.name.first having count(*) > 1

val q2 = OqlBuilder.from(classOf[User], "u")
q2.on { e =>
  q2.select(sum(e.id), count(distinct(e.role.name)), e.id.f("avg(_)"))
    .groupBy(e.member.admin)
}
// select sum(u.id),count(distinct u.role.name),avg(u.id) ... group by u.member.admin
```

### 5.6 堆叠条件与可组合过滤片段

```scala
q.where { e => e.member.admin.equal(true) }
q.where { e => e.age.gt(18) }
// where (u.member.admin = :v1) and (u.age > :v2)

def active(u: Prop): Prop = u.deletedAt.isNull  // 非映射列/计算路径
def adult(u: Prop): Prop = u.age.gt(18)
q.where { e => active(e).and(adult(e)) }
```

### 5.7 混合字符串与 Prop

```scala
q.on { e =>
  q.select(e.id, e.name.first, "count(*)")   // Prop 与字符串混用
    .orderBy(e.name.last)
}
```

## 6. 已知限制与使用约定

- 属性路径/函数名拼写错误运行期暴露（接受项）；
- 实体集合 `contains` 需传**实体对象**（传 id 会类型不匹配）；Map 查值用 `contains`、查键用 `containsKey`；
- `applyDynamic` 无函数白名单，自由形式把 SQL 正确性责任交给使用者；
- 函数调用中字符串参数按字面量处理（引号包裹），路径请用 `Prop` 表达。

## 7. 相关文件

- `model/src/main/scala/org/beangle/data/dao/Prop.scala` —— Dynamic 路径类（88 行）
- `model/src/main/scala/org/beangle/data/dao/OqlBuilder.scala` —— where/on/聚合/集合算子
- `model/src/main/scala/org/beangle/data/dao/AbstractQueryBuilder.scala` —— select/groupBy/orderBy Any* 渲染
- `model/src/test/scala/org/beangle/data/dao/OqlBuilderTest.scala` —— 语句级用例（含集合/函数/聚合）
- `hibernate/src/test/scala/org/beangle/data/hibernate/DynamicOqlTest.scala` —— H2 执行级用例
- `model/src/test/scala/org/beangle/data/dao/DynamicFuncPrototype.scala` —— 函数/聚合能力原型

## 8. 验证与性能数据

- 全量测试：`model` 37 + `hibernate` 23 全部通过；
- 微基准（路径记录，1M 次/轮，7 轮取最优）：tracker ~207-220 ns/op vs Dynamic ~38-40 ns/op（约 5.4x）；
- 绝对量说明：查询构造相对 DB 往返（毫秒级）可忽略，性能收益非主要动机，主要收益在工程简化与表达力。
