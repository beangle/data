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
package org.beangle.data.hibernate.id

import java.{ util => ju }

import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.data.hibernate.naming.NamingPolicy
import org.hibernate.dialect.Dialect
import org.hibernate.id.IdentifierGenerator
import org.hibernate.id.PersistentIdentifierGenerator.{ CATALOG, SCHEMA, TABLE }
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.id.enhanced.SequenceStyleGenerator.{ DEF_SEQUENCE_NAME, SEQUENCE_PARAM }
import org.hibernate.mapping.Table
/**
 * 按照表明进行命名序列<br>
 * 依据命名模式进行，默认模式seq_{table}<br>
 * 该生成器可以
 *
 * <pre>
 * 1)具有较好的数据库移植性，支持没有sequence的数据库。
 * 2)可以通过设置优化起进行优化
 * 3)可以按照表名进行自动命名序列名，模式seq_{table}
 * </pre>
 *
 * @author chaostone
 */
class TableSeqGenerator extends SequenceStyleGenerator with Logging {

  var sequencePrefix = "seq_"

  protected override def determineSequenceName(params: ju.Properties, dialect: Dialect): String = {
    import SequenceStyleGenerator._
    var seqName = params.getProperty(SEQUENCE_PARAM)
    if (Strings.isEmpty(seqName)) {
      val tableName = params.getProperty(TABLE)
      seqName = if (null != tableName) sequencePrefix + tableName else DEF_SEQUENCE_NAME
    }

    if (seqName.indexOf('.') < 0) {
      val entityName = params.getProperty(IdentifierGenerator.ENTITY_NAME)
      if (null != entityName && null != NamingPolicy.Instance) {
        val schema = NamingPolicy.Instance.getSchema(entityName).getOrElse(params.getProperty(SCHEMA))
        seqName = Table.qualify(dialect.quote(params.getProperty(CATALOG)), dialect.quote(schema), dialect.quote(seqName))
      }
    }
    if (Strings.substringAfterLast(seqName, ".").length > NamingPolicy.DefaultMaxLength) logger.warn(s"$seqName's length >=30, wouldn't be supported in oracle!")
    seqName
  }
}