/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.orm.hibernate

import org.beangle.commons.bean.ProxyResolver
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy

object HibernateProxyResolver extends ProxyResolver {

  override def isProxy(obj: AnyRef): Boolean = {
    classOf[HibernateProxy].isAssignableFrom(obj.getClass)
  }

  def targetClass(obj: AnyRef): Class[_] = {
    obj match {
      case proxy: HibernateProxy => proxy.getHibernateLazyInitializer.getPersistentClass
      case _ => obj.getClass
    }
  }

  def unproxy(obj: AnyRef): AnyRef = {
    if isProxy(obj) then Hibernate.unproxy(obj) else obj
  }
}
