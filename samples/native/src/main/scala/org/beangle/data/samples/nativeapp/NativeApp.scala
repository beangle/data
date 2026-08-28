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
import org.beangle.commons.collection.page.PageLimit
import org.beangle.commons.lang.math.Decimal5

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
    builder.properties.put("hibernate.cache.use_query_cache", "true")
    builder.properties.put("hibernate.cache.region.factory_class", "jcache")
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

      // 9. EXTENDED: associations, collections, enum, UDT, code-style id
      println("--- EMPLOYEE / ROLE (associations + collections + enum + UDT) ---")
      val eng = entityDao.get(classOf[Department], engineering.id)
      val roleAdmin = new Role
      roleAdmin.code = "ADMIN"
      roleAdmin.name = "Admin"
      val roleDev = new Role
      roleDev.code = "DEV"
      roleDev.name = "Developer"
      val alice = new Employee
      alice.code = "E001"
      alice.name = "Alice"
      alice.department = eng
      alice.boss = None
      alice.level = EmpLevel.Senior
      alice.salary = Some(Decimal5.of("123.45"))
      alice.roles += roleAdmin
      roleAdmin.employee = alice
      alice.roles += roleDev
      roleDev.employee = alice
      alice.tags.put("city", "BeiJing")
      alice.updatedAt = java.time.Instant.now()
      entityDao.saveOrUpdate(alice)
      session.flush()
      println()

      // 10. lazy proxy + collection init
      println("--- LAZY PROXY + COLLECTION ---")
      val fresh = entityDao.get(classOf[Employee], alice.id)
      println(s"  dept via lazy proxy: ${fresh.department.name}")
      println(s"  boss: ${fresh.boss}")
      println(s"  roles: ${fresh.roles.size}")
      println(s"  tags: ${fresh.tags}")
      println()

      // 11. merge / evict / refresh
      println("--- MERGE / EVICT / REFRESH ---")
      val detached = entityDao.get(classOf[Employee], alice.id)
      session.evict(detached)
      detached.name = "Alice Smith"
      val merged = session.merge(detached)
      println(s"  merged name: ${merged.name}")
      session.refresh(merged)
      println(s"  refreshed name: ${merged.name}")
      println()

      // 12. L2 cache round-trip (Department/Employee are cacheable)
      println("--- L2 CACHE ---")
      entityDao.evict(classOf[Employee], alice.id)
      val cached = entityDao.get(classOf[Employee], alice.id)
      println(s"  L2 reload name: ${cached.name}")
      println()

      // 13. OQL: join / pagination / in / count
      println("--- OQL (join / pagination / in / count) ---")
      val jq = OqlBuilder.from(classOf[Employee], "e")
      jq.where("e.department = :dept", eng)
      jq.orderBy("e.code")
      println(s"  join query: ${entityDao.search(jq).map(e => e.code).mkString(",")}")
      val pq = OqlBuilder.from(classOf[Employee], "e")
      pq.limit(PageLimit(1, 10))
      pq.orderBy("e.code")
      println(s"  pagination: ${entityDao.search(pq).size}")
      val cq = OqlBuilder.from(classOf[Employee], "e")
      cq.where("e.code in ('E001')")
      println(s"  in-clause: ${entityDao.search(cq).size}")
      println(s"  count by code: ${entityDao.count(classOf[Employee], "code" -> "E001")}")
      println()

      // 14. bulk update
      println("--- BULK UPDATE ---")
      val updated = entityDao.executeUpdate(
        "update " + classOf[Employee].getName + " e set e.name = :name where e.code = :code",
        Map("name" -> "Alice Updated", "code" -> "E001"))
      println(s"  bulk updated rows: $updated")
      println()

      // 15. native SQL
      println("--- NATIVE SQL ---")
      val count = session.createNativeQuery("select count(*) from DEPARTMENTS").getSingleResult
      println(s"  native count: $count")
      println()

      // 16. Course with code-style generator
      println("--- COURSE (code id generator) ---")
      val course = new Course
      course.code = "CS101"
      course.name = "Computer Science"
      entityDao.saveOrUpdate(course)
      println(s"  course id: ${course.id}")
      val loadedCourse = entityDao.get(classOf[Course], course.id)
      println(s"  loaded course: ${loadedCourse.code} ${loadedCourse.name}")
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
