package org.beangle.data.jdbc.vendor

object Driver {
  def apply(className: String, prefix: String, formats: String*): DriverInfo = {
    new DriverInfo(className, formats)
  }
}

class DriverInfo(val className: String, val urlformats: Seq[String]) {
}