package org.beangle.data.jpa.hibernate.id

import java.{ util => ju }

import org.beangle.data.jpa.mapping.NamingPolicy
import org.hibernate.`type`.{ IntegerType, LongType, ShortType, Type }
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.id.{ Configurable, IdentifierGenerator }
import org.hibernate.id.PersistentIdentifierGenerator.{ CATALOG, SCHEMA, TABLE }
import org.hibernate.mapping.Table

class AutoIncrementGenerator extends IdentifierGenerator with Configurable {
  var identifierType: Type = _
  var query: String = _

  override def configure(t: Type, params: ju.Properties, dialect: Dialect) {
    this.identifierType = t
    val entityName = params.getProperty(IdentifierGenerator.ENTITY_NAME)
    val schema = NamingPolicy.Instance.getSchema(entityName).getOrElse(params.getProperty(SCHEMA))
    val tableName = Table.qualify(dialect.quote(params.getProperty(CATALOG)), dialect.quote(schema), dialect.quote(params.getProperty(TABLE)))
    query = "select max(id) from " + tableName
  }

  def generate(session: SessionImplementor, obj: Object): java.io.Serializable = {
    val jdbcCoordinator = session.getTransactionCoordinator().getJdbcCoordinator()
    val st = jdbcCoordinator.getStatementPreparer().prepareStatement(query)
    try {
      val rs = jdbcCoordinator.getResultSetReturn().extract(st)
      if (rs.next()) {
        identifierType match {
          case lt: LongType => rs.getLong(1) + 1
          case it: IntegerType => rs.getInt(1) + 1
          case st: ShortType => (rs.getShort(1) + 1).shortValue
        }
      } else {
        identifierType match {
          case lt: LongType => 1L
          case it: IntegerType => 1
          case st: ShortType => 1.asInstanceOf[Short]
        }
      }
    } finally {
      jdbcCoordinator.release(st)
    }
  }
}