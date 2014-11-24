package org.beangle.data.jpa.model

import org.beangle.data.model.bean.LongIdBean
import org.beangle.data.model.bean.IntIdBean
import org.beangle.data.model.bean.NamedBean
import org.beangle.data.model.YearId

class LongIdResource extends LongIdBean with NamedBean {

}

class LongDateIdResource extends LongIdBean with NamedBean with YearId {

  var year: Int = _
}

class IntIdResource extends IntIdBean with NamedBean with YearId {

  var year: Int = _
}