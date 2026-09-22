package com.ticketly.catalog.application;

import java.time.Instant;

public record UpdateEventCommand(String title, String description, Instant startsAt, Instant endsAt) {
}
