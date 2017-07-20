package org.beangle.data.orm

import org.beangle.data.model.LongId

class UserProperty extends LongId {
  var user: TestUser = _
  var name: String = _
  var value: String = _
}