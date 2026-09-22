package com.ticketly.catalog.domain;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

// Money as a record @Embeddable (§6.2): an amount + currency is a VALUE — two
// equal amounts in the same currency are the same money — so record equality
// and immutability are exactly right. Hibernate calls the canonical
// constructor when it loads a row, so the compact-constructor validation below
// also runs on the read path: a corrupt row cannot become a Money in memory.
@Embeddable
public record Money(BigDecimal amount, String currency) {

	// ISO-4217 alphabetic code shape (EUR, USD, ...); java.util.Currency would
	// validate the full list, but the spec only asks for the shape.
	private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");
	private static final int MAX_SCALE = 2;

	// Compact constructor (records): runs BEFORE the implicit field assignments,
	// so it can validate and normalise the components. Normalising to two
	// decimals keeps record equality sane: 10 and 10.00 are the same money.
	public Money {
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(currency, "currency must not be null");
		if (amount.signum() < 0) {
			throw new IllegalArgumentException("amount must not be negative: " + amount);
		}
		if (amount.scale() > MAX_SCALE) {
			throw new IllegalArgumentException("amount must have at most two decimals: " + amount);
		}
		if (!CURRENCY_CODE.matcher(currency).matches()) {
			throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code: " + currency);
		}
		amount = amount.setScale(MAX_SCALE, RoundingMode.UNNECESSARY);
	}

	public static Money of(BigDecimal amount, String currency) {
		return new Money(amount, currency);
	}

	public Money plus(Money other) {
		if (!currency.equals(other.currency)) {
			throw new IllegalArgumentException(
					"cannot add %s to %s: currencies differ".formatted(other.currency, currency));
		}
		return new Money(amount.add(other.amount), currency);
	}

	public Money times(int factor) {
		if (factor < 0) {
			throw new IllegalArgumentException("factor must not be negative: " + factor);
		}
		return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
	}

}
