package com.ticketly.catalog.persistence.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketly.catalog.TestcontainersConfiguration;
import com.ticketly.catalog.domain.common.Money;
import com.ticketly.catalog.domain.event.Event;
import com.ticketly.catalog.domain.venue.Address;
import com.ticketly.catalog.domain.venue.Venue;
import com.ticketly.catalog.persistence.venue.VenueRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// JPA slice against real Postgres 17 (Flyway V1..V4 applied), so the tests
// prove the MIGRATION matches the mapping: Money's @AttributeOverride columns,
// the FK, the enum-as-text column. Each test runs in a rolled-back
// transaction unless it says otherwise (see the NOT_SUPPORTED tests).
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class EventRepositoryTest {

	private static final Instant STARTS_AT = Instant.parse("2027-06-01T19:00:00Z");
	private static final Money PRICE = Money.of(new BigDecimal("49.90"), "EUR");

	@Autowired
	private EventRepository repository;

	@Autowired
	private VenueRepository venues;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// The NOT_SUPPORTED tests commit for real; the rolled-back ones do not need
	// this but it is harmless. Keeps the shared container clean for the other
	// @DataJpaTest classes that reuse the same cached context.
	@AfterEach
	void cleanUpCommittedRows() {
		jdbcTemplate.update("delete from ticket_tiers");
		jdbcTemplate.update("delete from events");
		jdbcTemplate.update("delete from venues");
	}

	private Event newDraftEvent(int capacity) {
		var venue = venues.save(new Venue("Small Hall", new Address("1 Main St", "Paris", "France"), capacity));
		return new Event("dev-organizer", "Concert", null, STARTS_AT, STARTS_AT.plus(Duration.ofHours(3)), venue);
	}

	@Test
	void given_eventWithTier_when_saved_then_moneyAndStatusRoundTripThroughTheirColumns() {
		// given
		var event = newDraftEvent(100);
		event.addTier("Standard", PRICE, 60, 8);
		repository.save(event);
		entityManager.flush();
		entityManager.clear();

		// when
		var row = jdbcTemplate.queryForMap(
				"select price_amount, price_currency from ticket_tiers where event_id = ?", event.getId());
		var reloaded = repository.findWithTiersById(event.getId()).orElseThrow();
		var status = jdbcTemplate.queryForObject("select status from events where id = ?", String.class,
				event.getId());

		// then: the Money record was rebuilt through its validating constructor
		assertThat(reloaded.getTiers()).hasSize(1);
		assertThat(reloaded.getTiers().getFirst().getPrice()).isEqualTo(PRICE);
		assertThat(row).containsEntry("price_amount", new BigDecimal("49.90"))
				.containsEntry("price_currency", "EUR");
		assertThat(status).isEqualTo("DRAFT");
	}

	@Test
	void given_eventWithTier_when_removeTierAndFlush_then_rowIsGone() {
		// given. Trap: the id is app-assigned, so Spring Data's save() sees a
		// non-null id, decides the entity is NOT new and calls merge(), which
		// returns a managed COPY and leaves `event` detached. Only the managed
		// copy is watched by dirty checking and orphanRemoval, so work on it.
		var event = newDraftEvent(100);
		var tierId = event.addTier("Standard", PRICE, 60, 8).getId();
		var managed = repository.save(event);
		entityManager.flush();
		var rowsBefore = countTierRows(tierId);

		// when: no repository call for the tier — dropping it from the parent's
		// collection is the whole operation; orphanRemoval issues the DELETE.
		managed.removeTier(tierId);
		entityManager.flush();

		// then
		assertThat(rowsBefore).isEqualTo(1);
		assertThat(countTierRows(tierId)).isZero();
	}

	// NOT_SUPPORTED: this test method runs with NO transaction, which is the
	// only way to reproduce the production bug. The setup commits through a
	// TransactionTemplate (programmatic tx), then the entity is loaded in the
	// repository's own short read transaction and comes back DETACHED.
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void given_findById_when_tiersAccessedOutsideTx_then_lazyInitializationException() {
		// given
		var eventId = commitEventWithOneTier();
		var detached = repository.findById(eventId).orElseThrow();

		// when: getTiers() itself is fine (it only wraps); touching the contents
		// forces Hibernate to load the lazy bag — with no session to do it.
		var thrown = assertThatThrownBy(() -> detached.getTiers().size());

		// then
		thrown.isInstanceOf(LazyInitializationException.class);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void given_findWithTiersById_when_tiersAccessedOutsideTx_then_tiersLoaded() {
		// given
		var eventId = commitEventWithOneTier();

		// when: the @EntityGraph query fetched the tiers inside its own
		// transaction, so the detached entity already carries them.
		var detached = repository.findWithTiersById(eventId).orElseThrow();

		// then
		assertThat(detached.getTiers()).hasSize(1);
		assertThat(detached.getTiers().getFirst().getName()).isEqualTo("Standard");
	}

	@Test
	void given_eventInHashSet_when_persisted_then_stillContained() {
		// given: hashCode is computed from the id BEFORE the entity is saved.
		var event = newDraftEvent(100);
		var set = new HashSet<Event>();
		set.add(event);

		// when: with an app-generated UUID the id — and so the hash — does not
		// change on persist. With a DB-generated id it would go from null to a
		// value, the entity would sit in the wrong bucket, and contains() would
		// be false for the very same object.
		repository.save(event);
		entityManager.flush();
		entityManager.clear();
		var reloaded = repository.findById(event.getId()).orElseThrow();

		// then: same instance still found, and a different instance with the
		// same id is "contained" too — equality is identity of the row.
		assertThat(set).contains(event).contains(reloaded);
		assertThat(reloaded).isNotSameAs(event).isEqualTo(event);
	}

	@Test
	void given_savedEvent_when_publishedAndFlushed_then_versionIncremented() {
		// given: a fresh row starts at version 0. The tier is added BEFORE the
		// first flush on purpose — every flushed change to the row (addTier
		// bumps updated_at) increments the version, not only publish().
		var event = newDraftEvent(100);
		event.addTier("Standard", PRICE, 60, 8);
		var managed = repository.save(event);
		entityManager.flush();
		var versionBefore = managed.getVersion();

		// when: dirty checking issues `update ... set version = 1 where version = 0`
		managed.publish();
		entityManager.flush();
		var versionInDb = jdbcTemplate.queryForObject("select version from events where id = ?", Long.class,
				managed.getId());

		// then
		assertThat(versionBefore).isZero();
		assertThat(managed.getVersion()).isEqualTo(1L);
		assertThat(versionInDb).isEqualTo(1L);
	}

	// The lost-update demo. Two threads each open their OWN transaction, load
	// the same row (both see version 0), then WAIT on a latch so neither can
	// commit before the other has loaded. Both publish and commit: the first
	// UPDATE ... WHERE version = 0 succeeds and sets version 1; the second
	// matches zero rows, Hibernate raises StaleObjectStateException and
	// JpaTransactionManager translates it at commit into Spring's
	// ObjectOptimisticLockingFailureException. The latch is what makes the
	// race deterministic — racing two HTTP calls would be a flaky test.
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void given_twoThreadsLoadSameEvent_when_bothPublish_then_oneFailsWithOptimisticLock() throws Exception {
		// given
		var eventId = commitEventWithOneTier();
		var bothLoaded = new CountDownLatch(2);
		var template = new TransactionTemplate(transactionManager);
		Callable<Void> publishInOwnTransaction = () -> {
			template.executeWithoutResult(status -> {
				var event = repository.findWithTiersById(eventId).orElseThrow();
				bothLoaded.countDown();
				awaitQuietly(bothLoaded);
				event.publish();
			});
			return null;
		};

		// when: a virtual thread per task (Java 21). ExecutorService is
		// AutoCloseable since 19: close() waits for both tasks to finish.
		List<Future<Void>> outcomes;
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			outcomes = executor.invokeAll(List.of(publishInOwnTransaction, publishInOwnTransaction));
		}

		// then: exactly one loser, and the row shows exactly one commit
		var failures = outcomes.stream().map(EventRepositoryTest::failureOf).filter(t -> t != null).toList();
		assertThat(failures).hasSize(1);
		assertThat(failures.getFirst()).isInstanceOf(ObjectOptimisticLockingFailureException.class);
		var row = jdbcTemplate.queryForMap("select status, version from events where id = ?", eventId);
		assertThat(row).containsEntry("status", "PUBLISHED").containsEntry("version", 1L);
	}

	private static Throwable failureOf(Future<Void> outcome) {
		try {
			outcome.get(10, TimeUnit.SECONDS);
			return null;
		} catch (ExecutionException e) {
			return e.getCause();
		} catch (Exception e) {
			throw new AssertionError("Task did not complete", e);
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new AssertionError("Other thread never loaded the event");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError("Interrupted while waiting for the other thread", e);
		}
	}

	private UUID commitEventWithOneTier() {
		var template = new TransactionTemplate(transactionManager);
		return template.execute(status -> {
			var event = newDraftEvent(100);
			event.addTier("Standard", PRICE, 60, 8);
			return repository.save(event).getId();
		});
	}

	private int countTierRows(UUID tierId) {
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from ticket_tiers where id = ?", Integer.class, tierId);
		return count == null ? 0 : count;
	}

}
