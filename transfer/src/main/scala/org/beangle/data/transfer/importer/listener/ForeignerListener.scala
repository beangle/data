/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.transfer.importer.listener

import org.beangle.commons.bean.Properties
import org.beangle.commons.lang.Strings.*
import org.beangle.data.dao.{EntityDao, OqlBuilder}
import org.beangle.data.model.Entity
import org.beangle.data.transfer.importer.{AbstractImporter, ImportListener, ImportResult, MultiEntityImporter}

object ForeignerListener {
  val CACHE_SIZE = 500
}

/** 导入数据外键监听器
 * 这里尽量使用entityDao，因为在使用entityService加载其他代码时，jpa会保存还未修改外的"半成对象"<br>
 * 从而造成有些外键是空对象的错误<br>
 * 如果外键不存在，则目标中的外键会置成null；<br>
 * 如果外键是空的，那么目标的外键取决于transfer.isIgnoreNull取值
 *
 * @author chaostone
 */
class ForeignerListener(entityDao: EntityDao) extends ImportListener {

  import ForeignerListener.*

  protected val foreignersMap = new collection.mutable.HashMap[String, collection.mutable.HashMap[String, Object]]

  private val foreignerKeys = new collection.mutable.ListBuffer[String]
  foreignerKeys += "code"

  private var multiEntity = false

  private var aliases: Set[String] = Set.empty

  override def onStart(tr: ImportResult): Unit = {
    transfer match {
      case mei: MultiEntityImporter =>
        multiEntity = true
        aliases = mei.aliases.toSet
      case _ => multiEntity = false
    }
  }

  override def onItemFinish(tr: ImportResult): Unit = {
    val itermTransfer = transfer.asInstanceOf[AbstractImporter]
    // 过滤所有外键
    val iter = itermTransfer.attrs.iterator
    while (iter.hasNext) {
      val attri = iter.next().name
      val isForeigner =
        foreignerKeys exists { fk =>
          val endWithKey = attri.endsWith("." + fk) && count(attri, '.') >= 2
          if endWithKey then aliases.contains(substringBefore(attri, ".")) else false
        }
      if (isForeigner) {
        val codeStr = transfer.curData(attri).asInstanceOf[String]
        var foreigner: Object = null
        // 外键的代码是空的
        if (isNotEmpty(codeStr)) {
          val codeValue = if codeStr.contains(" ") then substringBefore(codeStr, " ") else codeStr
          var entity: Object = null
          if (multiEntity) {
            val shortName = substringBefore(attri, ".")
            entity = transfer.asInstanceOf[MultiEntityImporter].getCurrent(shortName)
          } else {
            entity = transfer.current
          }

          val attr = substringAfter(attri, ".")
          var nestedForeigner = Properties.get[Object](entity, substring(attr, 0, attr.lastIndexOf(".")))
          if (nestedForeigner.isInstanceOf[Option[_]]) {
            nestedForeigner = nestedForeigner.asInstanceOf[Option[AnyRef]].orNull
          }
          nestedForeigner match {
            case _: Entity[_] =>
              val className = nestedForeigner.getClass.getName
              val foreignerMap = foreignersMap.getOrElseUpdate(className, new collection.mutable.HashMap[String, Object])
              if (foreignerMap.size > CACHE_SIZE) foreignerMap.clear()
              foreigner = foreignerMap.get(codeValue).orNull
              if (foreigner == null) {
                val clazz = nestedForeigner.getClass.asInstanceOf[Class[Entity[_]]]
                val query = OqlBuilder.from(clazz, "f")
                query.where(foreignerKeys.map(k => s"f.$k = :fk_value").mkString(" or "), codeValue)
                val foreigners = entityDao.search(query)
                if (foreigners.nonEmpty) {
                  foreigner = foreigners.head
                  foreignerMap.put(codeValue, foreigner)
                } else {
                  tr.addFailure("代码不存在", codeValue)
                }
              }
            case _ =>
          }
          val parentAttr = substring(attr, 0, attr.lastIndexOf("."))
          val entityImporter = transfer.asInstanceOf[MultiEntityImporter]
          entityImporter.populator.populate(entity.asInstanceOf[Entity[_]], entityImporter.domain.getEntity(entity.getClass).get, parentAttr, foreigner)
        }
      }
    }
  }

  def addForeigerKey(key: String): Unit = {
    this.foreignerKeys += key
  }

}
