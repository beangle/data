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
package org.beangle.data.transfer.importer

import java.util.Locale

import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.data.transfer.Format
import org.beangle.data.transfer.io.Attribute

import scala.collection.mutable.ListBuffer

/** 导入的抽象和缺省实现
  *
  * @author chaostone
  */
abstract class AbstractImporter extends Importer with Logging {
  protected var transferResult: ImportResult = _
  protected val listeners = new ListBuffer[ImportListener]
  var success, fail = 0
  this.prepare = new AttributePrepare()
  protected var index = 0

  /**
    * 进行转换
    */
  def transfer(tr: ImportResult): Unit = {
    this.transferResult = tr
    this.transferResult.transfer = this
    val transferStartAt = System.currentTimeMillis()
    try {
      prepare.prepare(this)
    } catch {
      // 预导入发生位置错误，错误信息已经记录在tr了
      case e: Throwable => e.printStackTrace(); return
    }
    listeners.foreach(l => l.onStart(tr))
    while (read()) {
      val transferItemStart = System.currentTimeMillis()
      index += 1
      beforeImportItem()
      if (isDataValid) {
        val errors = tr.errors
        // 实体转换开始
        listeners.foreach(l => l.onItemStart(tr))
        // 如果转换前已经存在错误,则不进行转换
        if (tr.errors == errors) {
          // 进行转换
          transferItem()
          // 实体转换结束
          listeners.foreach(l => l.onItemFinish(tr))
          // 如果导入过程中没有错误，将成功记录数增一
          if (tr.errors == errors) this.success += 1
          else this.fail += 1
        }
      }
    }
    listeners.foreach(l => l.onFinish(tr))
    reader.close()
    logger.debug("importer elapse: " + (System.currentTimeMillis() - transferStartAt))
  }

  def ignoreNull: Boolean = {
    true
  }

  def locale: Locale = {
    Locale.getDefault()
  }

  def format: Format.Value = {
    reader.format
  }

  def tranferIndex: Int = {
    index
  }

  override def addListener(listener: ImportListener): Importer = {
    listeners += listener
    listener.transfer = this
    this
  }

  protected def beforeImportItem(): Unit = {
  }

  /**
    * 改变现有某个属性的值
    */
  def changeCurValue(attr: String, value: Any): Unit = {
    this.curData.put(attr, value)
  }

  final override def read(): Boolean = {
    val data = reader.read().asInstanceOf[Array[_]]
    if (null == data) {
      this.current = null
      this.curData = null
      false
    } else {
      curData = new collection.mutable.HashMap[String, Any]
      data.indices foreach { i =>
        this.curData.put(attrs(i).name, data(i))
      }
      true
    }
  }

  override def isDataValid: Boolean = {
    this.curData.values exists { v =>
      v match {
        case tt: String => Strings.isNotBlank(tt)
        case _ => null != v
      }
    }
  }

  def setAttrs(attrs: List[Attribute]): Unit = {
    this.attrs = attrs.toArray
  }

  def description(attr: String): String = {
    attrs.find(_.name == attr) match {
      case None => ""
      case Some(e) => e.description
    }
  }

}
