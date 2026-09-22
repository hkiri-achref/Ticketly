package com.ticketly.catalog.api.event;

import com.ticketly.catalog.domain.common.Money;
import java.math.BigDecimal;

// Separate from the domain Money on purpose (§6.3): the wire shape may evolve
// (formatted amounts, symbols) without touching the value type.
public record MoneyResponse(BigDecimal amount, String currency) {

	public static MoneyResponse from(Money money) {
		return new MoneyResponse(money.amount(), money.currency());
	}

}
