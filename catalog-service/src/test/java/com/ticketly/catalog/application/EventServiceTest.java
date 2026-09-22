package com.ticketly.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ticketly.catalog.domain.Address;
import com.ticketly.catalog.domain.CapacityExceededException;
import com.ticketly.catalog.domain.Event;
import com.ticketly.catalog.domain.EventStatus;
import com.ticketly.catalog.domain.Money;
import com.ticketly.catalog.domain.Venue;
import com.ticketly.catalog.persistence.EventRepository;
import com.ticketly.catalog.persistence.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Plain Mockito unit test: proves the service's orchestration decisions —
// which repository it asks, what it does on a miss, and that the write paths
// rely on dirty checking (no save) — without a Spring context or a database.
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	private static final Instant STARTS_AT = Instant.parse("2027-06-01T19:00:00Z");
	private static final Instant ENDS_AT = STARTS_AT.plus(Duration.ofHours(3));
	private static final Money PRICE = Money.of(new BigDecimal("49.90"), "EUR");

	@Mock
	private EventRepository events;

	@Mock
	private VenueRepository venues;

	@InjectMocks
	private EventService service;

	private static Venue smallHall() {
		return new Venue("Small Hall", new Address("1 Main St", "Paris", "France"), 100);
	}

	@Test
	void given_existingVenue_when_create_then_savesDraftEventBuiltFromCommand() {
		// given
		var venue = smallHall();
		var command = new CreateEventCommand("dev-organizer", "Concert", "Music", STARTS_AT, ENDS_AT, venue.getId());
		given(venues.findById(venue.getId())).willReturn(Optional.of(venue));
		given(events.save(any(Event.class))).willAnswer(invocation -> invocation.getArgument(0));

		// when
		var created = service.create(command);

		// then
		assertThat(created.getOrganizerId()).isEqualTo("dev-organizer");
		assertThat(created.getTitle()).isEqualTo("Concert");
		assertThat(created.getVenue()).isSameAs(venue);
		assertThat(created.getStatus()).isEqualTo(EventStatus.DRAFT);
		assertThat(created.getTiers()).isEmpty();
	}

	@Test
	void given_unknownVenue_when_create_then_throwsEntityNotFoundAndSavesNothing() {
		// given
		var venueId = UUID.randomUUID();
		var command = new CreateEventCommand("dev-organizer", "Concert", null, STARTS_AT, ENDS_AT, venueId);
		given(venues.findById(venueId)).willReturn(Optional.empty());

		// when
		var thrown = assertThatThrownBy(() -> service.create(command));

		// then
		thrown.isInstanceOf(EntityNotFoundException.class).hasMessageContaining(venueId.toString());
		then(events).should(never()).save(any());
	}

	@Test
	void given_existingEvent_when_update_then_mutatesLoadedEntityWithoutSave() {
		// given
		var event = new Event("dev-organizer", "Concert", null, STARTS_AT, ENDS_AT, smallHall());
		given(events.findWithTiersById(event.getId())).willReturn(Optional.of(event));
		var command = new UpdateEventCommand("Concert (moved)", "New room", STARTS_AT, ENDS_AT);

		// when
		var updated = service.update(event.getId(), command);

		// then: same managed instance, changed in place — dirty checking does the UPDATE
		assertThat(updated).isSameAs(event);
		assertThat(updated.getTitle()).isEqualTo("Concert (moved)");
		assertThat(updated.getDescription()).isEqualTo("New room");
		then(events).should(never()).save(any());
	}

	@Test
	void given_existingEvent_when_addTier_then_tierIsOnReturnedEventWithoutSave() {
		// given
		var event = new Event("dev-organizer", "Concert", null, STARTS_AT, ENDS_AT, smallHall());
		given(events.findWithTiersById(event.getId())).willReturn(Optional.of(event));
		var command = new AddTierCommand("Standard", PRICE, 60, 8);

		// when
		var updated = service.addTier(event.getId(), command);

		// then
		assertThat(updated.getTiers()).hasSize(1);
		assertThat(updated.getTiers().getFirst().getName()).isEqualTo("Standard");
		then(events).should(never()).save(any());
	}

	@Test
	void given_tierBeyondCapacity_when_addTier_then_domainExceptionPropagates() {
		// given
		var event = new Event("dev-organizer", "Concert", null, STARTS_AT, ENDS_AT, smallHall());
		given(events.findWithTiersById(event.getId())).willReturn(Optional.of(event));
		var command = new AddTierCommand("Standard", PRICE, 101, 8);

		// when
		var thrown = assertThatThrownBy(() -> service.addTier(event.getId(), command));

		// then: the service adds nothing — the rule lives in the aggregate
		thrown.isInstanceOf(CapacityExceededException.class);
	}

	@Test
	void given_existingId_when_getById_then_returnsEventLoadedWithTiers() {
		// given
		var event = new Event("dev-organizer", "Concert", null, STARTS_AT, ENDS_AT, smallHall());
		given(events.findWithTiersById(event.getId())).willReturn(Optional.of(event));

		// when
		var found = service.getById(event.getId());

		// then
		assertThat(found).isSameAs(event);
		then(events).should(never()).findById(any());
	}

	@Test
	void given_unknownId_when_getById_then_throwsEntityNotFoundNamingTheId() {
		// given
		var id = UUID.randomUUID();
		given(events.findWithTiersById(id)).willReturn(Optional.empty());

		// when
		var thrown = assertThatThrownBy(() -> service.getById(id));

		// then
		thrown.isInstanceOf(EntityNotFoundException.class).hasMessageContaining(id.toString());
	}

}
