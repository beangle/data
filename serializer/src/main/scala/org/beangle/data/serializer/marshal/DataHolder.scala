package org.beangle.data.serializer.marshal

trait DataHolder {

  def get(key: Object): Object
  def put(key: Object, value: Object);
  def keys(): Iterator[AnyRef]
}

class DefaultDataHolder(map: collection.mutable.Map[AnyRef, AnyRef] = new collection.mutable.HashMap[AnyRef, AnyRef]) extends DataHolder {

  override def get(key: AnyRef): AnyRef = {
    map.getOrElse(key, null)
  }
  def put(key: AnyRef, value: AnyRef): Unit = {
    map.put(key, value)
  }
  def keys(): Iterator[AnyRef] = {
    map.keysIterator
  }
}