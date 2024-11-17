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

import org.beangle.data.dao.OqlBuilder
import org.beangle.data.orm.model.{Skill, SkillType}
import org.hibernate.SessionFactory

object CacheTest {

  def test(sf: SessionFactory): Unit = {
    val entityDao = new HibernateEntityDao(sf)
    entityDao.init()
    val session = sf.getCurrentSession
    val transaction = session.beginTransaction()
    val skillType = new SkillType
    skillType.name = "play basketball"
    val skill = new Skill
    skill.skillType = skillType
    skill.name = "national basketball champion"
    entityDao.saveOrUpdate(skillType, skill)

    val q = OqlBuilder.from(classOf[Skill], "s")
    q.where("s.name like :name", "%basket%")
    q.cacheable(true)
    val skills = entityDao.search(q)
    assert(skills.size == 1)
    entityDao.evict(classOf[Skill])
    entityDao.search(q)
    session.flush()
    transaction.commit()
  }
}
