package com.ticketly.catalog.application.venue;

import com.ticketly.catalog.domain.venue.Address;

// Command record (§6.2): the use case's input, so the application layer never
// imports an HTTP request type. The dependency arrow points api → application,
// never the other way — enforced by ArchitectureTest.
public record CreateVenueCommand(String name, Address address, int capacity) {
}
