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

  val tables = new ListBuffer[Tuple2[Table, Table]]

  protected def addTable(pair: Tuple2[Table, Table]) {
    tables += pair
  }

  def addAll(pairs: Seq[Tuple2[Table, Table]]) {
    tables ++= pairs
  }

  def reset() {
  }

  def start() {
    val watch = new Stopwatch(true)
    val tableCount = tables.length
    val buffer = new ArrayBuffer[Tuple2[Table, Table]] with SynchronizedBuffer[Tuple2[Table, Table]]
    buffer ++= tables.sortWith(_._1.name > _._1.name)
    info(s"Start $tableCount tables data replication in $threads threads...")
    ThreadTasks.start(new ConvertTask(source, target, buffer), threads)
    info(s"End $tableCount tables data replication,using $watch")
  }

  class ConvertTask(val source: DataWrapper, val target: DataWrapper, val buffer: Buffer[Tuple2[Table, Table]]) extends Runnable {

    def run() {
      while (!buffer.isEmpty) {
        try {
          convert(buffer.remove(0))
        } catch {
          case e: IndexOutOfBoundsException =>
          case e: Exception => error("Error in convertion ", e)
        }
      }
    }

    private def createOrReplaceTable(table: Table): Boolean = {
      if (target.drop(table)) {
        if (target.create(table)) {
          info(s"Create table ${table.name}")
          return true
        } else {
          error(s"Create table ${table.name} failure.")
        }
      }
      false
    }

    def convert(pair: Tuple2[Table, Table]) {
      val srcTable = pair._1
      val targetTable = pair._2
      try {
        if (!createOrReplaceTable(targetTable)) return
        var count = source.count(srcTable)
        if (count == 0) {
          target.save(targetTable, List.empty)
          info(s"Insert $targetTable(0)")
        } else {
          var curr = 0
          var pageNo = 0
          while (curr < count) {
            val limit = new PageLimit(pageNo + 1, 1000)
            val data = if (source.supportLimit) source.get(srcTable, limit) else source.get(srcTable)
            if (data.isEmpty) {
              error(s"Failure in fetching ${srcTable.name} data ${limit.pageNo}(${limit.pageSize})")
            }
            val successed = target.save(targetTable, data)
            curr += data.size
            pageNo += 1
            if (successed == count) {
              info(s"Insert $targetTable($successed)")
            } else if (successed == data.size) {
              info(s"Insert $targetTable($curr/$count)")
            } else {
              warn(s"Insert $targetTable($successed/${data.size})")
            }
          }
        }
      } catch {
        case e: Exception => error(s"Insert error ${srcTable.identifier}", e)
      }
    }
  }
}