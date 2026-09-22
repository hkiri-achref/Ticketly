package com.ticketly.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// @ServiceConnection: the container bean itself tells Spring its JDBC
	// URL/credentials once started — replaces the old @DynamicPropertySource
	// boilerplate of copying container properties by hand.
	// postgres:17 pinned to match infra/docker-compose.yml — tests should run
	// against the same major version as dev.
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

}
