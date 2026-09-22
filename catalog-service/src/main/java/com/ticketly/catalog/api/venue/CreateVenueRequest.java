package com.ticketly.catalog.api.venue;

import com.ticketly.catalog.application.venue.CreateVenueCommand;
import com.ticketly.catalog.domain.venue.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Bean Validation annotations sit directly on record components — they land on
// the field AND the constructor parameter, so the record is validated wherever
// Spring binds it. @Valid on the nested record cascades into its components.
public record CreateVenueRequest(
		@NotBlank @Size(min = 3, max = 120) String name,
		@Min(1) @Max(200000) int capacity,
		@NotNull @Valid AddressPayload address) {

	// Compact constructor: normalization only, no reassignment ceremony — the
	// implicit `this.name = name` runs after this block. Null-safe strip: the
	// constructor must not NPE before validation gets a chance to report null
	// (an `if` rather than a ternary, so no null is ever assigned explicitly).
	public CreateVenueRequest {
		if (name != null) {
			name = name.strip();
		}
	}

	// The request → command mapping lives at the edge: the application layer
	// receives domain-typed input and never sees the HTTP shape.
	public CreateVenueCommand toCommand() {
		return new CreateVenueCommand(name, new Address(address.street(), address.city(), address.country()), capacity);
	}

	// Nested record: the address shape only exists as part of this request, so
	// it lives inside it instead of polluting the api package.
	public record AddressPayload(
			@NotBlank String street,
			@NotBlank String city,
			@NotBlank String country) {

		public AddressPayload {
			if (street != null) {
				street = street.strip();
			}
			if (city != null) {
				city = city.strip();
			}
			if (country != null) {
				country = country.strip();
			}
		}
	}

}
