package org.beangle.data.hibernate.model

import org.beangle.data.model.LongId
import org.beangle.data.model.Named

/**
 * @author chaostone
 */
class Profile extends LongId with Named {

  var user: User = _

}