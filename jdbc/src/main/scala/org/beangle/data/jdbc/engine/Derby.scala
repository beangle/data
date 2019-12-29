package org.beangle.data.jdbc.engine

class Derby(v:String) extends DB2(v) {
  options.comment.supportsCommentOn = false

  override def name:String={
    "Derby"
  }
}
