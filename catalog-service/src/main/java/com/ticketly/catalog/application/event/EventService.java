package com.ticketly.catalog.application.event;

import com.ticketly.catalog.domain.event.Event;
import com.ticketly.catalog.domain.event.TransitionResult;
import com.ticketly.catalog.domain.venue.Venue;
import com.ticketly.catalog.persistence.event.EventRepository;
import com.ticketly.catalog.persistence.venue.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// One service method = one transaction = one unit of work on the aggregate.
// The write methods follow the same three-step shape — load, call a domain
// method, return — and NEVER call save(): the entity is managed inside the
// transaction, so Hibernate's dirty checking flushes the changes at commit.
@Service
@Transactional(readOnly = true)
public class EventService {

	private final EventRepository events;
	private final VenueRepository venues;

	public EventService(EventRepository events, VenueRepository venues) {
		this.events = events;
		this.venues = venues;
	}

	@Transactional
	public Event create(CreateEventCommand command) {
		var venue = venues.findById(command.venueId())
				.orElseThrow(() -> new EntityNotFoundException("Venue %s not found".formatted(command.venueId())));
		var event = new Event(command.organizerId(), command.title(), command.description(),
				command.startsAt(), command.endsAt(), venue);
		// The only save() in this class, because this is the only NEW entity.
		return events.save(event);
	}

	// Edits on a CANCELLED event are refused by the aggregate itself
	// (EventNotEditableException → 422); the service does not repeat the check.
	@Transactional
	public Event update(UUID id, UpdateEventCommand command) {
		var event = loadWithTiers(id);
		event.update(command.title(), command.description(), command.startsAt(), command.endsAt());
		return event;
	}

	@Transactional
	public Event addTier(UUID id, AddTierCommand command) {
		var event = loadWithTiers(id);
		event.addTier(command.name(), command.price(), command.quantity(), command.maxPerBooking());
		return event;
	}

	// The service passes the domain's answer through untouched. On Rejected
	// nothing was mutated, so commit issues no UPDATE; on Ok the version
	// column is checked at flush — a concurrent writer surfaces here as
	// ObjectOptimisticLockingFailureException, thrown out of the transaction
	// commit (not from the domain), which the API maps to 409.
	@Transactional
	public TransitionResult publish(UUID id) {
		var event = loadWithTiers(id);
		var result = event.publish();
		// TODO: F-15 — on Ok, write an EventPublished record to the outbox in
		// this same transaction.
		return result;
	}

	@Transactional
	public TransitionResult cancel(UUID id, CancelEventCommand command) {
		var event = loadWithTiers(id);
		return event.cancel(command.reason());
	}

	public Event getById(UUID id) {
		return loadWithTiers(id);
	}

	// Always the entity-graph query, even for update(): the controller builds
	// the response — including tiers[] — AFTER this transaction has closed
	// (OSIV is off), so the tiers must already be loaded when we return.
	private Event loadWithTiers(UUID id) {
		return events.findWithTiersById(id)
				.orElseThrow(() -> new EntityNotFoundException("Event %s not found".formatted(id)));
	}

}
