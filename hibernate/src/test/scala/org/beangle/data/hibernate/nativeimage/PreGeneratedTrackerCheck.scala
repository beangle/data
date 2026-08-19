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

package org.beangle.data.hibernate.nativeimage

import org.beangle.data.dao.{AccessTracker, AccessTrackerGenerator}
import org.beangle.data.hibernate.model.User

/** 验证构建期预生成的 $Tracker 类机制：
 *
 * 1. 生成 tracker 类到输出目录；
 * 2. 检查 AccessTracker.generate 使用的是预生成类（classpath 上有则用，否则回退 ByteBuddy）；
 * 3. 验证预生成 tracker 的功能（属性访问记录）正常。
 */
object PreGeneratedTrackerCheck {

  def main(args: Array[String]): Unit = {
    val dir = new java.io.File(if (args.length > 0) args(0) else "target/pregen-trackers")
    val classes = AccessTrackerGenerator.generate("classpath*:beangle.xml", "PostgreSQL", dir)
    println(s"[check] generated ${classes.size} tracker classes to ${dir.getAbsolutePath}")

    // 清空缓存，确保 generate 走真实的加载路径（classpath 预生成类 or ByteBuddy）
    AccessTracker.cleanup()
    val trackerClazz = AccessTracker.generate(classOf[User])
    val codeSource = trackerClazz.getProtectionDomain.getCodeSource
    val preGenerated = codeSource != null && codeSource.getLocation != null &&
      codeSource.getLocation.toString.contains("pregen-trackers")
    println(s"[check] User tracker: ${trackerClazz.getName} loader=${trackerClazz.getClassLoader}")
    println(s"[check] code source: ${if (codeSource == null) "null" else codeSource.getLocation}")
    println(s"[check] pre-generated used: $preGenerated")

    val tracker = trackerClazz.getConstructor(classOf[AccessTracker.Context])
      .newInstance(new AccessTracker.Context()).asInstanceOf[User & AccessTracker]
    tracker.name
    tracker.age
    val accessed = tracker.ctx.accessed()
    println(s"[check] accessed properties: $accessed")
    require(accessed.contains("name") && accessed.contains("age"), "name/age should be tracked")
    println("[check] PRE-GENERATED TRACKER CHECK PASSED")
  }
}