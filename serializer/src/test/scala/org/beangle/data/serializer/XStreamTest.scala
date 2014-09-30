package org.beangle.data.serializer

import org.beangle.data.serializer.io.xml.DomDriver
import java.io.File

class XStreamTest {

}

class Person(var code: String, var name: String) {
  var address = Address("minzu", "500", "jiading")
  var mobile: String = _
  var addresses = List(Address("minzu", "500", "jiading"), Address("minzu2", "5002", "jiading2"))
}

trait Addressable {
  val name: String
  val street: String
  val city: String
}

case class Address(name: String, street: String, city: String) extends Addressable

object XStreamTest {

  def main(args: Array[String]) {
    val clazz = classOf[Address]
    println(clazz.getSuperclass())

    //val serializer = new MarshallingSerializer 
    //    serializer.marshaller =  
    //    val xstream = XStreamConversions(new XStream(new DomDriver()))
    // make Topping and Pizza lowercase
    //    val xstream = new XStream(new JsonHierarchicalStreamDriver)
    val xstream = XStream(new DomDriver)
    //xstream.alias("person", classOf[Person])
    //xstream.alias("address", classOf[Address])
        xstream.toXML(List(new Person("002", "admin2"), new Person("001", "admin")), new java.io.FileOutputStream(new File("/tmp/b.xml")))
//    xstream.toXML(new Person("002", "admin2"), new java.io.FileOutputStream(new File("/tmp/b.xml")))
  }
}