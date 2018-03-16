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
package org.beangle.data.transfer

import java.util.Locale

import org.beangle.data.transfer.io.{ Reader, TransferFormat }

/**
 * 数据转换接口
 *
 * @author chaostone
 */
trait Transfer {
  /**
   * 启动转换
   */
  def transfer(tr: TransferResult);

  /**
   * 转换一个对象
   */
  def transferItem(): Unit

  /**
   * 添加转换监听器
   */
  def addListener(listener: TransferListener): Transfer

  /**
   * 转换数据的类型
   */
  def format: TransferFormat.Value

  /**
   * 转换使用的locale
   */
  def locale: Locale

  /**
   * 转换数据的名称
   */
  def dataName: String

  /**
   * 得到转换过程中失败的个数
   */
  def fail: Int

  /**
   * 得到转换过程中成功的个数
   */
  def success: Int

  /**
   * 查询正在转换的对象的次序号,从1开始
   */
  def tranferIndex: Int

  /**
   * 返回方前正在转换成的对象
   */
  def current: AnyRef

  /**
   * 是否忽略空值
   */
  def ignoreNull: Boolean

  /**
   * 读取数据，设置内部步进状态等
   */
  def read(): Boolean

  /**
   * 当前读入的数据是否有效
   */
  def isDataValid: Boolean

  /**
   * 设置当前正在转换的对象
   */
  def current_=(obj: AnyRef)

  var reader: Reader = _

  var attrs: List[String] = _

  var curData: collection.mutable.Map[String, Any] = _

  var prepare: TransferPrepare = _
}
