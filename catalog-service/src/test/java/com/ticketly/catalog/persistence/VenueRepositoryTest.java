package com.ticketly.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketly.catalog.TestcontainersConfiguration;
import com.ticketly.catalog.domain.Address;
import com.ticketly.catalog.domain.Venue;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Boot 4.1 moved slice-test annotations from the old monolithic
// spring-boot-test-autoconfigure into per-module artifacts/packages.
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

// @DataJpaTest = JPA slice only (entities, repositories, Flyway, JdbcTemplate —
// no controllers/services), each test in a rolled-back transaction. With the
// @ServiceConnection postgres container imported, Boot backs off its embedded-DB
// replacement and Flyway runs V1+V2 against real Postgres 17 — so this test
// proves the MIGRATION matches the entity, not just Hibernate against itself.
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class VenueRepositoryTest {

	@Autowired
	private VenueRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void given_savedVenue_when_reloadedFromDatabase_then_addressRoundTripsThroughThreeColumns() {
		// given: an INSERT that really hit the database. flush + clear force the
		// SQL and drop the first-level cache, so the reload below is a real read
		// instead of the same in-memory instance.
		var saved = repository.save(
				new Venue("Le Zénith", new Address("211 Avenue Jean Jaurès", "Paris", "France"), 6293));
		entityManager.flush();
		entityManager.clear();

		// when
		var reloaded = repository.findById(saved.getId()).orElseThrow();
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"select street, city, country from venues where id = ?", saved.getId());

		// then: record equality compares all components — one assert proves the
		// whole value object survived; the raw row proves one embedded field
		// really became three physical columns.
		assertThat(reloaded.getAddress()).isEqualTo(new Address("211 Avenue Jean Jaurès", "Paris", "France"));
		assertThat(reloaded).isEqualTo(saved); // ID-based entity equality
		assertThat(row).containsEntry("street", "211 Avenue Jean Jaurès")
				.containsEntry("city", "Paris")
				.containsEntry("country", "France");
	}

	@Test
	void given_venuesInTwoCities_when_findByCityIgnoringCase_then_returnsOnlyThatCity() {
		// given
		repository.save(new Venue("Le Zénith", new Address("211 Avenue Jean Jaurès", "Paris", "France"), 6293));
		repository.save(new Venue("O2 Arena", new Address("Peninsula Square", "London", "UK"), 20000));
		entityManager.flush();

		// when
		Page<Venue> page = repository.findByAddressCityIgnoreCase("pArIs", PageRequest.of(0, 10));

		// then
		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().getFirst().getName()).isEqualTo("Le Zénith");
	}

}
