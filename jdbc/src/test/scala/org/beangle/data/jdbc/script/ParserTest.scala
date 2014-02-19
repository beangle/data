package org.beangle.data.jdbc.script;
import org.scalatest.FunSpec
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class ParserTest extends FunSpec with Matchers {
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
