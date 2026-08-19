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
实体集合可枚举，适合构建期处理；但 `AccessTracker`/Hibernate 代理等运行期字节码生成必须改为构建期预生成。

构建期生成 native-image 配置与预生成类：

```bash
sbt 'nativeImageConfig --output target/native-image --engine PostgreSQL \
     --dialect org.hibernate.dialect.PostgreSQLDialect'
# 产物: reflect-config.json / resource-config.json / proxy-config.json /
#       serialization-config.json / native-image-args.txt / trackers/
```

详见 [docs/native-image.md](docs/native-image.md)（可行性分析、阻塞点审计、分阶段改造计划与实现状态）。

## License

Beangle Data is released under the [GNU Lesser General Public License v3](LICENSE).