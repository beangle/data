package org.beangle.data.jdbc.engine

import org.scalatest.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class SQLServerTest extends AnyFlatSpec with Matchers {
  var engine = Engines.SQLServer
  val rs1 = engine.limit("select * from users", 0, 10)
  val rs2 = engine.limit("select * from users order by name", 0, 10)
  val rs3 = engine.limit("select * FROM users order by name", 10, 10)

  println(rs1)
  println(rs2)
  println(rs3)
}
