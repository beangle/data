package org.beangle.data.hibernate.cfg

import org.hibernate.boot.spi.MetadataBuilderFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.spi.MetadataBuilderImplementor
import org.hibernate.boot.model.source.internal.hbm.HbmMetadataSourceProcessorImpl
import org.hibernate.boot.internal.InFlightMetadataCollectorImpl
import org.hibernate.boot.model.source.spi.MetadataSourceProcessor
import org.hibernate.boot.model.process.internal.ScanningCoordinator
import org.hibernate.boot.internal.MetadataBuildingContextRootImpl
import org.hibernate.boot.model.process.internal.ManagedResourcesImpl
import org.hibernate.boot.internal.ClassLoaderAccessImpl
import org.hibernate.boot.spi.MetadataBuildingOptions
import org.hibernate.usertype.CompositeUserType
import org.hibernate.boot.model.process.spi.ManagedResources
import org.hibernate.boot.internal.MetadataBuilderImpl
import org.hibernate.boot.model.TypeContributions
import org.hibernate.`type`.BasicTypeRegistry
import org.hibernate.boot.model.source.internal.annotations.AnnotationMetadataSourceProcessorImpl
import org.hibernate.usertype.UserType
import org.hibernate.boot.spi.MetadataImplementor
import org.hibernate.`type`.TypeFactory
import org.hibernate.`type`.BasicType
import org.hibernate.`type`.TypeResolver
import org.hibernate.boot.model.TypeContributor
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.boot.spi.MetadataContributor
import collection.JavaConverters.iterableAsScalaIterable
import org.hibernate.cfg.MetadataSourceType

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
      ScanningCoordinator.INSTANCE.coordinateScan(managedResources, options, sources.getXmlMappingBinderAccess());
      return managedResources
    }

    def complete(sources: MetadataSources, managedResources: ManagedResources, options: MetadataBuildingOptions): MetadataImplementor = {
      val basicTypeRegistry = handleTypes(options);

      val metadataCollector = new InFlightMetadataCollectorImpl(
        options,
        new TypeResolver(basicTypeRegistry, new TypeFactory()))

      for (attributeConverterDefinition <- iterableAsScalaIterable(managedResources.getAttributeConverterDefinitions)) {
        metadataCollector.addAttributeConverter(attributeConverterDefinition)
      }

      val classLoaderService = options.getServiceRegistry().getService(classOf[ClassLoaderService]);

      val classLoaderAccess = new ClassLoaderAccessImpl(
        options.getTempClassLoader(),
        classLoaderService)

      val rootMetadataBuildingContext = new MetadataBuildingContextRootImpl(
        options,
        classLoaderAccess,
        metadataCollector);

      val jandexView = options.getJandexView();

      val processor = new MetadataSourceProcessor() {
        private val hbmProcessor = new HbmMetadataSourceProcessorImpl(
          managedResources,
          rootMetadataBuildingContext);

        private val annotationProcessor = new AnnotationMetadataSourceProcessorImpl(
          managedResources,
          rootMetadataBuildingContext,
          jandexView);

        private val bindProcessor = new BindMatadataProcessor(
          sources.asInstanceOf[BindMetadataSources],
          rootMetadataBuildingContext,
          jandexView);

        def prepare() {
          hbmProcessor.prepare();
          annotationProcessor.prepare();
          bindProcessor.prepare();
        }

        def processTypeDefinitions() {
          hbmProcessor.processTypeDefinitions();
          annotationProcessor.processTypeDefinitions()
          bindProcessor.processTypeDefinitions()
        }

        def processQueryRenames() {
          hbmProcessor.processQueryRenames();
          annotationProcessor.processQueryRenames();
          bindProcessor.processQueryRenames()
        }

        def processNamedQueries() {
          hbmProcessor.processNamedQueries();
          annotationProcessor.processNamedQueries()
          bindProcessor.processNamedQueries()
        }

        def processAuxiliaryDatabaseObjectDefinitions() {
          hbmProcessor.processAuxiliaryDatabaseObjectDefinitions();
          annotationProcessor.processAuxiliaryDatabaseObjectDefinitions()
          bindProcessor.processAuxiliaryDatabaseObjectDefinitions()
        }

        def processIdentifierGenerators() {
          hbmProcessor.processIdentifierGenerators();
          annotationProcessor.processIdentifierGenerators();
          bindProcessor.processIdentifierGenerators()
        }

        def processFilterDefinitions() {
          hbmProcessor.processFilterDefinitions();
          annotationProcessor.processFilterDefinitions()
          bindProcessor.processFilterDefinitions()
        }

        def processFetchProfiles() {
          hbmProcessor.processFetchProfiles();
          annotationProcessor.processFetchProfiles()
          bindProcessor.processFetchProfiles()
        }

        def prepareForEntityHierarchyProcessing() {
          for (metadataSourceType <- iterableAsScalaIterable(options.getSourceProcessOrdering)) {
            if (metadataSourceType == MetadataSourceType.HBM) {
              hbmProcessor.prepareForEntityHierarchyProcessing();
            }

            if (metadataSourceType == MetadataSourceType.CLASS) {
              annotationProcessor.prepareForEntityHierarchyProcessing();
            }
          }
          bindProcessor.prepareForEntityHierarchyProcessing()
        }

        def processEntityHierarchies(processedEntityNames: java.util.Set[String]) {
          for (metadataSourceType <- iterableAsScalaIterable(options.getSourceProcessOrdering)) {
            if (metadataSourceType == MetadataSourceType.HBM) {
              hbmProcessor.processEntityHierarchies(processedEntityNames);
            }

            if (metadataSourceType == MetadataSourceType.CLASS) {
              annotationProcessor.processEntityHierarchies(processedEntityNames);
            }
          }
          bindProcessor.processEntityHierarchies(processedEntityNames)
        }

        def postProcessEntityHierarchies() {
          for (metadataSourceType <- iterableAsScalaIterable(options.getSourceProcessOrdering)) {
            if (metadataSourceType == MetadataSourceType.HBM) {
              hbmProcessor.postProcessEntityHierarchies();
            }

            if (metadataSourceType == MetadataSourceType.CLASS) {
              annotationProcessor.postProcessEntityHierarchies();
            }
          }
          bindProcessor.postProcessEntityHierarchies()
        }

        def processResultSetMappings() {
          hbmProcessor.processResultSetMappings();
          annotationProcessor.processResultSetMappings()
          bindProcessor.processResultSetMappings()
        }

        def finishUp() {
          hbmProcessor.finishUp();
          annotationProcessor.finishUp();
          bindProcessor.finishUp()
        }
      };

      processor.prepare();

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
        contributor.contribute(metadataCollector, jandexView);
      }

      metadataCollector.processSecondPasses(rootMetadataBuildingContext);

      return metadataCollector.buildMetadataInstance(rootMetadataBuildingContext);
    }

    def handleTypes(options: MetadataBuildingOptions): BasicTypeRegistry = {
      val classLoaderService = options.getServiceRegistry().getService(classOf[ClassLoaderService]);

      // ultimately this needs to change a little bit to account for HHH-7792
      val basicTypeRegistry = new BasicTypeRegistry();

      val typeContributions = new TypeContributions() {

        def contributeType(t: org.hibernate.`type`.BasicType) {
          basicTypeRegistry.register(t);
        }

        def contributeType(t: BasicType, keys: Array[String]) {
          basicTypeRegistry.register(t, keys);
        }

        def contributeType(t: UserType, keys: Array[String]) {
          basicTypeRegistry.register(t, keys);
        }

        def contributeType(t: CompositeUserType, keys: Array[String]) {
          basicTypeRegistry.register(t, keys);
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
