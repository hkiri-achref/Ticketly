package com.ticketly.catalog.domain.event;

import com.ticketly.catalog.domain.common.DomainRuleViolationException;
import java.io.Serial;
import java.time.Instant;

public final class InvalidEventPeriodException extends DomainRuleViolationException {

	@Serial
	private static final long serialVersionUID = 1L;

	public InvalidEventPeriodException(Instant startsAt, Instant endsAt) {
		super("Event must end after it starts (startsAt=%s, endsAt=%s)".formatted(startsAt, endsAt));
	}

}
