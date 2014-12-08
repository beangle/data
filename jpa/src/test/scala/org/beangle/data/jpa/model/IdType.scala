package org.beangle.data.jpa.model

import org.beangle.data.model.annotation.code
import org.beangle.data.model.bean.{ CodedBean, IntIdBean, NamedBean }

@code("school")
class IdType extends IntIdBean with CodedBean with NamedBean {

}