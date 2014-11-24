package org.beangle.data.jpa.hibernate.id

import java.{ util => ju }
import org.beangle.data.jpa.mapping.NamingPolicy
import org.hibernate.`type`.{ IntegerType, LongType, ShortType, Type }
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.id.{ Configurable, IdentifierGenerator }
import org.hibernate.id.PersistentIdentifierGenerator.{ CATALOG, SCHEMA, TABLE }
import org.hibernate.mapping.Table
import org.hibernate.engine.jdbc.spi.JdbcCoordinator

class AutoIncrementGenerator extends IdentifierGenerator with Configurable {
  var identifierType: Type = _
  val sql = "next_id(?)"
  var tableName: String = _

  override def configure(t: Type, params: ju.Properties, dialect: Dialect) {
    this.identifierType = t
    val schema = NamingPolicy.Instance.getSchema(params.getProperty(IdentifierGenerator.ENTITY_NAME)).getOrElse(params.getProperty(SCHEMA))
    tableName = Table.qualify(dialect.quote(params.getProperty(CATALOG)), dialect.quote(schema), dialect.quote(params.getProperty(TABLE)))
  }

  def generate(session: SessionImplementor, obj: Object): java.io.Serializable = {
    val jdbc = session.getTransactionCoordinator.getJdbcCoordinator
    val st = jdbc.getStatementPreparer().prepareStatement(sql, true)
    try {
      st.setString(1, tableName)
      val rs = jdbc.getResultSetReturn().extract(st)
      rs.next()
      val id: Number =
        identifierType match {
          case lt: LongType    => new java.lang.Long(rs.getLong(1))
          case it: IntegerType => new Integer(rs.getInt(1))
          case st: ShortType   => new java.lang.Short(rs.getShort(1))
        }
      jdbc.release(rs, st)
      id
    } finally {
      jdbc.release(st)
    }

  }
}
