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

package org.beangle.data.jdbc.script

import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec

class ParserTest extends AnyFunSpec with Matchers {
  describe("Oracle Parser") {
    it("parse prompt") {
      val list = OracleParser.parse(
        """prompt 开始安装存储过程
set feedback off
drop package fk_util;
drop package seq_util;
drop package string_util;
""")
      list.size should equal(5)
      list(1) should equal("set feedback off")
    }

    it("parse package") {
      val states = OracleParser.parse(
        """prompt 安装更新sequence起始值的脚本...

CREATE OR REPLACE package SEQ_UTIL
IS
        PROCEDURE update_sequence(v_table_name varchar2, v_seq_name varchar2);
END SEQ_UTIL;
/""")
      for (l <- states) println(l)
      states.size should equal(2)
      states(0) should equal("prompt 安装更新sequence起始值的脚本...")
    }
  }
}
