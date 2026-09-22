package com.ticketly.catalog.api.event;

import com.ticketly.catalog.domain.event.Event;
import com.ticketly.catalog.domain.event.EventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Hand-written mapping (§6.3). Note what it touches: getTiers() — which is
// why every service method returns an event whose tiers are already loaded —
// and getVenue().getId(), which Hibernate answers from the lazy proxy WITHOUT
// hitting the database (the id is the one thing a proxy always knows).
public record EventResponse(
		UUID id,
		String organizerId,
		String title,
		String description,
		Instant startsAt,
		Instant endsAt,
		UUID venueId,
		EventStatus status,
		List<TierResponse> tiers,
		String cancellationReason,
		Instant cancelledAt,
		Instant createdAt,
		Instant updatedAt,
		// Exposed so a client can see that the row changed under it; a future
		// If-Match / ETag scheme would compare exactly this number.
		long version) {

	public static EventResponse from(Event event) {
		return new EventResponse(
				event.getId(),
				event.getOrganizerId(),
				event.getTitle(),
				event.getDescription(),
				event.getStartsAt(),
				event.getEndsAt(),
				event.getVenue().getId(),
				event.getStatus(),
				event.getTiers().stream().map(TierResponse::from).toList(),
				event.getCancellationReason(),
				event.getCancelledAt(),
				event.getCreatedAt(),
				event.getUpdatedAt(),
				event.getVersion());
	}

}
