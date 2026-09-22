package com.ticketly.catalog.application.event;

import java.time.Instant;
import java.util.UUID;

// Command record (§6.2): the use case's input, independent of the HTTP shape.
// The controller builds it from the request and adds what HTTP knows and the
// body must not carry — here the organizer identity.
public record CreateEventCommand(
		String organizerId,
		String title,
		String description,
		Instant startsAt,
		Instant endsAt,
		UUID venueId) {
}
