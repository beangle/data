package org.beangle.data.jpa.model

import org.beangle.data.model.{ Named, YearId }
import org.beangle.data.model.bean.{ IntIdBean, LongIdBean }

class LongIdResource extends LongIdBean with Named {

}

class LongDateIdResource extends LongIdBean with Named with YearId {

  var year: Int = _
}

class IntIdResource extends IntIdBean with Named with YearId {

  var year: Int = _
}