/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.transfer.importer

import org.beangle.data.model.meta.EntityType

object DefaultEntityImporter {

  val alias = "_entity";
}

/**
 * DefaultEntityImporter
 *
 * @author chaostone
 */
class DefaultEntityImporter(val entityClass: Class[_]) extends MultiEntityImporter {

  import DefaultEntityImporter._

  this.prepare = EntityPrepare

  protected override def getEntityType(attr: String): EntityType = {
    return entityTypes(alias);
  }

  def getEntityClass: Class[_] = {
    return entityTypes(alias).clazz
  }

  def getEntityName(): String = {
    return entityTypes(alias).entityName
  }

  override def getCurrent(attr: String): AnyRef = {
    current
  }

  override def current: AnyRef = {
    super.getCurrent(alias);
  }

  protected override def getEntityName(attr: String): String = {
    getEntityName()
  }

  override def processAttr(attr: String): String = {
    attr
  }

  override def current_=(obj: AnyRef) {
    currentEntities.put(alias, obj);
  }

}
