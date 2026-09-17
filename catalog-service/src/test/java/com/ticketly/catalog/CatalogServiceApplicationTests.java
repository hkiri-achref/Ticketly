package com.ticketly.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Full-context integration test: proves the skeleton wires up end to end —
// DataSource from the Testcontainers Postgres, Flyway V1 applied,
// CatalogProperties bound and validated, MVC + springdoc beans created.
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class CatalogServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
