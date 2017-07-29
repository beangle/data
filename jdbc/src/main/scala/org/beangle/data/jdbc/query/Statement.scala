package org.beangle.data.jdbc.query

import java.sql.PreparedStatement

class Statement(sql: String, executor: JdbcExecutor) {

  private var setter: PreparedStatement => Unit = _

  def prepare(setter: PreparedStatement => Unit): this.type = {
    this.setter = setter
    this
  }

  def query(): Seq[Array[Any]] = {
    executor.query(sql, setter)
  }

  def execute(): Int = {
    executor.update(sql, setter)
  }
}