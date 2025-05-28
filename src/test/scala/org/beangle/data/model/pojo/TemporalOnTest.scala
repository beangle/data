package org.beangle.data.model.pojo

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate

class TemporalOnTest extends AnyFunSpec with Matchers {

  describe("TemporalOn") {
    it("fillin endOn") {
      val datas = Seq(new TitleJournal("1998-09-03", "科员"),
        new TitleJournal("2000-11-02", "副科长"),
        new TitleJournal("2000-11-02", "实验室副研究员"),
        new TitleJournal("2001-08-02", "科长"),
        new TitleJournal("2003-07-06", "副主任"),
        new TitleJournal("2003-07-06", "研究员"),
      )

      val sorted = TemporalOn.fillinEndOn(datas)
      val d0 = sorted.head
      val d1 = sorted(1)
      val d2 = sorted(2)
      val d3 = sorted(3)
      val d4 = sorted(4)
      val d5 = sorted(5)

      assert(d0.endOn.nonEmpty)
      assert(d0.endOn.contains(LocalDate.parse("2000-11-01")))

      assert(d1.endOn.nonEmpty)
      assert(d1.endOn.contains(LocalDate.parse("2001-08-01")))
      assert(d2.endOn.contains(LocalDate.parse("2001-08-01")))

      assert(d3.endOn.contains(LocalDate.parse("2003-07-05")))

      assert(d4.endOn.isEmpty)
      assert(d5.endOn.isEmpty)
    }
  }
}

class TitleJournal extends TemporalOn {
  var title: String = _

  def this(beginOn: String, title: String) = {
    this()
    this.beginOn = LocalDate.parse(beginOn)
    this.title = title
  }

  override def toString: String = {
    endOn match {
      case None => s"${title} ${beginOn}~"
      case Some(d) => s"${title} ${beginOn}~${d}"
    }
  }
}
