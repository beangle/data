package org.beangle.data.hibernate.cfg

import org.hibernate.boot.MetadataSources
import org.hibernate.mapping.TypeDef
import org.beangle.commons.orm.Mappings
import org.hibernate.service.ServiceRegistry

class BindMetadataSources(val mappings: Mappings, serviceRegistry: ServiceRegistry) extends MetadataSources(serviceRegistry) {

}
