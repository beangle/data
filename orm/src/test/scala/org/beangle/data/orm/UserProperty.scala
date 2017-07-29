package org.beangle.data.orm

import org.beangle.data.model.LongId
import org.beangle.data.model.pojo.Named

class UserProperty extends LongId with Named {
  var user: TestUser = _
  var value: String = _
}