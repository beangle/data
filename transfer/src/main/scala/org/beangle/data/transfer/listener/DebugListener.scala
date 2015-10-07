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
package org.beangle.data.transfer.listener

import org.beangle.data.transfer.AbstractTransferListener
import org.beangle.data.transfer.TransferResult

/**
 * 转换调试监听器
 *
 * @author chaostone
 */
class DebugListener extends AbstractTransferListener {

  override def onStart(tr: TransferResult) {
    tr.addMessage("start", transfer.dataName);
  }

  override def onFinish(tr: TransferResult) {
    tr.addMessage("end", transfer.dataName);
  }

  override def onItemStart(tr: TransferResult) {
    tr.addMessage("start Item", transfer.tranferIndex + "");
  }

  override def onItemFinish(tr: TransferResult) {
    tr.addMessage("end Item", transfer.current);
  }

}
