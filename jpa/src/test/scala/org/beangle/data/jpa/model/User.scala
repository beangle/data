package org.beangle.data.jpa.model

class User(var id: Long) {
  def this() = this(0)
  var name: String = _
  var role2s: collection.mutable.Seq[Role] = new collection.mutable.ListBuffer[Role]
  var roles: collection.mutable.Set[Role] = new collection.mutable.HashSet[Role]
  var age: Option[Int] = None
  var properties: collection.mutable.Map[String, String] = _
//  var properties: java.util.Map[String, String] = _
}