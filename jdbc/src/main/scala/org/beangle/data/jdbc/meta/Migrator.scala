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
package org.beangle.data.jdbc.meta

import java.io.File

import org.beangle.commons.io.Files
import org.beangle.data.jdbc.meta.Serializer

object Migrator {

  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      println("Usage:Migrator database1.xml database2.xml /path/to/diff.sql")
      return
    }
    val db1 = Serializer.fromXml(Files.readString(new File(args(0))))
    val db2 = Serializer.fromXml(Files.readString(new File(args(1))))
    val diff = Diff.diff(db1, db2)
    val sqls = Diff.sql(diff)
    Files.writeString(new File(args(2)), sqls.toBuffer.sorted.append("").mkString(";\n"))
  }

}