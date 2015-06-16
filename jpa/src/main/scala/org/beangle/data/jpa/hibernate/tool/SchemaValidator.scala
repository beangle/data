/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jpa.hibernate.tool

import java.io.{ File, FileWriter, Writer }
import java.sql.{ Connection, Types }

import org.beangle.commons.conversion.converter.String2BooleanConverter
import org.beangle.data.jpa.hibernate.DefaultConfigurationBuilder
import org.beangle.data.jpa.hibernate.cfg.OverrideConfiguration
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.{ AvailableSettings, Configuration, NamingStrategy, ObjectNameNormalizer }
import org.hibernate.dialect.Dialect
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.engine.spi.Mapping
import org.hibernate.id.PersistentIdentifierGenerator
import org.hibernate.mapping.{ Column, Table }
import org.hibernate.tool.hbm2ddl.{ DatabaseMetadata, TableMetadata }

object SchemaValidator {

  def main(args: Array[String]): Unit = {
    val target = if (args.length > 0) args(0) else "/tmp"
    if (!(new File(target).exists)) {
      println(target + " not exists.")
      return
    }

    val config = DefaultConfigurationBuilder.build(new OverrideConfiguration)
    val serviceRegistry = new StandardServiceRegistryBuilder().applySettings(config.getProperties).build()

    val writer = new FileWriter(target + "/schema_validate.txt")
    val jdbcServices = serviceRegistry.getService(classOf[JdbcServices])
    val connectionProvider = serviceRegistry.getService(classOf[ConnectionProvider])
    val connection = connectionProvider.getConnection
    val dialect = jdbcServices.getDialect
    val validator = new SchemaValidator(config, connection, dialect)
    validator.validate(writer)
    writer.close()
    connection.close()
    StandardServiceRegistryBuilder.destroy(serviceRegistry)
    if (validator.errors == 0) {
      println("Schema is OK!")
    } else {
      println(s"Schema validating is completed! See $target/schema_validate.txt")
    }
  }

}

class SchemaValidator(cfg: Configuration, connection: Connection, dialect: Dialect) {
  val mapping = cfg.buildMapping()
  val meta = new DatabaseMetadata(connection, dialect, cfg, false)

  var errors: Int = 0

  def warn(writer: Writer, msg: String): Unit = {
    writer.write(msg)
    writer.write('\n')
  }

  def missing(writer: Writer, msg: String): Unit = {
    errors += 1
    writer.write("Missing ")
    writer.write(msg)
    writer.write('\n')
  }

  def mismatch(writer: Writer, msg: String, expected: Any, found: Any): Unit = {
    errors += 1
    writer.write("Mismatch ")
    writer.write(msg)
    writer.write(" Found: ")
    writer.write(found.toString)
    writer.write(" expected: ")
    writer.write(expected.toString)

    writer.write('\n')
  }

  object Normalizer extends ObjectNameNormalizer with Serializable {
    override def isUseQuotedIdentifiersGlobally(): Boolean = {
      val setting = cfg.getProperty(AvailableSettings.GLOBALLY_QUOTED_IDENTIFIERS).asInstanceOf[String]
      return setting != null && java.lang.Boolean.valueOf(setting)
    }

    override def getNamingStrategy: NamingStrategy = {
      return cfg.getNamingStrategy
    }
  }

  private def validate(writer: Writer): Unit = {
    val properties = cfg.getProperties
    val defaultCatalog = properties.getProperty(AvailableSettings.DEFAULT_CATALOG)
    val defaultSchema = properties.getProperty(AvailableSettings.DEFAULT_SCHEMA)

    var tables = new collection.mutable.ListBuffer[Table]
    val iter = cfg.getTableMappings
    while (iter.hasNext) {
      val table = iter.next().asInstanceOf[Table]
      if (table.isPhysicalTable) tables += table
    }
    tables = tables.sortWith((ta, tb) => tableId(ta).compareTo(tableId(tb)) < 0)

    tables foreach { table =>
      val tableInfo = meta.getTableMetadata(
        table.getName,
        if (table.getSchema == null) defaultSchema else table.getSchema,
        if (table.getCatalog == null) defaultCatalog else table.getCatalog,
        table.isQuoted)
      if (tableInfo == null) {
        missing(writer, "table: " + tableId(table))
      } else {
        validateColumns(table, dialect, mapping, tableInfo, writer)
      }
    }

    val idIter = cfg.iterateGenerators(dialect)
    while (idIter.hasNext) {
      val generator = idIter.next().asInstanceOf[PersistentIdentifierGenerator]
      var key = generator.generatorKey
      if (key.isInstanceOf[String]) {
        key = Normalizer.normalizeIdentifierQuoting(key.asInstanceOf[String])
      }
      if (!meta.isSequence(key) && !meta.isTable(key)) {
        missing(writer, "sequence or table: " + key)
      }
    }

    warn(writer, s"Schemas have $errors errors.")
  }

  def validateColumns(table: Table, dialect: Dialect, mapping: Mapping, tableInfo: TableMetadata, writer: Writer): Unit = {
    val iter = table.getColumnIterator.asInstanceOf[java.util.Iterator[Column]]
    while (iter.hasNext) {
      val col = iter.next()
      val columnInfo = tableInfo.getColumnMetadata(col.getName)
      if (columnInfo == null) {
        missing(writer, "column: " + col.getName + " in " + Table.qualify(tableInfo.getCatalog, tableInfo.getSchema, tableInfo.getName))
      } else {
        // check type 
        val typesMatch =
          col.getSqlType(dialect, mapping).toLowerCase.startsWith(columnInfo.getTypeName.toLowerCase) ||
            columnInfo.getTypeCode == col.getSqlTypeCode(mapping)
        if (!typesMatch) {
          mismatch(writer,
            "type: " + tableId(tableInfo) + "(" + col.getName + ").",
            col.getSqlType(dialect, mapping), columnInfo.getTypeName.toLowerCase)
        } else {
          // check length
          columnInfo.getTypeCode match {
            case Types.CHAR | Types.VARCHAR =>
              if (col.getLength != columnInfo.getColumnSize) {
                mismatch(writer, "length: " + tableId(tableInfo) + "(" + col.getName + ").", col.getLength, columnInfo.getColumnSize)
              }
            case _ =>
          }
        }
        // check notnull
        if (col.isNullable != String2BooleanConverter(columnInfo.getNullable)) {
          mismatch(writer,
            "nullable: " + tableId(tableInfo) + "(" + col.getName + ")", col.isNullable, String2BooleanConverter(columnInfo.getNullable))
        }
      }
    }
  }

  def tableId(tableInfo: TableMetadata): String = {
    Table.qualify(tableInfo.getCatalog, tableInfo.getSchema, tableInfo.getName)
  }

  def tableId(table: Table): String = {
    Table.qualify(table.getCatalog, table.getSchema, table.getName)
  }
}