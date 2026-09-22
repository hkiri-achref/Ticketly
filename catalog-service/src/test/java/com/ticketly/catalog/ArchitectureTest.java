package com.ticketly.catalog;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

// Architecture as a test (ArchUnit): the package rules of REQUIREMENTS §5.2
// are checked on the compiled classes, so they cannot drift silently.
// Runs under Surefire like every other test and fails `verify` when red.
@AnalyzeClasses(packagesOf = CatalogServiceApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	private static final String BASE = "com.ticketly.catalog";

	// Rule 1: no class directly under a layer root. Each layer is split into
	// one sub-package per aggregate (event, venue, ...) plus `common` for
	// cross-cutting pieces, so a layer never grows into one flat directory.
	// resideInAnyPackage without ".." matches the EXACT package only.
	private static final ArchRule NO_CLASS_AT_LAYER_ROOT = noClasses()
			.should().resideInAnyPackage(
					BASE + ".api", BASE + ".application", BASE + ".domain",
					BASE + ".persistence", BASE + ".messaging", BASE + ".client")
			.as("no class may sit directly under a layer root; use <layer>.<aggregate> or <layer>.common");

	// Rule 2: dependencies only point downwards. api → application → domain,
	// persistence → domain; the application layer never imports an HTTP type,
	// the domain imports nothing above it. config is left unconstrained (it
	// wires everything).
	private static final ArchRule LAYERS_ONLY_DEPEND_DOWNWARDS = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.layer("Api").definedBy(BASE + ".api..")
			.layer("Application").definedBy(BASE + ".application..")
			.layer("Domain").definedBy(BASE + ".domain..")
			.layer("Persistence").definedBy(BASE + ".persistence..")
			.whereLayer("Api").mayNotBeAccessedByAnyLayer()
			.whereLayer("Application").mayOnlyBeAccessedByLayers("Api")
			.whereLayer("Persistence").mayOnlyBeAccessedByLayers("Application")
			.whereLayer("Domain").mayOnlyBeAccessedByLayers("Api", "Application", "Persistence");

	@ArchTest
	void given_mainClasses_when_checkingLayerRoots_then_everyClassIsInAnAggregateOrCommonSubPackage(
			JavaClasses classes) {
		// given: the compiled main classes (tests excluded), imported by @AnalyzeClasses

		// when / then
		NO_CLASS_AT_LAYER_ROOT.check(classes);
	}

	@ArchTest
	void given_mainClasses_when_checkingLayerDependencies_then_noLayerDependsUpwards(JavaClasses classes) {
		// given: the compiled main classes (tests excluded), imported by @AnalyzeClasses

		// when / then
		LAYERS_ONLY_DEPEND_DOWNWARDS.check(classes);
	}

}
