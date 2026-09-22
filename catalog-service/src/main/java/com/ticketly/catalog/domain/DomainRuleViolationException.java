package com.ticketly.catalog.domain;

import java.io.Serial;

// Base of every business-rule failure. Unchecked on purpose: the caller cannot
// do anything about a violated invariant except report it, and one advice
// method maps the whole hierarchy to 422 Unprocessable Content. F-04 adds
// state-transition violations under the same root.
public class DomainRuleViolationException extends RuntimeException {

	// Throwable is Serializable; an explicit id stops the JVM from deriving one
	// from the class shape (and stops PMD from flagging its absence).
	@Serial
	private static final long serialVersionUID = 1L;

	protected DomainRuleViolationException(String message) {
		super(message);
	}

}
