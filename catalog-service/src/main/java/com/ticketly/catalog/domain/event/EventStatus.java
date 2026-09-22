package com.ticketly.catalog.domain.event;

// Stored as text (@Enumerated(STRING) on the field): the ordinal form would
// silently corrupt data the day someone reorders the constants. Transitions:
// DRAFT -> PUBLISHED (Event.publish), DRAFT | PUBLISHED -> CANCELLED
// (Event.cancel); CANCELLED is terminal.
public enum EventStatus {
	DRAFT,
	PUBLISHED,
	CANCELLED
}
