package com.ticketly.catalog.domain.venue;

import jakarta.persistence.Embeddable;

// A record as a JPA @Embeddable (supported since Hibernate 6.2): an address has
// no identity of its own — two equal addresses ARE the same value — so record
// value semantics (equals over all components, immutable) are exactly right.
// Hibernate maps the three components to three columns of the owning table.
@Embeddable
public record Address(String street, String city, String country) {
}
