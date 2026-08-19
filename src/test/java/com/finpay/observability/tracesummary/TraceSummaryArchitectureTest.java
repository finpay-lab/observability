package com.finpay.observability.tracesummary;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Local enforcement of AGENTS.md Rule 4 for the trace summarization module:
 * domain logic must stay free of Spring/JPA/Kafka and must not depend on
 * infrastructure or web layers.
 */
class TraceSummaryArchitectureTest {

    @Test
    void domain_is_independent_of_infrastructure_and_web() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages("com.finpay.observability.tracesummary");

        ArchRule rule = noClasses()
                .that().resideInAPackage("..tracesummary.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "org.apache.kafka..",
                        "com.finpay.observability.tracesummary.infrastructure..",
                        "com.finpay.observability.tracesummary.interfaces..");

        rule.check(classes);
    }
}