package com.ticketly.catalog.domain;

import java.io.Serial;

public final class CapacityExceededException extends DomainRuleViolationException {

	@Serial
	private static final long serialVersionUID = 1L;

	public CapacityExceededException(int requested, int allocated, int capacity) {
		super("Tier quantity %d would exceed venue capacity %d (already allocated: %d)"
				.formatted(requested, capacity, allocated));
	}

}
