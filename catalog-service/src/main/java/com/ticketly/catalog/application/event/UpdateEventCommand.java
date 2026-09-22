package com.ticketly.catalog.application.event;

import java.time.Instant;

public record UpdateEventCommand(String title, String description, Instant startsAt, Instant endsAt) {
}
