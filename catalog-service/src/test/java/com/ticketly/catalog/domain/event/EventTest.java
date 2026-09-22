package com.ticketly.catalog.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketly.catalog.domain.common.DomainRuleViolationException;
import com.ticketly.catalog.domain.common.Money;
import com.ticketly.catalog.domain.event.TransitionResult.Ok;
import com.ticketly.catalog.domain.event.TransitionResult.Rejected;
import com.ticketly.catalog.domain.venue.Address;
import com.ticketly.catalog.domain.venue.Venue;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The aggregate's invariants are plain Java: no Spring, no database. This is
// the payoff of keeping rules inside the entity — they are trivially testable.
class EventTest {

	private static final Instant STARTS_AT = Instant.parse("2027-06-01T19:00:00Z");
	private static final Instant ENDS_AT = STARTS_AT.plus(Duration.ofHours(3));
	private static final Money PRICE = Money.of(new BigDecimal("49.90"), "EUR");
	private static final int CAPACITY = 100;

	private static Event draftEvent() {
		var venue = new Venue("Small Hall", new Address("1 Main St", "Paris", "France"), CAPACITY);
		return new Event("dev-organizer", "Concert", "A night of music", STARTS_AT, ENDS_AT, venue);
	}

	private static Event publishableEvent() {
		var event = draftEvent();
		event.addTier("Standard", PRICE, 60, 8);
		return event;
	}

	private static Event publishedEvent() {
		var event = publishableEvent();
		event.publish();
		return event;
	}

	private static Event cancelledEvent() {
		var event = draftEvent();
		event.cancel("Venue flooded");
		return event;
	}

	@Test
	void given_validInputs_when_constructed_then_isDraftWithNoTiers() {
		// given / when
		var event = draftEvent();

		// then
		assertThat(event.getId()).isNotNull();
		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
		assertThat(event.getTiers()).isEmpty();
		assertThat(event.getUpdatedAt()).isEqualTo(event.getCreatedAt());
	}

	@Test
	void given_endBeforeStart_when_constructed_then_throwsInvalidEventPeriod() {
		// given
		var venue = new Venue("Small Hall", new Address("1 Main St", "Paris", "France"), CAPACITY);

		// when
		var thrown = assertThatThrownBy(
				() -> new Event("dev-organizer", "Concert", null, ENDS_AT, STARTS_AT, venue));

		// then: the typed exception is also a DomainRuleViolationException (→ 422)
		thrown.isInstanceOf(InvalidEventPeriodException.class)
				.isInstanceOf(DomainRuleViolationException.class);
	}

	@Test
	void given_freeCapacity_when_addTier_then_tierIsAttachedToEvent() {
		// given
		var event = draftEvent();

		// when
		var tier = event.addTier("Standard", PRICE, 60, 8);

		// then
		assertThat(event.getTiers()).containsExactly(tier);
		assertThat(tier.getEvent()).isSameAs(event);
		assertThat(tier.getPrice()).isEqualTo(PRICE);
		assertThat(tier.getQuantity()).isEqualTo(60);
		assertThat(tier.getMaxPerBooking()).isEqualTo(8);
	}

	@Test
	void given_tiersAtCapacity_when_addTierExceeding_then_throwsCapacityExceeded() {
		// given: 60 + 40 = exactly the capacity of 100
		var event = draftEvent();
		event.addTier("Standard", PRICE, 60, 8);
		event.addTier("Balcony", PRICE, 40, 8);

		// when
		var thrown = assertThatThrownBy(() -> event.addTier("VIP", PRICE, 1, 2));

		// then
		thrown.isInstanceOf(CapacityExceededException.class)
				.isInstanceOf(DomainRuleViolationException.class)
				.hasMessageContaining("capacity 100");
		assertThat(event.getTiers()).hasSize(2);
	}

	@Test
	void given_eventWithTier_when_removeTier_then_tierIsGone() {
		// given
		var event = draftEvent();
		var tier = event.addTier("Standard", PRICE, 60, 8);

		// when
		event.removeTier(tier.getId());

		// then
		assertThat(event.getTiers()).isEmpty();
	}

	@Test
	void given_unknownTierId_when_removeTier_then_throwsEntityNotFound() {
		// given
		var event = draftEvent();
		var unknown = UUID.randomUUID();

		// when
		var thrown = assertThatThrownBy(() -> event.removeTier(unknown));

		// then
		thrown.isInstanceOf(EntityNotFoundException.class).hasMessageContaining(unknown.toString());
	}

	@Test
	void given_draftEvent_when_update_then_fieldsChangeAndUpdatedAtMovesForward() {
		// given
		var event = draftEvent();
		var newStart = STARTS_AT.plus(Duration.ofDays(1));
		var newEnd = newStart.plus(Duration.ofHours(2));
		var before = event.getUpdatedAt();

		// when
		event.update("Concert (moved)", null, newStart, newEnd);

		// then
		assertThat(event.getUpdatedAt()).isAfterOrEqualTo(before);
		assertThat(event.getTitle()).isEqualTo("Concert (moved)");
		assertThat(event.getDescription()).isNull();
		assertThat(event.getStartsAt()).isEqualTo(newStart);
		assertThat(event.getEndsAt()).isEqualTo(newEnd);
	}

	@Test
	void given_draftEvent_when_updateWithEndBeforeStart_then_throwsAndKeepsOldPeriod() {
		// given
		var event = draftEvent();

		// when
		var thrown = assertThatThrownBy(() -> event.update("Concert", null, ENDS_AT, STARTS_AT));

		// then
		thrown.isInstanceOf(InvalidEventPeriodException.class);
		assertThat(event.getStartsAt()).isEqualTo(STARTS_AT);
	}

	@Test
	void given_event_when_mutatingReturnedTiers_then_throwsUnsupportedOperation() {
		// given
		var event = draftEvent();
		var tiers = event.getTiers();

		// when
		var thrown = assertThatThrownBy(tiers::clear);

		// then
		thrown.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void given_twoEventsAndSameIdNotPossible_when_equals_then_onlyIdentityCounts() {
		// given
		var event = draftEvent();
		var other = draftEvent();

		// when
		boolean sameAsItself = event.equals(event);
		boolean sameAsOther = event.equals(other);

		// then: identical field values, different ids → not equal
		assertThat(sameAsItself).isTrue();
		assertThat(sameAsOther).isFalse();
		assertThat(event.hashCode()).isEqualTo(event.getId().hashCode());
	}

	// ---- state machine: publish -------------------------------------------

	@Test
	void given_draftWithTierInFuture_when_publish_then_okAndPublished() {
		// given
		var event = publishableEvent();
		var before = event.getUpdatedAt();

		// when
		var result = event.publish();

		// then: record pattern in the assertion — Ok carries the event itself
		assertThat(result).isInstanceOf(Ok.class);
		assertThat(((Ok) result).event()).isSameAs(event);
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getUpdatedAt()).isAfterOrEqualTo(before);
	}

	@Test
	void given_draftWithoutTiers_when_publish_then_rejectedNoTiersAndStillDraft() {
		// given
		var event = draftEvent();

		// when
		var result = event.publish();

		// then
		assertThat(result).isEqualTo(new Rejected(RejectionReason.NO_TIERS, EventStatus.DRAFT));
		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	void given_draftStartingInPast_when_publish_then_rejectedStartsInPast() {
		// given
		var venue = new Venue("Small Hall", new Address("1 Main St", "Paris", "France"), CAPACITY);
		var past = Instant.now().minus(Duration.ofDays(1));
		var event = new Event("dev-organizer", "Concert", null, past, past.plus(Duration.ofHours(2)), venue);
		event.addTier("Standard", PRICE, 60, 8);

		// when
		var result = event.publish();

		// then
		assertThat(result).isEqualTo(new Rejected(RejectionReason.STARTS_IN_PAST, EventStatus.DRAFT));
		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	void given_publishedEvent_when_publish_then_rejectedAlreadyPublished() {
		// given
		var event = publishedEvent();

		// when
		var result = event.publish();

		// then
		assertThat(result).isEqualTo(new Rejected(RejectionReason.ALREADY_PUBLISHED, EventStatus.PUBLISHED));
	}

	@Test
	void given_cancelledEvent_when_publish_then_rejectedAlreadyCancelled() {
		// given
		var event = cancelledEvent();

		// when
		var result = event.publish();

		// then
		assertThat(result).isEqualTo(new Rejected(RejectionReason.ALREADY_CANCELLED, EventStatus.CANCELLED));
		assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
	}

	// ---- state machine: cancel --------------------------------------------

	@Test
	void given_draftEvent_when_cancel_then_okWithReasonAndTimestamp() {
		// given
		var event = draftEvent();

		// when
		var result = event.cancel("Venue flooded");

		// then
		assertThat(result).isInstanceOf(Ok.class);
		assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
		assertThat(event.getCancellationReason()).isEqualTo("Venue flooded");
		assertThat(event.getCancelledAt()).isNotNull();
	}

	@Test
	void given_publishedEvent_when_cancel_then_okAndCancelled() {
		// given
		var event = publishedEvent();

		// when
		var result = event.cancel("Artist ill");

		// then
		assertThat(result).isInstanceOf(Ok.class);
		assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
	}

	@Test
	void given_cancelledEvent_when_cancelAgain_then_rejectedAndFirstReasonKept() {
		// given
		var event = cancelledEvent();
		var firstCancelledAt = event.getCancelledAt();

		// when
		var result = event.cancel("Second thoughts");

		// then: terminal state — the first reason is never overwritten
		assertThat(result).isEqualTo(new Rejected(RejectionReason.ALREADY_CANCELLED, EventStatus.CANCELLED));
		assertThat(event.getCancellationReason()).isEqualTo("Venue flooded");
		assertThat(event.getCancelledAt()).isEqualTo(firstCancelledAt);
	}

	@Test
	void given_nullReason_when_cancel_then_throwsNullPointerAndStaysDraft() {
		// given
		var event = draftEvent();

		// when
		var thrown = assertThatThrownBy(() -> event.cancel(null));

		// then
		thrown.isInstanceOf(NullPointerException.class);
		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
	}

	// ---- editing guard ----------------------------------------------------

	@Test
	void given_cancelledEvent_when_update_then_throwsNotEditable() {
		// given
		var event = cancelledEvent();

		// when
		var thrown = assertThatThrownBy(() -> event.update("New title", null, STARTS_AT, ENDS_AT));

		// then
		thrown.isInstanceOf(EventNotEditableException.class)
				.isInstanceOf(DomainRuleViolationException.class)
				.hasMessageContaining("CANCELLED");
		assertThat(event.getTitle()).isEqualTo("Concert");
	}

	@Test
	void given_cancelledEvent_when_addTier_then_throwsNotEditable() {
		// given
		var event = cancelledEvent();

		// when
		var thrown = assertThatThrownBy(() -> event.addTier("Standard", PRICE, 10, 8));

		// then
		thrown.isInstanceOf(EventNotEditableException.class);
		assertThat(event.getTiers()).isEmpty();
	}

	@Test
	void given_publishedEvent_when_updateAndAddTier_then_bothAllowed() {
		// given
		var event = publishedEvent();

		// when
		event.update("Concert (extended)", null, STARTS_AT, ENDS_AT);
		event.addTier("Balcony", PRICE, 20, 8);

		// then
		assertThat(event.getTitle()).isEqualTo("Concert (extended)");
		assertThat(event.getTiers()).hasSize(2);
	}

	@Test
	void given_newEvent_when_read_then_versionIsZeroAndNoCancellationData() {
		// given / when
		var event = draftEvent();

		// then
		assertThat(event.getVersion()).isZero();
		assertThat(event.getCancellationReason()).isNull();
		assertThat(event.getCancelledAt()).isNull();
	}

}
