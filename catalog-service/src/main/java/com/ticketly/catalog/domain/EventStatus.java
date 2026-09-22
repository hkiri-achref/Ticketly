package com.ticketly.catalog.domain;

// Stored as text (@Enumerated(STRING) on the field): the ordinal form would
// silently corrupt data the day someone reorders the constants. Only DRAFT is
// reachable in F-03; publish/cancel transitions arrive with F-04.
public enum EventStatus {
	DRAFT,
	PUBLISHED,
	CANCELLED
}
