package org.beangle.data.jpa.model

import org.beangle.data.model.{ Coded, Named }
import org.beangle.data.model.annotation.code
import org.beangle.data.model.bean.IntIdBean

@code("school")
class IdType extends IntIdBean with Coded with Named {

}