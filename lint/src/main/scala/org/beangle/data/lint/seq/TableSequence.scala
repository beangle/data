package org.beangle.data.lint.seq

class TableSequence {

  var seqName: String = _

  var tableName: String = _

  var idColumnName: String = "id"

  var lastNumber: Long = _

  var maxId: Long = _

  override def toString(): String = {
    val buffer = new StringBuilder();
    buffer.append(seqName).append('(').append(lastNumber).append(')');
    buffer.append("  ");
    if (null == tableName) {
      buffer.append("----");
    } else {
      buffer.append(tableName).append('(').append(maxId).append(')');
    }
    return buffer.toString();
  }
}