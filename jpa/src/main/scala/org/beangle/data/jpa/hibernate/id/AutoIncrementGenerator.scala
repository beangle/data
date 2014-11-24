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
import java.sql.CallableStatement

class AutoIncrementGenerator extends IdentifierGenerator with Configurable {
  var identifierType: Type = _
  val sql = "{? = call next_id(?)}"
  var tableName: String = _

  override def configure(t: Type, params: ju.Properties, dialect: Dialect) {
    this.identifierType = t
    val schema = NamingPolicy.Instance.getSchema(params.getProperty(IdentifierGenerator.ENTITY_NAME)).getOrElse(params.getProperty(SCHEMA))
    tableName = Table.qualify(dialect.quote(params.getProperty(CATALOG)), dialect.quote(schema), dialect.quote(params.getProperty(TABLE)))
  }

  def generate(session: SessionImplementor, obj: Object): java.io.Serializable = {
    val jdbc = session.getTransactionCoordinator.getJdbcCoordinator
    val st = jdbc.getStatementPreparer().prepareStatement(sql, true).asInstanceOf[CallableStatement]
    try {
      st.registerOutParameter(1, java.sql.Types.BIGINT)
      st.setString(2, tableName)
      st.execute()
      val id = st.getLong(1)
      identifierType match {
        case lt: LongType    => new java.lang.Long(id)
        case it: IntegerType => new Integer(id.asInstanceOf[Int])
        case sht: ShortType  => new java.lang.Short(st.getShort(1).asInstanceOf[Short])
      }
    } finally {
      jdbc.release(st)
    }

  }
}
