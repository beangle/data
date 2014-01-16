package org.beangle.data.lint.seq

import java.io.FileInputStream

import org.beangle.data.jdbc.util.DbConfig
import org.beangle.data.jdbc.util.PoolingDataSourceFactory
import org.beangle.commons.logging.Logging
import org.beangle.data.lint.seq.impl.DefaultSequenceNamePattern
import org.beangle.data.lint.seq.impl.OracleTableSequenceDao

import javax.sql.DataSource

object SequenceChecker extends Logging {

  /**
   * SequenceChecker /path/to/dbconfig.xml info|update|remove
   *
   * @param args
   * @throws Exception
   */
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("Usage: SequenceChecker /path/to/your/xml info|update|remove")
      return
    }
    val xml = scala.xml.XML.load(new FileInputStream(args(0)))
    val dataSource = getDataSource(xml)
    val action = if (args.length > 1) args(1) else "info"
    val update = (action == "update")
    val remove = (action == "remove")

    val tableSequenceDao = new OracleTableSequenceDao()
    tableSequenceDao.setDataSource(dataSource)
    tableSequenceDao.setRelation(new DefaultSequenceNamePattern())
    val sequences = tableSequenceDao.getInconsistent()
    info(sequences)
    if (update)
      adjust(tableSequenceDao, sequences)

    if (remove)
      drop(tableSequenceDao, sequences)

  }

  def drop(tableSequenceDao: TableSequenceDao, sequences: List[TableSequence]) {
    val ps = System.out
    if (!sequences.isEmpty)
      ps.println("start drop ...")
    for (seq <- sequences) {
      if (null == seq.tableName) {
        tableSequenceDao.drop(seq.seqName)
        ps.println("drop sequence " + seq.seqName)
      }
    }
  }

  def adjust(tableSequenceDao: TableSequenceDao, sequences: List[TableSequence]) {
    val ps = System.out
    if (!sequences.isEmpty) ps.println("start adjust ...")
    for (seq <- sequences) {
      if (null != seq.tableName) {
        ps.println("adjust sequence " + seq.seqName + " with lastnumber "
          + tableSequenceDao.adjust(seq))
      }
    }
    ps.println("finish adjust")
  }

  def info(sequences: List[TableSequence]) {
    val ps = System.out
    if (sequences.isEmpty) {
      ps.println("without any inconsistent  sequence")
    } else {
      ps.println("find inconsistent  sequence " + sequences.size)
      ps.println("sequence_name(lastnumber) table_name(max id)")
    }

    for (seq <- sequences)
      ps.println(seq)
  }

  private def getDataSource(xml: scala.xml.Node): DataSource = {
    val dbconf = DbConfig.build(xml)
    new PoolingDataSourceFactory(dbconf.driver,
      dbconf.url, dbconf.user, dbconf.password, dbconf.props).getObject
  }
}