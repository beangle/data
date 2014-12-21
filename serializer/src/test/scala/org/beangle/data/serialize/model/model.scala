package org.beangle.data.serialize.model

import java.math.BigInteger
import java.{ util => ju }
import java.net.URL
class Person(var code: String, var name: String) {
  var address = Address("minzu", "500", "jiading")
  var mobile: String = _
  var skills = List(new Skill("Play Basketball Best"), new Skill("Play football"))
  var skillsArray = Array(new Skill("Play Basketball Best"), new Skill("Play football"))
  val bestSkill = Some(skills.head)
  val badestSkill = None
  var accountMoney1: BigInt = new BigInt(new BigInteger("1234567890"))
  var accountMoney2: BigDecimal = new java.math.BigDecimal("12243434.23")
  var birthday = new ju.Date()
  var joinOn = new java.sql.Date(System.currentTimeMillis())
  var updatedAt = ju.Calendar.getInstance()
  var createdAt = new java.sql.Timestamp(System.currentTimeMillis)
  val locale = ju.Locale.SIMPLIFIED_CHINESE
  val homepage = new URL("http://www.some.com/info")
  var birthAt = java.sql.Time.valueOf("23:23:23")
  var remark = """
                A very famous Basketball Player, and
                so ... & <>"""
  var families = Map("wife" -> "a girl", "daught" -> "ketty")

  val sidekick = this
}

trait Addressable {
  val name: String
  val street: String
  val city: String
}

class Member {
  var families = new ju.HashMap[String, String]
  families.put("wife", "a girl")
  families.put("daught", "ketty")
}
class Skill(val name: String) {

  def excellent: Boolean = name.contains("Best")
}

case class Address(name: String, street: String, city: String) extends Addressable
