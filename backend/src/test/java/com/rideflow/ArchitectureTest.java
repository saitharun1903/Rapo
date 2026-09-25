package com.rideflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/** Enforces the layering rules from docs/architecture.md section 4. */
@AnalyzeClasses(packages = "com.rideflow", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotAccessRepositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule controllersDoNotExposeEntities = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule dtosDoNotReferenceEntities = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule controllersAreNotTransactional = noClasses()
            .that().resideInAPackage("..controller..")
            .should().beAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule restControllersLiveInControllerPackage = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule servicesDoNotDependOnControllers = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule websocketLayerUsesServicesNotPersistence = noClasses()
            .that().resideInAPackage("..websocket..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .orShould().dependOnClassesThat().areAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule websocketLayerIsNotTransactional = noClasses()
            .that().resideInAPackage("..websocket..")
            .should().beAnnotatedWith(Transactional.class);

    /** Services publish through ports (DomainEventPublisher, LocationStream); Kafka stays an adapter. */
    @ArchTest
    static final ArchRule servicesDoNotDependOnKafka = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..kafka..");

    @ArchTest
    static final ArchRule kafkaConsumersDelegateToServices = noClasses()
            .that().resideInAPackage("..kafka.consumer..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .orShould().dependOnClassesThat().areAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule kafkaConsumersAreNotTransactional = noClasses()
            .that().resideInAPackage("..kafka.consumer..")
            .should().beAnnotatedWith(Transactional.class);

    /** The AI layer sees only facts and prompts: never the database, services or transport layers. */
    @ArchTest
    static final ArchRule aiProvidersDoNotReachIntoTheApplication = noClasses()
            .that().resideInAPackage("com.rideflow.ai..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..repository..", "..service..", "..controller..", "..kafka..", "..websocket..", "..entity..");

    @ArchTest
    static final ArchRule entitiesDependOnlyOnDomainTypes = noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..service..", "..controller..", "..dto..", "..repository..", "..security..", "..mapper..",
                    "..websocket..", "..kafka..");
}
