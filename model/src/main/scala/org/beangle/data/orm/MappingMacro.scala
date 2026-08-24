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

package org.beangle.data.orm

import org.beangle.commons.bean.meta.{MetaDigger, MetaModel}
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.reflect.{BeanInfo, BeanInfos}
import org.beangle.data.orm.MappingModule.EntityHolder

import scala.quoted.{Expr, Quotes, Type}

object MappingMacro {

  def mismatch(msg: String, e: OrmEntityType, pm: OrmProperty): Unit = {
    throw new RuntimeException(msg + s",Not for ${e.entityName}.${pm.name}(${pm.getClass.getSimpleName}/${pm.clazz.getName})")
  }

  /** Macro: registers ClassMeta via MetaRegistry, guards bindImpl for null mappings.
    * When mappings is set (normal binding), builds BeanInfo from compile-time digged ClassMeta
    * to preserve generic type precision (e.g. Long instead of Object), and caches it
    * so that downstream code (e.g. genOwnerColumn) can find it via BeanInfos.get.
    */
  def bind[T:Type](entityName: Expr[String], module: Expr[MappingModule])(implicit quotes: Quotes): Expr[EntityHolder[T]] = {
    import quotes.reflect.*
    val tpr = quotes.reflect.TypeRepr.of[T]
    val clzz = Literal(ClassOfConstant(tpr)).asExpr.asInstanceOf[Expr[Class[T]]]
    val cm = new MetaDigger[quotes.type](tpr).dig()
    '{
      val m = ${ module }.mappings
      if m == null then
        ${ module }.addMetas(Seq(${ cm }))
        null.asInstanceOf[EntityHolder[T]]
      else
        val bi = BeanInfos.update(BeanInfo.from(${ cm }))
        if Strings.isBlank(${ entityName }) then
          ${ module }.bindImpl(${ clzz }, ${ clzz }.getName, bi)
        else
          ${ module }.bindImpl(${ clzz }, ${ entityName }, bi)
    }
  }
}
