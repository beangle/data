[#ftl]
[#include "template/head.ftl"/]
[@head "数据库设计"/]
[#include "template/table.ftl"/]
[#include "template/module.ftl"/]

#### 目 录

##### 1. 数据库设计说明
  * 数据环境要求
  * 数据库对象命名规则
  
##### 2. 数据库对象列表
  * 2.1 表格一览
  * 2.2 序列一览
  * 2.3 模块关系图

##### 3. 具体模块明细
[#list report.modules as m]
[@moduletree "3."+(m_index+1),m;prefix,module/]
[/#list]

##### 4. 数据库维护说明
  * 4.1 数据库管理要点
  
### 1. 数据库设计说明

#### 1.1 数据环境要求

[#include "template/env.ftl"/]
  
#### 1.2 数据库对象命名规则

[#include "template/rules.ftl"/]
  
### 2. 数据库对象列表

#### 2.1 表格列表

[#include "template/tables.ftl"/]

#### 2.2 序列一览

[#include "template/sequences.ftl"/]

#### 2.3 模块关系图
[#list report.images as img]

#### ${img_index+1}. ${img.title}
  * 关系图
  
![${img.title}](${report.imageurl}${img.name}.png)

[#if img.description?? && img.description?length>0]
  * 说明
  
  ${img.description}
[/#if]
[/#list]


### 3. 具体模块明细

[#list report.modules as m]

[@moduletables "3."+(m_index+1),m;prefix,module/]

[/#list]

### 4. 数据库维护说明

#### 4.1 数据库管理要点

[#include "template/mantain.ftl"/]
