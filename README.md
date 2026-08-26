# Beangle Data

The Beangle Data Library is a Scala 3 ORM framework built on JPA/Hibernate, providing a convention-over-configuration domain model, a fluent query API, DDL generation and JSON:API serialization.

## Features

- **Convention-based ORM** — plain Scala classes mapped to tables by convention; entity, component and collection support via `Entity`, `Component` and the `pojo` traits
- **DSL mapping module** — declare tables, columns, indexes, caches, generators and relationships in a `MappingModule` with a Scala DSL (inline macros)
- **Fluent query API** — `OqlBuilder` with type-safe expressions, group/aggregate functions, conditions and pagination
- **DAO abstraction** — `EntityDao` for CRUD, search, `executeUpdate`, cache eviction and transaction operations; Hibernate-backed implementation
- **DDL generation** — generate database schemas from mappings via `DdlGenerator`, with `EJB3NamingPolicy` / `RailsNamingPolicy` and schema profiles
- **JSON:API support** — serialize entities into [JSON:API](https://jsonapi.org/) documents with sparse fields, includes and relationships
- **Rich type mapping** — JSON columns, enums, `YearMonth`, value types and the `Decimal5`/`TinyDecimal5` fixed-point decimals mapped to `decimal(p,5)` columns
- **Transaction management** — Spring-based `HibernateTransactionManager`, declarative transactions via `TransactionalProxy`
- **ID generators** — auto-increment, date-time, code-style, uuid and assigned strategies

## Dependency

```scala
libraryDependencies += "org.beangle.data" % "beangle-data-model" % "5.12.6"
libraryDependencies += "org.beangle.data" % "beangle-data-hibernate" % "5.12.6"
```

Requires Scala 3 and JDK 21+.

## Modules

- `beangle-data-model` — domain model, DAO interfaces, OQL builder, ORM metadata and DDL generation (no Hibernate dependency)
- `beangle-data-hibernate` — Hibernate integration: `EntityDao` implementation, session factory, transaction management, type registration and ID generators

## Quick Start

### Define an entity

```scala
import org.beangle.data.model.*
import org.beangle.data.model.pojo.Named

class User extends LongId, Named {
  var member: NamedMember = _
  var roles: scala.collection.mutable.Buffer[Role] = _
  var age: Option[Int] = _
}

class NamedMember extends Component {
  var name: Name = _
}

class Name extends Component {
  var firstName: String = _
  var lastName: String = _
}
```

### Declare mappings in a `MappingModule`

```scala
import org.beangle.data.orm.{IdGenerator, MappingModule}

object DefaultMappings extends MappingModule {
  def binding(): Unit = {
    bind[User].declare { e =>
      e.member.name.first is(notnull, length(20), unique)
      e.member.name.first & e.member.name.last are notnull
      e.roles is ordered
    }.generator(IdGenerator.DateTime)

    bind[Role].declare { e =>
      e.name is(notnull, length(20), unique)
    }.generator(IdGenerator.Native)
  }
}
```

### Configure mappings via `beangle.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beangle>
  <jpa>
    <mapping class="com.example.DefaultMappings"/>
    <naming>
      <profile package = "com.example.model" pluralize="true"/>
    </naming>
  </jpa>
</beangle>
```

### Build the session factory and DAO

```scala
import org.beangle.data.hibernate.{HibernateEntityDao, LocalSessionFactoryBean}
import javax.sql.DataSource

val ds: DataSource = ...
val builder = new LocalSessionFactoryBean(ds)
builder.ormLocation = "classpath*:beangle.xml"
builder.init()
val entityDao = new HibernateEntityDao(builder.getObject)
entityDao.init()
```

### Query with `OqlBuilder`（scala.Dynamic 类型安全 DSL）

> 设计目的与取舍（为何用 scala.Dynamic：防止生成过多辅助类、扩展函数/聚合/集合查询、接受静态检查缺失）
> 详见 [docs/dynamic-oql.md](docs/dynamic-oql.md)。

```scala
import org.beangle.data.dao.OqlBuilder

val q = OqlBuilder.from(classOf[User], "u")
q.where { u =>
  (u.member.name.first like "bil")
    .and(u.age isNotNull)
    .and(u.createdOn.isNull or u.createdOn.gt(java.time.LocalDate.now))
}
q.on { u =>
  q.select(u.member.name.first, u.member.name.last, "count(*)")
    .groupBy(u.member.name.first, u.member.name.last)
    .orderBy(u.member.name.last)
}
val users = entityDao.search(q)
```

lambda 参数为 `Prop`（scala.Dynamic），`u.name.first` 编译为 selectDynamic 链，无需为每个实体生成
tracker 类；`u.func(a, b)`（如 `u.lower(u.name)`、`u.count(u.roles)`）编译为 applyDynamic，可直接表达
数据库函数与聚合。

### Search by arbitrary properties

```scala
entityDao.findBy(classOf[User], "member.name.first" -> "Bill")
entityDao.count(classOf[User], "roles.id" -> 1L)
```

## GraalVM native-image 支持

本库支持以 GraalVM native-image 方式构建使用方应用。由于 ORM 绑定是代码声明式（`MappingModule`），
实体集合可枚举，适合构建期处理。查询 DSL（`OqlBuilder`）与绑定声明（`declare`）均已改用 scala.Dynamic
（`Prop`/`DeclareProp`），库侧运行期零类生成、零反射，`AccessTracker`/ByteBuddy 已删除；
剩余需要构建期预生成的是 Hibernate 懒加载代理（hibernate-core 侧，见 [docs/native-image.md](docs/native-image.md) 的 P2）。

构建期生成 native-image 配置与预生成类：

库自身固定反射点/资源由 `BeangleAotHints` 与 `HibernateAotHints`（`AotHintRegistrar`
子类，位于 `hibernate/.../aot`）声明——前者为 beangle 自身反射/资源，后者复刻
hibernate-graalvm `GraalVMStaticFeature` 的静态反射注册（使用方可不再依赖 hibernate-graalvm）；
`hibernate` 项目启用 `AotPlugin` 后在编译期自动生成 `META-INF/native-image` 配置并随 jar 内嵌；
应用侧实体等配置由应用定义自己的 `AotHintRegistrar`/`MetaRegistrar` 子类并启用 `AotPlugin` 生成。

详见 [docs/native-image.md](docs/native-image.md)（可行性分析、阻塞点审计、分阶段改造计划与实现状态）。

## License

Beangle Data is released under the [GNU Lesser General Public License v3](LICENSE).
