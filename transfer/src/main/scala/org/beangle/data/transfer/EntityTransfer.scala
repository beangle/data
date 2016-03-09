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

import java.util.Set
import org.beangle.data.model.util.Populator
import org.beangle.data.transfer.io.ItemReader
import org.beangle.data.model.meta.EntityMetadata

/**
 * EntityImporter interface.
 * 
 * @author chaostone
 */
trait EntityTransfer extends Transfer {

  def foreignerKeys:collection.Set[String]

  def addForeignedKeys( foreignerKey:String)

  var populator :Populator=_
  
  var entityMetadata:EntityMetadata=_

}
