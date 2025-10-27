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

package org.beangle.data.dao

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.jar.asm.{MethodVisitor, Opcodes}

private[dao] object ByteBuddyHelper {

  def path(clazz: Class[_]): String = {
    clazz.getName.replace('.', '/')
  }

  def notation(clazz: Class[_]): String = {
    if (clazz == classOf[Unit]) {
      "V"
    } else if (clazz.isPrimitive) {
      if clazz == classOf[Byte] then "B"
      else if clazz == classOf[Short] then "S"
      else if clazz == classOf[Char] then "C"
      else if clazz == classOf[Int] then "I"
      else if clazz == classOf[Float] then "F"
      else if clazz == classOf[Double] then "D"
      else if clazz == classOf[Boolean] then "Z"
      else throw IllegalArgumentException(s"Cannot find primative ${clazz.getName}'s notation.")
    } else if (clazz.isArray) {
      "[" + notation(clazz.getComponentType)
    } else {
      "L" + clazz.getName.replace('.', '/') + ";"
    }
  }

  def appendLiteralToStack(mv: MethodVisitor, md: MethodDescription): Unit = {
    val rt = md.getReturnType
    if (rt.isPrimitive) {
      val primitiveType = rt.getTypeName
      primitiveType match {
        case "boolean" => mv.visitInsn(Opcodes.ICONST_0) // boolean 实际用 int 表示（0=false, 1=true）
        case "byte" => mv.visitIntInsn(Opcodes.BIPUSH, 0.asInstanceOf[Byte])
        case "char" => mv.visitIntInsn(Opcodes.SIPUSH, 0.asInstanceOf[Char])
        case "short" => mv.visitIntInsn(Opcodes.SIPUSH, 0.asInstanceOf[Short])
        case "int" => mv.visitInsn(Opcodes.ICONST_0)
        case "long" => mv.visitLdcInsn(0L)
        case "float" => mv.visitLdcInsn(0f)
        case "double" => mv.visitLdcInsn(0d)
        case _ => throw new IllegalArgumentException("不支持的基本类型: " + primitiveType)
      }
    } else {
      mv.visitInsn(Opcodes.ACONST_NULL)
    }
  }

  def getReturnOpcode(m: MethodDescription): Int = {
    val rt = m.getReturnType
    if (rt.represents(classOf[Unit])) {
      Opcodes.RETURN
    } else if (!rt.isPrimitive) {
      // 引用类型（包括包装类、自定义类等）
      Opcodes.ARETURN
    } else {
      // 基础类型（根据具体类型判断）
      if (rt.represents(classOf[Int]) ||
        rt.represents(classOf[Boolean]) ||
        rt.represents(classOf[Byte]) ||
        rt.represents(classOf[Char]) ||
        rt.represents(classOf[Short])) {
        Opcodes.IRETURN
      } else if (rt.represents(classOf[Long])) {
        Opcodes.LRETURN;
      } else if (rt.represents(classOf[Float])) {
        Opcodes.FRETURN;
      } else if (rt.represents(classOf[Double])) {
        Opcodes.DRETURN;
      } else {
        throw new IllegalArgumentException("未知的基础类型: " + rt);
      }
    }
  }
}
