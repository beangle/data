/* Copyright c 2005-2012.
 * Licensed under GNU  LESSER General Public License, Version 3.
 * http://www.gnu.org/licenses
 */
package org.beangle.data.lint.seq

import org.beangle.commons.bean.Initializing

trait SequenceNamePattern extends Initializing {

  def getTableName(seqName: String): String

}
