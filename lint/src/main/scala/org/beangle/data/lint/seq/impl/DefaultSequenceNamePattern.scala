/* Copyright c 2005-2012.
 * Licensed under GNU  LESSER General Public License, Version 3.
 * http://www.gnu.org/licenses
 */
package org.beangle.data.lint.seq.impl

import org.beangle.commons.bean.Initializing
import org.beangle.commons.lang.Strings
import org.beangle.data.lint.seq.SequenceNamePattern

class DefaultSequenceNamePattern extends SequenceNamePattern with Initializing {

  var pattern = "SEQ_${table}"

  var begin = 0
  var postfix: String = null

  def getTableName(seqName: String): String = {
    var end = seqName.length()
    if (Strings.isNotEmpty(postfix)) end = seqName.lastIndexOf(postfix)
    return Strings.substring(seqName, begin, end)
  }

  def getPattern(): String = pattern

  def init() {
    begin = pattern.indexOf("${table}")
    postfix = Strings.substringAfter(pattern, "${table}")
  }

}
