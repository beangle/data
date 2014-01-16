[#ftl]
[#include "template/head.ftl"/]
[@head "数据库设计" /]
[#include "template/module.ftl"/]

#### 目 录

##### 1. 数据库设计说明
  * [数据环境要求](env.html)
  * [数据库对象命名规则](rules.html)
  
##### 2. 数据库对象列表
  * 2.1 [表格一览](tables.html)
  * 2.2 [序列一览](sequences.html)
  * 2.3 [模块关系图](images.html)

##### 3. 具体模块明细
[#list report.modules as m]
[@moduleindex "3."+(m_index+1),m;prefix,module/]
[/#list]

##### 4. 数据库维护说明
  * 4.1 [数据库管理要点](mantain.html)
