package org.beangle.data.jpa.hibernate

import java.io.Serializable;
import org.hibernate.EmptyInterceptor
class TestInterceptor extends EmptyInterceptor {
  override def onPrepareStatement(sql: String): String = {
    sql
  }
}