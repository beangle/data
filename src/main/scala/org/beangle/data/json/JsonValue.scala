package org.beangle.data.json

import org.beangle.commons.json.{JsonArray, JsonObject}
import org.beangle.commons.lang.annotation.value

object JsonValue {
  var Empty = new JsonValue("")

  def apply(datas: collection.Map[String, Any]): JsonValue = {
    if (datas.isEmpty) Empty else new JsonValue(new JsonObject(datas).toJson)
  }

  def apply(value: JsonObject): JsonValue = {
    new JsonValue(value.toJson)
  }

  def apply(value: JsonArray): JsonValue = {
    new JsonValue(value.toJson)
  }
}

@value
class JsonValue(val value: String) extends Serializable {

}
