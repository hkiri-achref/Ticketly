package com.ticketly.catalog.api;

import com.ticketly.catalog.domain.Event;
import com.ticketly.catalog.domain.EventStatus;
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
		Instant createdAt,
		Instant updatedAt) {

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
				event.getCreatedAt(),
				event.getUpdatedAt());
	}

}
