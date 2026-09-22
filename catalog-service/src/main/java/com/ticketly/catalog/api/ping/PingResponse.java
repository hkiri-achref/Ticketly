package com.ticketly.catalog.api.ping;

import java.time.Instant;

// Response DTO as a record (§6.2): immutable, value-based equals/hashCode for
// free, and Jackson serializes the components directly — no getters to write.
public record PingResponse(String service, Instant time) {
}
