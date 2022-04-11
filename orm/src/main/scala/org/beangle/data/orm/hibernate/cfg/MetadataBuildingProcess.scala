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

package org.beangle.data.orm.hibernate.cfg

import org.hibernate.`type`.spi.TypeConfiguration
import org.hibernate.`type`.{BasicType, BasicTypeRegistry}
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.internal.{InFlightMetadataCollectorImpl, MetadataBuildingContextRootImpl}
import org.hibernate.boot.model.process.internal.{ManagedResourcesImpl, ScanningCoordinator}
import org.hibernate.boot.model.process.spi.ManagedResources
import org.hibernate.boot.model.{TypeContributions, TypeContributor}
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.boot.spi._
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.usertype.{CompositeUserType, UserType}

import scala.jdk.javaapi.CollectionConverters.asScala

/** MetadataBuildProcess
  * Clone hibernate spi,and invoke BindSourceProcessor
  * @see org.hibernate.boot.model.process.spi.MetadataBuildingProcess
  */
object MetadataBuildingProcess {

  def build(sources: MetadataSources, context: BootstrapContext, options: MetadataBuildingOptions): MetadataImplementor = {
    complete(sources, prepare(sources, context), context, options)
  }

  /** First step of 2-phase for MetadataSources->Metadata process
    * @param sources The MetadataSources
    * @param ctx     The bootstrapContext
    * @return
    */
  def prepare(sources: MetadataSources, ctx: BootstrapContext): ManagedResources = {
    val managedResources = ManagedResourcesImpl.baseline(sources, ctx)
    //FIXME why scan
    ScanningCoordinator.INSTANCE.coordinateScan(managedResources, ctx, sources.getXmlMappingBinderAccess)
    managedResources
  }

  /** Second step of 2-phase for MetadataSources->Metadata process
    * @param sources          The MetadataSources
    * @param managedResources The token/memento from 1st phase
    * @param context          The bootstrapContext
    * @param options          The building options
    * @return Token/memento representing all known users resources (classes, packages, mapping files, etc).
    */
  def complete(sources: MetadataSources, managedResources: ManagedResources, context: BootstrapContext,
               options: MetadataBuildingOptions): MetadataImplementor = {
    val metadataCollector = new InFlightMetadataCollectorImpl(context, options)

    handleTypes(context, options)

    val rootContext = new MetadataBuildingContextRootImpl(context, options, metadataCollector)

    for (converterInfo <- asScala(managedResources.getAttributeConverterDefinitions)) {
      metadataCollector.addAttributeConverter(converterInfo.toConverterDescriptor(rootContext))
    }

    context.getTypeConfiguration.scope(rootContext)

    val processor = new BindSourceProcessor(sources, rootContext)

    processor.prepare()

    processor.processTypeDefinitions()
    processor.processQueryRenames()
    processor.processAuxiliaryDatabaseObjectDefinitions()

    processor.processIdentifierGenerators()
    processor.processFilterDefinitions()
    processor.processFetchProfiles()

    val processedEntityNames = new java.util.HashSet[String]
    processor.prepareForEntityHierarchyProcessing()
    processor.processEntityHierarchies(processedEntityNames)
    processor.postProcessEntityHierarchies()

    processor.processResultSetMappings()
    processor.processNamedQueries()

    processor.finishUp()

    val classLoaderService = options.getServiceRegistry.getService(classOf[ClassLoaderService])
    for (contributor <- asScala(classLoaderService.loadJavaServices(classOf[MetadataContributor]))) {
      contributor.contribute(metadataCollector, context.getJandexView)
    }

    metadataCollector.processSecondPasses(rootContext)

    metadataCollector.buildMetadataInstance(rootContext)
  }

  def handleTypes(context: BootstrapContext, options: MetadataBuildingOptions): BasicTypeRegistry = {

    val classLoaderService = options.getServiceRegistry.getService(classOf[ClassLoaderService])

    // ultimately this needs to change a little bit to account for HHH-7792
    val basicTypeRegistry = new BasicTypeRegistry()

    val typeContributions = new BasicTypeContributions(basicTypeRegistry,context)

    // add Dialect contributed types
    val dialect = options.getServiceRegistry.getService(classOf[JdbcServices]).getDialect
    dialect.contributeTypes(typeContributions, options.getServiceRegistry)

    // add TypeContributor contributed types.
    for (contributor <- asScala(classLoaderService.loadJavaServices(classOf[TypeContributor]))) {
      contributor.contribute(typeContributions, options.getServiceRegistry)
    }

    // add explicit application registered types
    context.getTypeConfiguration.addBasicTypeRegistrationContributions(options.getBasicTypeRegistrations)
    basicTypeRegistry
  }

}
