package org.beangle.data.serializer.model

import java.math.BigInteger
import java.{util => ju}
class Person(var code: String, var name: String) {
  var address = Address("minzu", "500", "jiading")
  var mobile: String = _
  var skills = List(new Skill("Play Basketball Best"), new Skill("Play football"))
  val bestSkill = Some(skills.head)
  val badestSkill = None
  var accountMoney1: BigInt = new BigInt(new BigInteger("1234567890"))
  var accountMoney2: BigDecimal = new java.math.BigDecimal("12243434.23")
  var birthday = new ju.Date()
  var joinOn = new java.sql.Date(System.currentTimeMillis())
  var updatedAt = ju.Calendar.getInstance()
  var createdAt = new java.sql.Timestamp(System.currentTimeMillis)
}

trait Addressable {
  val name: String
  val street: String
  val city: String
}

class Skill(val name: String) {

  def excellent: Boolean = name.contains("Best")
}

case class Address(name: String, street: String, city: String) extends Addressable
