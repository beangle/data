package org.beangle.data.orm

import org.beangle.data.model.LongId
import org.beangle.data.model.pojo.Updated

class AccessLog extends LongId, Updated {

  var resource: String = _
  var username: String = _
  var action: String = _
  var ip: String = _
  var params: String = _
  var userAgent: String = _
  var result: String = _
  var duration: Long = _
}
