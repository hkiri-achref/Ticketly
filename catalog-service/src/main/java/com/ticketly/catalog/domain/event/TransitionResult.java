package com.ticketly.catalog.domain.event;

// A sealed interface lists ALL its implementations (`permits`), so a switch
// over it needs no default branch and the compiler fails when a new case is
// added but not handled. Records are the natural implementations: each
// outcome is an immutable value carrying just the data the caller needs.
// This is the §6.2 "sealed result type" alternative to throwing exceptions
// for EXPECTED business outcomes — a rejected publish is not an error, it is
// one of the two normal answers to the question "can this event go live?".
public sealed interface TransitionResult permits TransitionResult.Ok, TransitionResult.Rejected {

	record Ok(Event event) implements TransitionResult {
	}

	record Rejected(RejectionReason reason, EventStatus currentStatus) implements TransitionResult {
	}

}
