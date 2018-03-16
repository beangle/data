/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.hibernate.cfg

import scala.collection.JavaConverters.iterableAsScalaIterable

import org.hibernate.`type`.{ BasicType, BasicTypeRegistry, TypeFactory, TypeResolver }
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.internal.{ ClassLoaderAccessImpl, InFlightMetadataCollectorImpl, MetadataBuilderImpl, MetadataBuildingContextRootImpl }
import org.hibernate.boot.model.{ TypeContributions, TypeContributor }
import org.hibernate.boot.model.process.internal.{ ManagedResourcesImpl, ScanningCoordinator }
import org.hibernate.boot.model.process.spi.ManagedResources
import org.hibernate.boot.model.source.spi.MetadataSourceProcessor
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.boot.spi.{ MetadataBuilderFactory, MetadataBuilderImplementor, MetadataBuildingOptions, MetadataContributor, MetadataImplementor }
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.usertype.{ CompositeUserType, UserType }

class BeangleMetadataBuilderFactory extends MetadataBuilderFactory {

  def getMetadataBuilder(metadatasources: MetadataSources, defaultBuilder: MetadataBuilderImplementor): MetadataBuilderImplementor = {
    new BeangleMetadataBuilderImpl(metadatasources)
  }

  class BeangleMetadataBuilderImpl(metadataSources: MetadataSources) extends MetadataBuilderImpl(metadataSources) {

    override def build(): MetadataImplementor = {
      val options = getMetadataBuildingOptions
      complete(metadataSources, prepare(metadataSources, options), options)
    }

    def prepare(
      sources: MetadataSources,
      options: MetadataBuildingOptions): ManagedResources = {
      val managedResources = ManagedResourcesImpl.baseline(sources, options);
      //FIXME why scan
      ScanningCoordinator.INSTANCE.coordinateScan(managedResources, options, sources.getXmlMappingBinderAccess());
      return managedResources
    }

    def complete(sources: MetadataSources, managedResources: ManagedResources, options: MetadataBuildingOptions): MetadataImplementor = {
      val basicTypeRegistry = handleTypes(options);

      val metadataCollector = new InFlightMetadataCollectorImpl(
        options, new TypeResolver(basicTypeRegistry, new TypeFactory()))

      for (attributeConverterDefinition <- iterableAsScalaIterable(managedResources.getAttributeConverterDefinitions)) {
        metadataCollector.addAttributeConverter(attributeConverterDefinition)
      }

      val classLoaderService = options.getServiceRegistry().getService(classOf[ClassLoaderService]);
      val classLoaderAccess = new ClassLoaderAccessImpl(options.getTempClassLoader(), classLoaderService)

      val rootMetadataBuildingContext = new MetadataBuildingContextRootImpl(
        options, classLoaderAccess, metadataCollector);

      val processor = new BindMatadataProcessor(sources, rootMetadataBuildingContext)

      processor.prepare()

      processor.processTypeDefinitions();
      processor.processQueryRenames();
      processor.processAuxiliaryDatabaseObjectDefinitions();

      processor.processIdentifierGenerators();
      processor.processFilterDefinitions();
      processor.processFetchProfiles();

      val processedEntityNames = new java.util.HashSet[String];
      processor.prepareForEntityHierarchyProcessing();
      processor.processEntityHierarchies(processedEntityNames);
      processor.postProcessEntityHierarchies();

      processor.processResultSetMappings();
      processor.processNamedQueries();

      processor.finishUp();

      for (contributor <- iterableAsScalaIterable(classLoaderService.loadJavaServices(classOf[MetadataContributor]))) {
        contributor.contribute(metadataCollector, options.getJandexView());
      }

      metadataCollector.processSecondPasses(rootMetadataBuildingContext);

      return metadataCollector.buildMetadataInstance(rootMetadataBuildingContext);
    }

    def handleTypes(options: MetadataBuildingOptions): BasicTypeRegistry = {
      val classLoaderService = options.getServiceRegistry().getService(classOf[ClassLoaderService]);

      // ultimately this needs to change a little bit to account for HHH-7792
      val basicTypeRegistry = new BasicTypeRegistry();

      val typeContributions = new TypeContributions() {

        override def contributeType(t: BasicType) {
          basicTypeRegistry.register(t)
        }

        override def contributeType(t: BasicType, keys: String*) {
          basicTypeRegistry.register(t, keys.toArray)
        }

        override def contributeType(t: UserType, keys: String*) {
          basicTypeRegistry.register(t, keys.toArray)
        }

        override def contributeType(t: CompositeUserType, keys: String*) {
          basicTypeRegistry.register(t, keys.toArray)
        }
      };

      // add Dialect contributed types
      val dialect = options.getServiceRegistry.getService(classOf[JdbcServices]).getDialect
      dialect.contributeTypes(typeContributions, options.getServiceRegistry)

      // add TypeContributor contributed types.
      for (contributor <- iterableAsScalaIterable(classLoaderService.loadJavaServices(classOf[TypeContributor]))) {
        contributor.contribute(typeContributions, options.getServiceRegistry)
      }

      // add explicit application registered types
      for (basicTypeRegistration <- iterableAsScalaIterable(options.getBasicTypeRegistrations)) {
        basicTypeRegistry.register(
          basicTypeRegistration.getBasicType(),
          basicTypeRegistration.getRegistrationKeys());
      }

      return basicTypeRegistry;
    }
  }

}
