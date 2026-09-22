package com.ticketly.catalog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Aggregate root. Everything that touches a tier goes through this class, so
// the one rule spanning event and tiers — total quantity never exceeds venue
// capacity — has exactly one home and cannot be bypassed from a service.
@Entity
@Table(name = "events")
public class Event {

	@Id
	private UUID id;

	@Column(name = "organizer_id", nullable = false)
	private String organizerId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 2000)
	private String description;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	// The venue is a separate aggregate (it has its own lifecycle and API), so
	// the event only REFERENCES it — no cascade — and loads it lazily.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "venue_id", nullable = false)
	private Venue venue;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EventStatus status;

	// mappedBy: TicketTier.event owns the FK column; this side is the mirror.
	// cascade = ALL: persisting/removing the event does the same to its tiers.
	// orphanRemoval: a tier dropped from this list is DELETEd at flush — the
	// child's life is bound to its membership in the parent. @OneToMany is
	// LAZY by default, which is what we want (and what the tests probe).
	// final: the REFERENCE never changes, only the contents. Hibernate still
	// swaps in its PersistentBag on load — through reflection, which is allowed
	// to write a final instance field (only records/hidden classes forbid it).
	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
	private final List<TicketTier> tiers = new ArrayList<>();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Event() {
	}

	public Event(String organizerId, String title, String description, Instant startsAt, Instant endsAt,
			Venue venue) {
		requireValidPeriod(startsAt, endsAt);
		this.id = UUID.randomUUID();
		this.organizerId = Objects.requireNonNull(organizerId, "organizerId must not be null");
		this.title = Objects.requireNonNull(title, "title must not be null");
		this.description = description;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.venue = Objects.requireNonNull(venue, "venue must not be null");
		this.status = EventStatus.DRAFT;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	// Invariant: the sum of all tier quantities must fit in the venue. The
	// capacity is read from the referenced venue, never passed in — a caller
	// that could pass a number could also pass a wrong one.
	public TicketTier addTier(String name, Money price, int quantity, int maxPerBooking) {
		int allocated = tiers.stream().mapToInt(TicketTier::getQuantity).sum();
		int capacity = venue.getCapacity();
		if (allocated + quantity > capacity) {
			throw new CapacityExceededException(quantity, allocated, capacity);
		}
		var tier = new TicketTier(this, name, price, quantity, maxPerBooking);
		tiers.add(tier);
		touch();
		return tier;
	}

	// Removing from the collection is the whole operation: orphanRemoval turns
	// it into a DELETE at flush. No repository call for the child anywhere.
	public void removeTier(UUID tierId) {
		boolean removed = tiers.removeIf(tier -> tier.getId().equals(tierId));
		if (!removed) {
			throw new EntityNotFoundException("Tier %s not found in event %s".formatted(tierId, id));
		}
		touch();
	}

	public void update(String title, String description, Instant startsAt, Instant endsAt) {
		requireValidPeriod(startsAt, endsAt);
		this.title = Objects.requireNonNull(title, "title must not be null");
		this.description = description;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		touch();
	}

	// private static: called from the constructor, and calling an overridable
	// method from a constructor is a classic trap (the subclass — here a
	// Hibernate proxy — is not initialised yet).
	private static void requireValidPeriod(Instant startsAt, Instant endsAt) {
		Objects.requireNonNull(startsAt, "startsAt must not be null");
		Objects.requireNonNull(endsAt, "endsAt must not be null");
		if (!endsAt.isAfter(startsAt)) {
			throw new InvalidEventPeriodException(startsAt, endsAt);
		}
	}

	private void touch() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getOrganizerId() {
		return organizerId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public Venue getVenue() {
		return venue;
	}

	public EventStatus getStatus() {
		return status;
	}

	// Read-only view: callers can iterate but not add/remove behind the
	// invariant's back. The wrapper does not initialise a lazy collection —
	// touching it outside a transaction still throws (see EventRepositoryTest).
	public List<TicketTier> getTiers() {
		return Collections.unmodifiableList(tiers);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Event event && Objects.equals(id, event.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

}
