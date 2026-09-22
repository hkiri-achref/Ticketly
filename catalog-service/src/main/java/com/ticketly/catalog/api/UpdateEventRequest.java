package com.ticketly.catalog.api;

import com.ticketly.catalog.application.UpdateEventCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateEventRequest(
		@NotBlank @Size(min = 3, max = 200) String title,
		@Size(max = 2000) String description,
		@NotNull Instant startsAt,
		@NotNull Instant endsAt) {

	public UpdateEventRequest {
		if (title != null) {
			title = title.strip();
		}
		if (description != null) {
			description = description.strip();
		}
	}

	public UpdateEventCommand toCommand() {
		return new UpdateEventCommand(title, description, startsAt, endsAt);
	}

}
