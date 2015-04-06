package org.beangle.data.jdbc.dialect

case class Name(value: String, quoted: Boolean = false) extends Ordered[Name] {

  def toLowerCase(): Name = {
    new Name(value.toLowerCase(), quoted)
  }

  override def toString: String = {
    value
  }

  override def compare(other: Name): Int = {
    value.compareTo(other.value)
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