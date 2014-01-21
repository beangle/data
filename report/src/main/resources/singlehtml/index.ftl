[#ftl]
[#include "template/head.ftl"/]
[#include "template/table.ftl"/]
[#include "template/module.ftl"/]

<div class="container-narrow">
 <div class="content">
  <div class="page-header">
   <h1>${report.system.name} ${report.system.version!} ${report.title} </h1>
  </div>

  <div class="row-fluid">
   <div class="span12">

<h4>目 录</h4>

<h5> 1. 数据库设计说明</h5>
<ul>
  <li>数据环境要求</li>
  <li>数据库对象命名规则</li>
</ul>

<h5>2. 数据库对象列表</h5>
<ul>
  <li><a href="#table_list">2.1 表格一览</a></li>
  <li><a href="#sequence_list">2.2 序列一览</a></li>
  <li><a href="#image_list">2.3 模块关系图</a></li>
</ul>

<h5>3. 具体模块明细</h5>
<ul>
[#list report.modules as m]
[@moduletree "3."+(m_index+1),m;prefix,module/]
[/#list]
</ul>

<h5>4. 数据库维护说明</h5>
<ul>
  <li><a href="#database_mangement">4.1 数据库管理要点</a></li>
</ul>

<h3>1. 数据库设计说明</h3>

<h4>1.1 数据环境要求</h4>

<p>数据库环境要求:</p>

<ol>
  <li>数据库采用了oracle10g以上的版本。</li>
  <li>额外你需要补充的说明...</li>
</ol>

<h4>1.2 数据库对象命名规则</h4>

<p>数据库对象命名规则:</p>

<ol>
  <li>数据库中表、视图、索引采用英文及其缩写，尽量避免使用汉语拼音首字母缩写</li>
  <li>其他您的规则</li>
</ol>
  
<h3>2. 数据库对象列表</h3>

<h4 id="table_list">2.1 表格列表</h4>

[#include "template/tables.ftl"/]

<h4 id="sequence_list">2.2 序列一览</h4>

[#include "template/sequences.ftl"/]

<h4 id="image_list">2.3 模块关系图</h4>
[#list report.images as img]

<h4>${img_index+1}. ${img.title}</h4>
<ul>
  <li>关系图</li>
</ul>
<p><img src="${report.imageurl}${img.name}.png" alt="${img.title}" /></p>
[#if img.description?? && img.description?length>0]
<ul>
  <li>说明</li>
</ul>
<p>${img.description}</p>
[/#if]
[/#list]

<h3>3. 具体模块明细</h3>

[#list report.modules as m]
[@moduletables "3."+(m_index+1),m;prefix,module/]
[/#list]

<h3>4. 数据库维护说明</h3>
<h4 id="database_mangement">4.1 数据库管理要点</h4>
<p>维护说明:</p>
<ul>
  <li>不要在数据库中建立过多的临时表，数据库不是垃圾场。</li>
  <li>定期备份数据库，做好恢复准备。</li>
  <li>定期监视sql语句历史，为优化做准备。</li>
  <li>对经常进行读写，并且数据量较大的表格进行重组。</li>
</ul>

  </div>
</div>
  </div>
</div>
</body>
</html>