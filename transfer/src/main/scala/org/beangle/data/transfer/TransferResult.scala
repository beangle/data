/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.transfer;

import org.beangle.commons.collection.Collections
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import org.beangle.commons.conversion.Conversion
import org.beangle.commons.conversion.impl.DefaultConversion

/**
 * 转换结果
 *
 * @author chaostone
 */
class TransferResult {

  val msgs = new ListBuffer[TransferMessage]

  val errs = new ListBuffer[TransferMessage]

  var transfer: Transfer = _

  def addFailure(message: String, value: Any): Unit = {
    errs += new TransferMessage(transfer.tranferIndex, message, value)
  }

  def addMessage(message: String, value: Any): Unit = {
    msgs += new TransferMessage(transfer.tranferIndex, message, value)
  }

  def hasErrors: Boolean = {
    !errs.isEmpty
  }

  def errors: Int = {
    errs.size
  }
}
