package org.beangle.data.jpa.model

import org.beangle.commons.lang.time.WeekState
import org.beangle.data.model.Entity

class User(var id: java.lang.Long) extends Entity[java.lang.Long] {
  def this() = this(0)
  var name: Name = _
  var roleList: collection.mutable.Seq[Role] = new collection.mutable.ListBuffer[Role]
  var roleSet: collection.mutable.Set[Role] = new collection.mutable.HashSet[Role]
  var age: Option[Int] = None
  var properties: collection.mutable.Map[String, String] = _
  var occupy: WeekState = _
}

class Name {

  var first: String = _
  var last: String = _
}