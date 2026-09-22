package com.ticketly.catalog.api;

import com.ticketly.catalog.domain.Venue;
import java.time.Instant;
import java.util.UUID;

// Response DTO as a record + hand-written static factory (§6.3): the mapping
// boundary is explicit and cheap to read — no MapStruct magic yet, on purpose.
public record VenueResponse(
		UUID id,
		String name,
		AddressResponse address,
		int capacity,
		Instant createdAt) {

	public static VenueResponse from(Venue venue) {
		var address = venue.getAddress();
		return new VenueResponse(
				venue.getId(),
				venue.getName(),
				new AddressResponse(address.street(), address.city(), address.country()),
				venue.getCapacity(),
				venue.getCreatedAt());
	}

	public record AddressResponse(String street, String city, String country) {
	}

}
