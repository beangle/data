package org.beangle.data.serializer.marshal.converter

import org.beangle.data.serializer.marshal.Converter
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.marshal.MarshallingContext
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.commons.lang.Primitives

class BeanConverter(val mapper: Mapper) extends Converter[Object] {

  def marshal(source: Object, writer: StreamWriter, context: MarshallingContext): Unit = {
    val sourceType = source.getClass
    if (sourceType.getName().startsWith("java.lang.")) {
      writer.setValue(source.toString)
      //      throw new RuntimeException("BeanMarshaller Cannot accept primary")
    } else {
      val classAttributeName = mapper.aliasForSystemAttribute("class")
      BeanManifest.get(sourceType).getters foreach { getter =>
        val propertyName = getter._1
        val newObj = getter._2.method.invoke(source)
        if (null != newObj) {
          val fieldType = getter._2.returnType
          val actualType = newObj.getClass();
          val defaultType = mapper.defaultImplementationOf(fieldType);
          val serializedMember = mapper.serializedMember(source.getClass(), propertyName);
          writer.startNode(serializedMember, actualType)
          if (!actualType.equals(defaultType) && classAttributeName != null) {
            writer.addAttribute(classAttributeName, mapper.serializedClass(actualType));
          }
          context.convert(newObj, writer)
          writer.endNode()
        }
      }
    }
  }
}