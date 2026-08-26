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

package org.beangle.data.samples.nativeapp

import org.beangle.data.dao.OqlBuilder
import org.beangle.data.hibernate.HibernateEntityDao
import org.beangle.data.hibernate.LocalSessionFactoryBean

/** GraalVM native-image integration test application.
 *
 * Performs Hibernate CRUD operations and prints SQL output.
 * Usage: java -cp ... org.beangle.data.samples.nativeapp.NativeApp
 */
object NativeApp {

  def main(args: Array[String]): Unit = {
    try {
      doMain(args)
    } catch {
      case e: Throwable =>
        System.err.println(s"FATAL: ${e.getClass.getName}: ${e.getMessage}")
        e.printStackTrace()
    }
  }

  private def doMain(args: Array[String]): Unit = {
    // Set ByteBuddy property BEFORE any Hibernate/ByteBuddy code runs (GraalVM native-image compatible)
    System.setProperty("net.bytebuddy.reproducible", "true")

    println("=== Beangle Data Hibernate Native-Image Test ===")
    println()

    // 1. Setup H2 in-memory database (direct, no HikariCP to avoid version issues)
    val jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    val ds = new org.h2.jdbcx.JdbcDataSource()
    ds.setURL(jdbcUrl)
    ds.setUser("sa")
    ds.setPassword("")

    // 2. Build Hibernate SessionFactory
    val builder = new LocalSessionFactoryBean(ds)
    builder.ormLocation = "classpath*:beangle.xml"
    builder.properties.put("hibernate.show_sql", "true")
    builder.properties.put("hibernate.hbm2ddl.auto", "create")
    builder.properties.put("hibernate.cache.use_second_level_cache", "true")
    builder.properties.put("hibernate.javax.cache.provider", "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider")
    try {
      builder.init()
    } catch {
      case e: Exception =>
        System.err.println(s"BUILDER INIT FAILED: ${e.getClass.getName}: ${e.getMessage}")
        e.printStackTrace()
        return
    }
    val sf = builder.getObject

    // 3. Create DAO
    val entityDao = new HibernateEntityDao(sf)
    entityDao.init()

    val session = sf.getCurrentSession
    val tx = session.beginTransaction()

    try {
      // 4. INSERT
      println("--- INSERT ---")
      val engineering = new Department
      engineering.code = "ENG"
      engineering.name = "Engineering"
      engineering.updatedAt = java.time.Instant.now()
      entityDao.saveOrUpdate(engineering)

      val hr = new Department
      hr.code = "HR"
      hr.name = "Human Resources"
      hr.updatedAt = java.time.Instant.now()
      entityDao.saveOrUpdate(hr)

      session.flush()
      println()

      // 5. UPDATE
      println("--- UPDATE ---")
      engineering.name = "Engineering Department"
      engineering.updatedAt = java.time.Instant.now()
      entityDao.saveOrUpdate(engineering)
      session.flush()
      println()

      // 6. SELECT (OQL query)
      println("--- SELECT (OQL) ---")
      val query = OqlBuilder.from(classOf[Department], "d")
      query.where("d.code like :code", "E%")
      val depts = entityDao.search(query)
      println(s"Found ${depts.size} department(s) with code starting with 'E':")
      depts.foreach(d => println(s"  [${d.code}] ${d.name} (id=${d.id})"))
      println()

      // 7. SELECT with native SQL
      println("--- SELECT (Criteria) ---")
      val all = OqlBuilder.from(classOf[Department], "d")
      all.orderBy("d.code")
      val allDepts = entityDao.search(all)
      println(s"All departments (${allDepts.size}):")
      allDepts.foreach(d => println(s"  [${d.code}] ${d.name} (id=${d.id})"))
      println()

      // 8. DELETE
      println("--- DELETE ---")
      entityDao.remove(hr)
      session.flush()

      val remaining = entityDao.search(OqlBuilder.from(classOf[Department], "d"))
      println(s"After delete: ${remaining.size} department(s) remaining")
      println()

      tx.commit()
      println("=== All operations completed successfully ===")
    } catch {
      case e: Exception =>
        tx.rollback()
        println(s"ERROR: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    } finally {
      sf.close()
    }
  }
}
