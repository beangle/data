/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
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
package org.beangle.data.conversion.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.SynchronizedBuffer

import org.beangle.commons.collection.page.PageLimit
import org.beangle.commons.lang.ThreadTasks
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.conversion.Converter
import org.beangle.data.conversion.DataWrapper
import org.beangle.data.jdbc.meta.Table

class DataConverter(val source: DataWrapper, val target: DataWrapper, val threads: Int = 5) extends Converter with Logging {

  val tables = new ListBuffer[Pair[Table, Table]]

  protected def addTable(pair: Pair[Table, Table]) {
    tables += pair
  }

  def addAll(pairs: Seq[Pair[Table, Table]]) {
    tables ++= pairs
  }

  def reset() {
  }

  def start() {
    val watch = new Stopwatch(true)
    val tableCount = tables.length
    val buffer = new ArrayBuffer[Pair[Table, Table]] with SynchronizedBuffer[Pair[Table, Table]]
    buffer ++= tables.sortWith(_._1.name > _._1.name)
    logger.info("Start {} tables data replication in {} threads...", tableCount, threads)
    ThreadTasks.start(new ConvertTask(source, target, buffer), threads)
    logger.info("End {} tables data replication,using {}", tableCount, watch)
  }

  class ConvertTask(val source: DataWrapper, val target: DataWrapper, val buffer: Buffer[Pair[Table, Table]]) extends Runnable {

    def run() {
      while (!buffer.isEmpty) {
        try {
          convert(buffer.remove(0))
        } catch {
          case e: IndexOutOfBoundsException =>
          case e: Exception => logger.error("Error in convertion ", e)
        }
      }
    }

    private def createOrReplaceTable(table: Table): Boolean = {
      if (target.drop(table)) {
        if (target.create(table)) {
          logger.info("Create table {}", table.name)
          return true
        } else {
          logger.error("Create table {} failure.", table.name)
        }
      }
      false
    }

    def convert(pair: Pair[Table, Table]) {
      val srcTable = pair._1
      val targetTable = pair._2
      try {
        if (!createOrReplaceTable(targetTable)) return
        var count = source.count(srcTable)
        if (count == 0) {
          target.save(targetTable, List.empty)
          logger.info("Insert {}(0)", targetTable)
        } else {
          var curr = 0
          var pageNo = 0
          while (curr < count) {
            val limit = new PageLimit(pageNo + 1, 1000)
            val data = if (source.supportLimit) source.get(srcTable, limit) else source.get(srcTable)
            if (data.isEmpty) {
              logger.error("Failure in fetching {} data {}({})", Array(srcTable.name, limit.pageNo, limit.pageSize).asInstanceOf[Array[Object]])
            }
            val successed = target.save(targetTable, data)
            curr += data.size
            pageNo += 1
            if (successed == count) {
              logger.info("Insert {}({})", targetTable, successed)
            } else if (successed == data.size) {
              logger.info("Insert {}({}/{})", Array(targetTable, curr, count).asInstanceOf[Array[Object]])
            } else {
              logger.warn("Insert {}({}/{})", Array(targetTable, successed, data.size).asInstanceOf[Array[Object]])
            }
          }
        }
      } catch {
        case e: Exception =>
          logger.error("Insert error " + srcTable.identifier, e)
      }
    }
  }
}