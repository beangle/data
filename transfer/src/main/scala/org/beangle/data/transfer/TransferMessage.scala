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
package org.beangle.data.transfer;

import org.beangle.commons.lang.Objects;
import scala.collection.mutable.ListBuffer

object TransferMessage {
  /** Constant <code>ERROR_ATTRS="error.transfer.attrs"</code> */
  val ERROR_ATTRS = "error.transfer.attrs";

  /** Constant <code>ERROR_ATTRS_EXPORT="error.transfer.attrs.export"</code> */
  val ERROR_ATTRS_EXPORT = "error.transfer.attrs.export";
}
/**
 * 转换消息
 */
class TransferMessage(val index: Int, val message: String, value: Any) {

  /**
   * 消息中使用的对应值
   */
  val values = new ListBuffer[Any]
  values += value

  /**
   * toString.
   */
  override def toString: String = {
    Objects.toStringBuilder(this).add("index", this.index).add("message", this.message)
      .add("values", this.values).toString();
  }

}
