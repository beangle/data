package org.beangle.data.jdbc.dialect

case class Name(value: String, quoted: Boolean = false) extends Ordered[Name] {

  def toLowerCase(): Name = {
    new Name(value.toLowerCase(), quoted)
  }

  override def toString: String = {
    if (quoted) "`" + value + "`"
    else value
  }

  override def compare(other: Name): Int = {
    value.compareTo(other.value)
  }

  override def equals(other: Any): Boolean = {
    other match {
      case n: Name => n.value == this.value
      case _ => false
    }
  }
  override def hashCode: Int = {
    value.hashCode()
  }

  def attach(dialect: Dialect): Name = {
    val needQuote = dialect.needQuoted(value)
    if (needQuote != quoted) Name(value, needQuote)
    else this
  }

  def qualified(dialect: Dialect): String = {
    if (quoted) dialect.openQuote + value + dialect.closeQuote
    else value
  }
}