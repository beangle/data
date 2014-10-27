[#ftl]
<?xml version="1.0"?>
<!DOCTYPE hibernate-mappng PUBLIC "-//Hibernate/Hibernate Mappng DTD 3.0//EN"
 "http://www.hibernate.org/dtd/hibernate-mappng-3.0.dtd">

<hibernate-mapping>
[#list classes as cls]
<class name="${cls.className}" table="${cls.table.name}" schema="${cls.table.schema!}"
       entity-name="${cls.entityName}">
    [#if cls.cacheConcurrencyStrategy??]
    <cache usage="${cls.cacheConcurrencyStrategy}" region="${cls.cacheRegionName}"/>
    [/#if]
    <id name="${cls.identifierProperty.name}" column="[#list cls.identifierProperty.value.columnIterator as c]${c.name}[/#list]" unsaved-value="null"  type="${cls.identifierProperty.value.typeName}">
      <generator class="${cls.identifier.identifierGeneratorStrategy}"/>
    </id>
    [#list cls.propertyIterator as p]
    [#assign pv = p.value/]
    [#t/]
    [#if generator.isToOne(pv)]
    <[#if pv.ignoreNotFound??]many-to-one[#else]one-to-one[/#if] name="${p.name}" class="${pv.referencedEntityName}"[#rt/]
    [#list pv.columnIterator as ci] column="${ci.name}"[#if ci.unique] unique="true"[/#if] [#if !ci.nullable] not-null="true"[/#if][/#list]/>
    [#elseif generator.isSet(pv)]
    <set name="${p.name}"[#if pv.inverse] inverse="true"[/#if] table="${pv.collectionTable.name}" [#if p.cascade??]cascade="${p.cascade}"[/#if]>
        <key [#list pv.key.columnIterator as pki]column="${pki.name}"[#if pki.nullable] not-null="true"[/#if][/#list]/>
        [#if generator.isOneToMany(pv.element)]
        <one-to-many class="${pv.element.referencedEntityName}"/>
        [#elseif generator.isManyToMany(pv.element)]
        <many-to-many class="${pv.element.referencedEntityName}" [#list pv.element.columnIterator as ci] column="${ci.name}"[/#list]/>
        [/#if]
    </set>
    [#elseif generator.isBag(pv)]
    <bag name="${p.name}"[#if pv.inverse] inverse="true"[/#if] table="${pv.collectionTable.name}" [#if p.cascade??]cascade="${p.cascade}"[/#if]>
        <key [#list pv.key.columnIterator as pki]column="${pki.name}"[#if pki.nullable] not-null="true"[/#if][/#list]/>
        [#if generator.isOneToMany(pv.element)]
        <one-to-many class="${pv.element.referencedEntityName}"/>
        [#elseif generator.isManyToMany(pv.element)]
        <many-to-many class="${pv.element.referencedEntityName}" [#list pv.element.columnIterator as ci] column="${ci.name}"[/#list]/>
        [/#if]
    </bag>
    [#elseif pv.columnSpan==1]
    <property name="${p.name}" [#list pv.columnIterator as ci]column="${ci.name}"[#rt/]
    [#if ci.length!=255] length="${ci.length?c}"[/#if][#t/]
    [#if ci.unique] unique="true"[/#if][#if !ci.nullable] not-null="true"[/#if] [/#list][#t/]
    [#if !generator.isCustomType(pv.type)] type="${pv.typeName}"[/#if][#t/]
    [#if p.metaAttributes??][#list p.metaAttributes?keys as mak]${mak}="${p.metaAttributes[mak]}" [/#list][/#if][#t/]
    >
    [#if generator.isCustomType(pv.type)]
      [#if generator.isEnumType(pv.type)]
        <type name="org.hibernate.type.EnumType"><param name="enumClass">${pv.type.returnedClass.name}</param></type>
      [#elseif generator.isScalaEnumType(pv.type)]
        <type name="org.beangle.data.jpa.hibernate.udt.EnumType"><param name="enumClass">${pv.type.returnedClass.name}</param></type>
      [#else]
        <type name="${pv.type.name}"/>
      [/#if]
    [/#if]
    </property>
    [/#if]
    [/#list]
</class>

[/#list]
</hibernate-mapping>