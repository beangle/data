package org.beangle.data.orm.hibernate

import org.beangle.data.dao.OqlBuilder
import org.beangle.data.orm.model.{Skill, SkillType}
import org.hibernate.SessionFactory

object CacheTest {

  def test(sf: SessionFactory): Unit = {
    val entityDao = new HibernateEntityDao(sf)
    val session = sf.getCurrentSession()
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
    transaction.commit()
    session.clear()
    session.close()
  }
}
