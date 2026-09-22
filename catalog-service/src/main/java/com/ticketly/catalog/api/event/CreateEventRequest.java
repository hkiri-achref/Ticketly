package com.ticketly.catalog.api.event;

import com.ticketly.catalog.application.event.CreateEventCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

// Request record: shape + syntactic validation only. The business rule
// "ends after it starts" is NOT here — it belongs to the Event aggregate, and
// duplicating it at the edge would give two places to keep in sync.
public record CreateEventRequest(
		@NotBlank @Size(min = 3, max = 200) String title,
		@Size(max = 2000) String description,
		@NotNull Instant startsAt,
		@NotNull Instant endsAt,
		@NotNull UUID venueId) {

	public CreateEventRequest {
		if (title != null) {
			title = title.strip();
		}
		if (description != null) {
			description = description.strip();
		}
	}

	// The organizer is not part of the body: the controller supplies it from
	// the security context (F-07) — today a fixed placeholder.
	public CreateEventCommand toCommand(String organizerId) {
		return new CreateEventCommand(organizerId, title, description, startsAt, endsAt, venueId);
	}

}
