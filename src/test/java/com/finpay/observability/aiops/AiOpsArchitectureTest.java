package com.finpay.observability.aiops;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Local enforcement of AGENTS.md Rule 4 for the AI Ops module: domain logic
 * must stay free of Spring/JPA/Kafka and must not depend on infrastructure or
 * web layers.
 */
class AiOpsArchitectureTest {

    @Test
    void domain_is_independent_of_infrastructure_and_web() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages("com.finpay.observability.aiops");

        ArchRule rule = noClasses()
                .that().resideInAPackage("..aiops.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "org.apache.kafka..",
                        "com.finpay.observability.aiops.infrastructure..",
                        "com.finpay.observability.aiops.interfaces..");

        rule.check(classes);
    }
}