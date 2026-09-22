package com.ticketly.catalog.api;

import com.ticketly.catalog.application.AddTierCommand;
import com.ticketly.catalog.domain.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AddTierRequest(
		@NotBlank @Size(max = 80) String name,
		@NotNull @Valid MoneyPayload price,
		@Min(1) int quantity,
		@Min(1) @Max(100) Integer maxPerBooking) {

	private static final int DEFAULT_MAX_PER_BOOKING = 8;

	// maxPerBooking is optional in the JSON: Jackson passes null for a missing
	// component and the compact constructor substitutes the default, so the
	// rest of the code never sees a null. The boxed Integer is what makes
	// "absent" representable at all — an int would silently become 0.
	public AddTierRequest {
		if (name != null) {
			name = name.strip();
		}
		if (maxPerBooking == null) {
			maxPerBooking = DEFAULT_MAX_PER_BOOKING;
		}
	}

	public AddTierCommand toCommand() {
		return new AddTierCommand(name, Money.of(price.amount(), price.currency()), quantity, maxPerBooking);
	}

	// Bean Validation mirrors Money's own constructor checks so a bad price is
	// reported as a 400 with a field path (price.amount) instead of surfacing
	// as an IllegalArgumentException from the domain.
	public record MoneyPayload(
			@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
			@NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {
	}

}
