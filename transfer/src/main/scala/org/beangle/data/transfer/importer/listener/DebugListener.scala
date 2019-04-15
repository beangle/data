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
package org.beangle.data.transfer.importer.listener

import org.beangle.data.transfer.importer.AbstractImportListener
import org.beangle.data.transfer.importer.ImportResult

/**
 * 转换调试监听器
 *
 * @author chaostone
 */
class DebugListener extends AbstractImportListener {

  override def onStart(tr: ImportResult) {
    tr.addMessage("start", transfer.dataName)
  }

  override def onFinish(tr: ImportResult) {
    tr.addMessage("end", transfer.dataName)
  }

  override def onItemStart(tr: ImportResult) {
    tr.addMessage("start Item", transfer.tranferIndex + "")
  }

  override def onItemFinish(tr: ImportResult) {
    tr.addMessage("end Item", transfer.current)
  }

}
