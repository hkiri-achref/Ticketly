package com.ticketly.catalog.application;

import com.ticketly.catalog.domain.Money;

// The price arrives already as a domain Money: the API layer converts its
// payload (and Money's constructor validates), so the use case never sees
// raw amount/currency strings.
public record AddTierCommand(String name, Money price, int quantity, int maxPerBooking) {
}
