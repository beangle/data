/* Copyright c 2005-2012.
 * Licensed under GNU  LESSER General Public License, Version 3.
 * http://www.gnu.org/licenses
 */
package org.beangle.data.lint.seq

/**
 * @author cheneystar 2008-07-23
 */
trait TableSequenceDao {

  /** 得到所有用户的序列号* */
  def getAllNames(): List[String]

  /** 得到数据库中没有被指定的sequence* */
  def getNoneReferenced(): List[String]

  /**
   * 找到所有错误的sequence
   *
   * @return
   */
  def getInconsistent(): List[TableSequence]

  /**
   * 删除指定的sequence
   *
   * @param sequence_name
   * @return
   */
  def drop(sequence_name: String): Boolean

  def setRelation(relation: SequenceNamePattern);

  def adjust(tableSequence: TableSequence): Long
}
