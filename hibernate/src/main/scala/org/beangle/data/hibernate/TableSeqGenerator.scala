/*
 * Beangle, Agile Java/Scala Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2013, Beangle Software.
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
package org.beangle.data.hibernate;

import java.util.Properties
import org.beangle.commons.lang.Strings
import org.hibernate.dialect.Dialect
import org.hibernate.id.IdentifierGenerator
import org.hibernate.id.PersistentIdentifierGenerator
import org.hibernate.id.SequenceGenerator
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.`type`.Type
import org.beangle.commons.logging.Logging
import org.beangle.data.jpa.mapping.NamingPolicy

/**
 * 按照表明进行命名序列<br>
 * 依据命名模式进行，默认模式{table}_seq<br>
 * 该生成器可以
 *
 * <pre>
 * 1)具有较好的数据库移植性，支持没有sequence的数据库。
 * 2)可以通过设置优化起进行优化
 * 3)可以按照表名进行自动命名序列名，模式{table}_seq
 * </pre>
 *
 * @author chaostone
 */
class TableSeqGenerator extends SequenceStyleGenerator with Logging {

  /** 序列命名模式 */
  val sequencePattern = "seq_{table}"

  /**
   * If the parameters do not contain a {@link SequenceGenerator#SEQUENCE} name, we assign one
   * based on the table name.
   */
  override def configure(htype: Type, params: Properties, dialect: Dialect) {
    import SequenceStyleGenerator._
    import RailsNamingStrategy._
    if (Strings.isEmpty(params.getProperty(SEQUENCE_PARAM))) {
      val tableName = params.getProperty(PersistentIdentifierGenerator.TABLE)
      val pk = params.getProperty(PersistentIdentifierGenerator.PK)
      if (null != tableName) {
        var seqName = Strings.replace(sequencePattern, "{table}", tableName)
        seqName = Strings.replace(seqName, "{pk}", pk)
        if (seqName.length > NamingPolicy.defaultMaxLength)
          logger.warn("{}'s length >=30, wouldn't be supported in oracle!", seqName);
        val entityName = params.getProperty(IdentifierGenerator.ENTITY_NAME)
        if (null != entityName && null != namingPolicy) {
          val schema = namingPolicy.getSchema(entityName)
          if (null != schema) seqName = schema + "." + seqName
        }
        params.setProperty(SEQUENCE_PARAM, seqName)
      }
    }
    super.configure(htype, params, dialect)
  }
}
