package com.ticketly.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Pure unit tests: a record's compact constructor is plain Java, no Spring, no
// Hibernate — the whole value type is verified in milliseconds.
class MoneyTest {

	@Test
	void given_wholeAmount_when_created_then_amountIsNormalisedToTwoDecimals() {
		// given
		var amount = new BigDecimal("10");

		// when
		var money = Money.of(amount, "EUR");

		// then: 10 and 10.00 are the same money, so they must be equal records
		assertThat(money.amount()).isEqualTo(new BigDecimal("10.00"));
		assertThat(money).isEqualTo(Money.of(new BigDecimal("10.00"), "EUR"));
	}

	@Test
	void given_negativeAmount_when_created_then_throwsIllegalArgument() {
		// given
		var amount = new BigDecimal("-0.01");

		// when
		var thrown = assertThatThrownBy(() -> Money.of(amount, "EUR"));

		// then
		thrown.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negative");
	}

	@Test
	void given_threeDecimals_when_created_then_throwsIllegalArgument() {
		// given
		var amount = new BigDecimal("9.999");

		// when
		var thrown = assertThatThrownBy(() -> Money.of(amount, "EUR"));

		// then
		thrown.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("two decimals");
	}

	@ParameterizedTest
	@ValueSource(strings = {"eur", "EU", "EURO", "E1R", ""})
	void given_malformedCurrency_when_created_then_throwsIllegalArgument(String currency) {
		// given
		var amount = BigDecimal.TEN;

		// when
		var thrown = assertThatThrownBy(() -> Money.of(amount, currency));

		// then
		thrown.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISO-4217");
	}

	@Test
	void given_nullComponents_when_created_then_throwsNullPointer() {
		// given
		var amount = BigDecimal.TEN;

		// when
		var nullAmount = assertThatThrownBy(() -> Money.of(null, "EUR"));
		var nullCurrency = assertThatThrownBy(() -> Money.of(amount, null));

		// then
		nullAmount.isInstanceOf(NullPointerException.class).hasMessageContaining("amount");
		nullCurrency.isInstanceOf(NullPointerException.class).hasMessageContaining("currency");
	}

	@Test
	void given_sameCurrency_when_plus_then_returnsSum() {
		// given
		var ten = Money.of(new BigDecimal("10.50"), "EUR");
		var five = Money.of(new BigDecimal("5.25"), "EUR");

		// when
		var sum = ten.plus(five);

		// then
		assertThat(sum).isEqualTo(Money.of(new BigDecimal("15.75"), "EUR"));
	}

	@Test
	void given_differentCurrencies_when_plus_then_throwsIllegalArgument() {
		// given
		var euros = Money.of(BigDecimal.TEN, "EUR");
		var dollars = Money.of(BigDecimal.TEN, "USD");

		// when
		var thrown = assertThatThrownBy(() -> euros.plus(dollars));

		// then
		thrown.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currencies differ");
	}

	@Test
	void given_price_when_timesThree_then_returnsTripleInSameCurrency() {
		// given
		var price = Money.of(new BigDecimal("19.99"), "EUR");

		// when
		var total = price.times(3);

		// then
		assertThat(total).isEqualTo(Money.of(new BigDecimal("59.97"), "EUR"));
	}

	@Test
	void given_negativeFactor_when_times_then_throwsIllegalArgument() {
		// given
		var price = Money.of(BigDecimal.ONE, "EUR");

		// when
		var thrown = assertThatThrownBy(() -> price.times(-1));

		// then
		thrown.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("factor");
	}

}
