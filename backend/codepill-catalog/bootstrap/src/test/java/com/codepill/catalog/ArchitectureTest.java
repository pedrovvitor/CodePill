package com.codepill.catalog;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * TESTING_QUALITY.md §3.1 — dependency rules as executable architecture:
 * domain has no framework imports, dependencies point inward only, adapters
 * never call each other, controllers never touch repositories.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.codepill.catalog");
    }

    @Test
    void domainMustDependOnJdkOnly() {
        noClasses().that().resideInAPackage("..catalog.domain..")
                .should().dependOnClassesThat()
                .resideOutsideOfPackages("..catalog.domain..", "java..")
                .because("the domain module is pure Java 25 — no frameworks, no I/O (ARCHITECTURE.md §3.1)")
                .check(classes);
    }

    @Test
    void dependenciesMustPointInwardOnly() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy("..catalog.domain..")
                .layer("Application").definedBy("..catalog.application..")
                .layer("WebAdapter").definedBy("..catalog.adapter.in.web..")
                .layer("PersistenceAdapter").definedBy("..catalog.adapter.out.persistence..")
                .layer("CacheAdapter").definedBy("..catalog.adapter.out.cache..")
                .layer("Bootstrap").definedBy("com.codepill.catalog", "..catalog.bootstrap..")
                .whereLayer("WebAdapter").mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("PersistenceAdapter").mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("CacheAdapter").mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("Application").mayOnlyBeAccessedByLayers(
                        "WebAdapter", "PersistenceAdapter", "CacheAdapter", "Bootstrap")
                .whereLayer("Bootstrap").mayNotBeAccessedByAnyLayer()
                .check(classes);
    }

    @Test
    void controllersMustNotTouchRepositories() {
        noClasses().that()
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter.out..")
                .because("controllers call use cases, never repositories or out-adapters (ARCHITECTURE.md §3.1 rule 2)")
                .check(classes);
    }

    @Test
    void applicationMustNotDependOnAdapters() {
        noClasses().that().resideInAPackage("..catalog.application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..catalog.adapter..", "..catalog.bootstrap..")
                .because("the application layer depends only on domain and its own ports")
                .check(classes);
    }

    @Test
    void transactionsBeginAndEndInUseCasesOnly() {
        methods().that()
                .areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .should().beDeclaredInClassesThat().resideInAPackage("..application.usecase..")
                .because("transactions begin and end in the use case, never in controllers or repositories (ARCHITECTURE.md §3.1 rule 6)")
                .check(classes);
        noClasses().that().resideOutsideOfPackage("..application.usecase..")
                .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .because("class-level @Transactional outside use cases would hide the transaction boundary")
                .check(classes);
    }
}
