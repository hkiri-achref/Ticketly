package com.ticketly.catalog.domain.event;

import com.ticketly.catalog.domain.common.DomainRuleViolationException;
import java.io.Serial;

// Thrown, not returned as a Rejected result: editing a cancelled event is a
// caller bug, not an expected outcome the client chooses between. It joins
// the existing hierarchy so the 422 handler covers it without new code.
public final class EventNotEditableException extends DomainRuleViolationException {

	@Serial
	private static final long serialVersionUID = 1L;

	public EventNotEditableException(EventStatus status) {
		super("Event is %s and cannot be edited".formatted(status));
	}

}
