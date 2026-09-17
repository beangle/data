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

package org.beangle.data.json

import org.beangle.data.model.LongId
import org.beangle.data.model.pojo.Named
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class Department extends LongId with Named {
  var code: String = _
}

class Employee extends LongId with Named {
  var department: Department = _
  var previous: mutable.Buffer[Department] = new mutable.ListBuffer[Department]
}

class JsonAPITest extends AnyFunSpec, Matchers {

  describe("JsonAPI") {
    it("parse") {
      val json =
        """
          |{
          |    "data": [
          |        {
          |            "attributes": {
          |                "code": "alter_apply_1",
          |                "name": "22"
          |            },
          |            "id": "2025012018162405962",
          |            "type": "flows",
          |            "relationships": {
          |                "tasks": {
          |                    "data": [
          |                        {
          |                            "id": "2025012018162405963",
          |                            "type": "flow-tasks"
          |                        },
          |                        {
          |                            "id": "2025012018172905964",
          |                            "type": "flow-tasks"
          |                        },
          |                        {
          |                            "id": "2025012018182205965",
          |                            "type": "flow-tasks"
          |                        }
          |                    ]
          |                }
          |            }
          |        }
          |    ],
          |    "included": [
          |        {
          |            "attributes": {
          |                "name": "学生"
          |            },
          |            "id": "2",
          |            "type": "groups"
          |        },
          |        {
          |            "attributes": {
          |                "name": "教职工"
          |            },
          |            "id": "3",
          |            "type": "groups"
          |        },
          |        {
          |            "attributes": {
          |                "name": "导师审批",
          |                "idx": 1
          |            },
          |            "id": "2025012018162405963",
          |            "type": "flow-tasks",
          |            "relationships": {
          |                "group": {
          |                    "data": {
          |                        "id": "3",
          |                        "type": "groups"
          |                    }
          |                }
          |            }
          |        },
          |        {
          |            "attributes": {
          |                "name": "提交申请",
          |                "idx": 0
          |            },
          |            "id": "2025012018172905964",
          |            "type": "flow-tasks",
          |            "relationships": {
          |                "group": {
          |                    "data": {
          |                        "id": "2",
          |                        "type": "groups"
          |                    }
          |                }
          |            }
          |        },
          |        {
          |            "attributes": {
          |                "name": "学院审批",
          |                "idx": 2
          |            },
          |            "id": "2025012018182205965",
          |            "type": "flow-tasks",
          |            "relationships": {
          |                "group": {
          |                    "data": {
          |                        "id": "2",
          |                        "type": "groups"
          |                    }
          |                }
          |            }
          |        }
          |    ]
          |}
          |""".stripMargin

      val r = JsonAPI.parse(json)
      assert(r.resources.size == 1)
      val data = r.resource
      assert(data.getArray("tasks").size == 3)
      assert(!data.contains("attributes"))
      assert(!data.contains("relationships"))
    }

    it("append custom attributes") {
      val (emp, _) = sample

      given context: JsonAPI.Context = new JsonAPI.Context
      val filters = context.filters
      filters.register(classOf[Employee], "departName", e => e.asInstanceOf[Employee].department.name)
      filters.register(classOf[Employee], "leaderName", e => e.asInstanceOf[Employee].department.name)

      val resource = JsonAPI.create(emp, "")
      assert(resource.attributes.get("departName").contains("计算机学院"))
      assert(resource.attributes.get("leaderName").contains("计算机学院"))
    }

    it("append custom attribute of associated entity") {
      val (emp, _) = sample

      given context: JsonAPI.Context = new JsonAPI.Context
      context.filters.register(classOf[Department], "fullName", d => {
        val dept = d.asInstanceOf[Department]
        s"${dept.code}-${dept.name}"
      })

      JsonAPI.create(emp, "")
      val deptResource = context.includedResources(JsonAPI.typeName(classOf[Department]))("1").asInstanceOf[JsonAPI.Resource]
      assert(deptResource.attributes.get("fullName").contains("CS-计算机学院"))
    }

    it("skip null custom attribute, registered attribute ignores filters") {
      val (emp, _) = sample

      given context: JsonAPI.Context = new JsonAPI.Context
      context.filters.register(classOf[Employee], "departName", _ => null)
      context.filters.register(classOf[Employee], "age", _ => 30)
      context.exclude[Employee]("age")

      val resource = JsonAPI.create(emp, "")
      assert(!resource.attributes.contains("departName"))
      assert(resource.attributes.get("age").contains(30))
    }
  }

  private def sample: (Employee, Department) = {
    val dept = new Department
    dept.id = 1
    dept.name = "计算机学院"
    dept.code = "CS"

    val emp = new Employee
    emp.id = 100
    emp.name = "张三"
    emp.department = dept
    (emp, dept)
  }
}
