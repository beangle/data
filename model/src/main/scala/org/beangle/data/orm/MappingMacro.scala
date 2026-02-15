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

import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.reflect.{BeanInfoDigger, BeanInfos}
import org.beangle.data.orm.MappingModule.{EntityHolder, Target}

import scala.quoted.{Expr, Quotes, Type}

object MappingMacro {

  def castImpl[T:Type] (pm: Expr[OrmProperty], holder: Expr[EntityHolder[_]], msg: Expr[String])(implicit quotes: Quotes): Expr[T] = {
    import quotes.reflect.*
    val tpr = quotes.reflect.TypeRepr.of[T]
    val clzz = Literal(ClassOfConstant(tpr)).asExpr.asInstanceOf[Expr[Class[T]]]
    '{
      if (!${clzz}.isAssignableFrom(${pm}.getClass)) mismatch(${msg}, ${holder}.mapping, ${pm})
      ${pm}.asInstanceOf[T]
    }
  }

  def mismatch(msg: String, e: OrmEntityType, pm: OrmProperty): Unit = {
    throw new RuntimeException(msg + s",Not for ${e.entityName}.${pm.name}(${pm.getClass.getSimpleName}/${pm.clazz.getName})")
  }

  def target[T:Type] (implicit quotes: Quotes):Expr[Target]={
    import quotes.reflect.*
    val tpr = quotes.reflect.TypeRepr.of[T]
    '{new Target(${Literal(ClassOfConstant(tpr)).asExpr.asInstanceOf[Expr[Class[T]]]})}
  }

  def collection[T:Type](properties:Expr[Seq[String]]) (implicit quotes: Quotes):Expr[List[Collection]]={
    import quotes.reflect.*
    val tpr = quotes.reflect.TypeRepr.of[T]
    val clzz = Literal(ClassOfConstant(tpr)).asExpr.asInstanceOf[Expr[Class[T]]]
    '{
      val definitions = new scala.collection.mutable.ListBuffer[Collection]
      ${properties} foreach (p => definitions += new Collection(${clzz}, p))
      definitions.toList
    }
  }

  def bind[T:Type](entityName: Expr[String], module:Expr[MappingModule]) (implicit quotes: Quotes):Expr[EntityHolder[T]]={
    import quotes.reflect.*
    val tpr = quotes.reflect.TypeRepr.of[T]
    val digger = new BeanInfoDigger[quotes.type](tpr)
    val clzz = Literal(ClassOfConstant(tpr)).asExpr.asInstanceOf[Expr[Class[T]]]
    '{
      val bi = BeanInfos.cache.update(${digger.dig()})
      if(Strings.isBlank(${entityName})){
        ${module}.bindImpl(${clzz},${clzz}.getName,bi)
      }else{
        ${module}.bindImpl(${clzz},${entityName},bi)
      }
    }
  }
}
