package com.ticketly.catalog.domain.event;

import com.ticketly.catalog.domain.common.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

// Child of the Event aggregate. The constructor is package-private on purpose:
// the ONLY way to obtain a TicketTier is Event.addTier(...), which is where the
// capacity invariant lives. Nothing outside the domain package can bypass it.
@Entity
@Table(name = "ticket_tiers")
public class TicketTier {

	@Id
	private UUID id;

	// LAZY is the right default for every @ManyToOne: loading a tier must not
	// drag its whole event (and, transitively, the venue) out of the database.
	// JPA's default for @ManyToOne is EAGER, so this has to be spelled out.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	@Column(nullable = false, length = 80)
	private String name;

	// @AttributeOverride renames the embeddable's columns for THIS usage: the
	// Money record says "amount"/"currency", the table says price_amount /
	// price_currency. Repeatable since JPA 2.2, so no @AttributeOverrides wrapper.
	@Embedded
	@AttributeOverride(name = "amount",
			column = @Column(name = "price_amount", nullable = false, precision = 12, scale = 2))
	@AttributeOverride(name = "currency",
			column = @Column(name = "price_currency", nullable = false, length = 3))
	private Money price;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "max_per_booking", nullable = false)
	private int maxPerBooking;

	protected TicketTier() {
	}

	TicketTier(Event event, String name, Money price, int quantity, int maxPerBooking) {
		this.id = UUID.randomUUID();
		this.event = event;
		this.name = name;
		this.price = price;
		this.quantity = quantity;
		this.maxPerBooking = maxPerBooking;
	}

	public UUID getId() {
		return id;
	}

	public Event getEvent() {
		return event;
	}

	public String getName() {
		return name;
	}

	public Money getPrice() {
		return price;
	}

	public int getQuantity() {
		return quantity;
	}

	public int getMaxPerBooking() {
		return maxPerBooking;
	}

	// ID-only equality (§6.1), same reasoning as Venue.
	@Override
	public boolean equals(Object other) {
		return other instanceof TicketTier tier && Objects.equals(id, tier.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

}
