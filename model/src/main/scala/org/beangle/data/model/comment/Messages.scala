/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.model.comment

import java.util.Locale
import org.beangle.commons.text.i18n.TextBundleRegistry
import org.beangle.commons.text.i18n.TextFormater
import org.beangle.commons.text.i18n.DefaultTextFormater
import org.beangle.commons.text.i18n.DefaultTextBundleRegistry
import org.beangle.commons.text.i18n.HierarchicalTextResource

object Messages {
  def build(locale: Locale): Messages = {
    new Messages(locale, new DefaultTextBundleRegistry(), new DefaultTextFormater())
  }

}
class Messages(locale: Locale, registry: TextBundleRegistry, format: TextFormater) {
  registry.reloadable = false

  def get(clazz: Class[_], key: String): String = {
    new HierarchicalTextResource(clazz, locale, registry, format)(key).orNull
  }
}
