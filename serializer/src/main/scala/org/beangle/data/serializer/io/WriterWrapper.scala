package org.beangle.data.serializer.io

class WriterWrapper(val wrapped: StreamWriter) extends StreamWriter {

  override def startNode(name: String, clazz: Class[_]): Unit = {
    wrapped.startNode(name, clazz)
  }

  override def endNode(): Unit = {
    wrapped.endNode();
  }

  override def addAttribute(key: String, value: String): Unit = {
    wrapped.addAttribute(key, value)
  }

  override def setValue(text: String): Unit = {
    wrapped.setValue(text);
  }

  override def flush(): Unit = {
    wrapped.flush();
  }

  override def close(): Unit = {
    wrapped.close();
  }

  override def underlying(): StreamWriter = {
    wrapped.underlying
  }

}
