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

### Query with `OqlBuilder`

```scala
import org.beangle.data.dao.OqlBuilder

val q = OqlBuilder.from(classOf[User], "u")
import q.given
q.where { u =>
  (u.member.name.first like "bil")
    .and(u.age isNotNull)
}
val users = entityDao.search(q)
```

### Search by arbitrary properties

```scala
entityDao.findBy(classOf[User], "member.name.first" -> "Bill")
entityDao.count(classOf[User], "roles.id" -> 1L)
```

## License

Beangle Data is released under the [GNU Lesser General Public License v3](LICENSE).
