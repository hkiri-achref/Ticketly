package com.ticketly.catalog.api;

import com.ticketly.catalog.domain.TicketTier;
import java.util.UUID;

public record TierResponse(UUID id, String name, MoneyResponse price, int quantity, int maxPerBooking) {

	public static TierResponse from(TicketTier tier) {
		return new TierResponse(tier.getId(), tier.getName(), MoneyResponse.from(tier.getPrice()),
				tier.getQuantity(), tier.getMaxPerBooking());
	}

}
