package com.ticketly.catalog.api.event;

import com.ticketly.catalog.application.event.CancelEventCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Same length as the cancellation_reason column, so a too-long reason fails
// as a 400 field error instead of a 500 from the database.
public record CancelEventRequest(@NotBlank @Size(max = 500) String reason) {

	public CancelEventRequest {
		if (reason != null) {
			reason = reason.strip();
		}
	}

	public CancelEventCommand toCommand() {
		return new CancelEventCommand(reason);
	}

}
