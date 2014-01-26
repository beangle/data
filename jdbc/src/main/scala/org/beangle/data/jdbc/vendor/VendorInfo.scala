package org.beangle.data.jdbc.vendor

object Vendor {
  def apply(name: String, drivers: DriverInfo*): VendorInfo = {
    new VendorInfo(name, drivers)
  }
}

class VendorInfo(val name: String, val drivers: Seq[DriverInfo]) {

}

