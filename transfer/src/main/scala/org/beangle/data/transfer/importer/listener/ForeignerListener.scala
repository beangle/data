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

import org.beangle.commons.bean.Properties
import org.beangle.commons.lang.Strings
import org.beangle.data.dao.EntityDao
import org.beangle.data.model.Entity
import org.beangle.data.transfer.importer.{AbstractImporter, ImportListener, ImportResult, MultiEntityImporter}

object ForeignerListener {
  val CACHE_SIZE = 500
}
/**
 * 导入数据外键监听器<br>
 * 这里尽量使用entityDao，因为在使用entityService加载其他代码时，jpa会保存还未修改外的"半成对象"<br>
 * 从而造成有些外键是空对象的错误<br>
 * 如果外键不存在，则目标中的外键会置成null；<br>
 * 如果外键是空的，那么目标的外键取决于transfer.isIgnoreNull取值
 *
 * @author chaostone
 */
class ForeignerListener(entityDao: EntityDao) extends ImportListener {

  import ForeignerListener._
  protected val foreigersMap = new collection.mutable.HashMap[String, collection.mutable.HashMap[String, Object]]

  private val foreigerKeys = new collection.mutable.ListBuffer[String]
  foreigerKeys += "code"

  private var multiEntity = false

  override def onStart(tr: ImportResult) {
    multiEntity = transfer.getClass == classOf[MultiEntityImporter]
  }

  override def onItemFinish(tr: ImportResult) {
    val itermTranfer = transfer.asInstanceOf[AbstractImporter]
    // 过滤所有外键
    val iter = itermTranfer.attrs.iterator
    while (iter.hasNext) {
      val attri = iter.next()
      var foreigerKeyIndex = -1
      val isforeiger = foreigerKeys exists { fk =>
        foreigerKeyIndex += 1
        attri.endsWith("." + fk) && Strings.count(attri, '.') >= 2
      }
      if (isforeiger) {
        val codeValue = transfer.curData(attri).asInstanceOf[String]
        var foreiger: Object = null
        // 外键的代码是空的
        if (Strings.isNotEmpty(codeValue)) {
          var entity: Object = null
          if (multiEntity) {
            val shortName = Strings.substringBefore(attri, ".")
            entity = transfer.asInstanceOf[MultiEntityImporter].getCurrent(shortName)
          } else {
            entity = transfer.current
          }

          val attr = Strings.substringAfter(attri, ".")
          var nestedForeigner = Properties.get[Object](entity, Strings.substring(attr, 0, attr.lastIndexOf(".")))
          if (nestedForeigner.isInstanceOf[Option[_]]) {
            nestedForeigner = nestedForeigner.asInstanceOf[Option[AnyRef]].orNull
          }
          nestedForeigner match {
            case nestf: Entity[_] =>
              val className = nestedForeigner.getClass.getName
              val foreignerMap = foreigersMap.getOrElseUpdate(className, new collection.mutable.HashMap[String, Object])
              if (foreignerMap.size > CACHE_SIZE) foreignerMap.clear()
              foreiger = foreignerMap.get(codeValue).orNull
              if (foreiger == null) {
                val clazz = nestedForeigner.getClass.asInstanceOf[Class[Entity[_]]]
                val foreigners = entityDao.findBy(clazz, foreigerKeys(foreigerKeyIndex), List(codeValue))
                if (foreigners.nonEmpty) {
                  foreiger = foreigners.head
                  foreignerMap.put(codeValue, foreiger)
                } else {
                  tr.addFailure("代码不存在", codeValue)
                }
              }
            case _ =>
          }
          val parentAttr = Strings.substring(attr, 0, attr.lastIndexOf("."))
          val entityImporter = transfer.asInstanceOf[MultiEntityImporter]
          entityImporter.populator.populate(entity.asInstanceOf[Entity[_]], entityImporter.domain.getEntity(entity.getClass).get, parentAttr, foreiger)
        }
      }
    }
  }

  def addForeigerKey(key: String) {
    this.foreigerKeys += key
  }

  /**
   * 结束转换
   */
  override def onFinish(tr: ImportResult) {}

  /**
   * 开始转换单个项目
   */
  override def onItemStart(tr: ImportResult) {}

}
