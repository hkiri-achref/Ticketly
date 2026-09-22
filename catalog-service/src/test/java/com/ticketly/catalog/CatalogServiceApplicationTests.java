package com.ticketly.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Full-context integration test: proves the skeleton wires up end to end —
// DataSource from the Testcontainers Postgres, Flyway migrations applied,
// CatalogProperties bound and validated, MVC + springdoc beans created.
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class CatalogServiceApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void given_testcontainersPostgres_when_contextStarts_then_applicationBeansAreWired() {
		// given: the annotations above started the full context against a real Postgres

		// when
		var beanCount = context.getBeanDefinitionCount();

		// then: startup did not throw and the context is populated
		assertThat(beanCount).isPositive();
	}

}
