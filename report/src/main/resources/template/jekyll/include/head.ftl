[#ftl]
[#macro head toc=false]
---
layout: page
title: ${report.system.name} ${report.title}
description: "${report.system.name}${report.title}"
categories: [model-${report.system.version}]
version: ["${report.system.version}"]
---
{% include JB/setup %}
[#if toc]
 目  录

* toc
{:toc}
[/#if]
[/#macro]
