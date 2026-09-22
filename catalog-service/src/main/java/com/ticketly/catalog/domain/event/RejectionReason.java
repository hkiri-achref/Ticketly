package com.ticketly.catalog.domain.event;

// Why a transition was refused. An enum, not a free string: the API layer
// decides the HTTP status by switching on it, and an exhaustive switch over
// an enum breaks the build the day a new reason is added without a mapping.
public enum RejectionReason {

	NO_TIERS("An event needs at least one ticket tier to be published"),
	STARTS_IN_PAST("An event that has already started cannot be published"),
	ALREADY_PUBLISHED("The event is already published"),
	ALREADY_CANCELLED("The event is cancelled and cannot change any more");

	private final String message;

	RejectionReason(String message) {
		this.message = message;
	}

	public String message() {
		return message;
	}

}
