/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.transfer

import org.beangle.commons.model.meta.EntityType

object DefaultEntityTransfer {

  val alias = "_entity";
}

/**
 * DefaultEntityImporter class.
 *
 * @author chaostone
 */
class DefaultEntityTransfer(val entityClass: Class[_]) extends MultiEntityTransfer {

  import DefaultEntityTransfer._

  this.prepare = EntityPrepare

  protected override def getEntityType(attr: String): EntityType = {
    return entityTypes(alias);
  }

  def getEntityClass: Class[_] = {
    return entityTypes(alias).entityClass
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
