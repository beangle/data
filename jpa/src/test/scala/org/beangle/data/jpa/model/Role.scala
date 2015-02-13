package org.beangle.data.jpa.model

import org.beangle.data.model.Entity

class Role(var id: java.lang.Integer) extends Entity[java.lang.Integer] {
  def this() = this(0)
  var name: String = _
}