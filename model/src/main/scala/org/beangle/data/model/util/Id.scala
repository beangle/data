package org.beangle.data.model.util

import org.beangle.commons.lang.functor.NotZero
import org.beangle.commons.lang.functor.NotEmpty

/**
 * @author chaostone
 */
object Id {

  def isValid(value: Any): Boolean = {
    if (null == value) return false
    if (value.isInstanceOf[Number]) return NotZero(value.asInstanceOf[Number])
    return NotEmpty(value.toString)
  }
}