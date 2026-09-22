package com.ticketly.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Entity = class, not record (§6.1): Hibernate needs a no-arg constructor,
// mutable fields for dirty checking, identity-based equality and proxying —
// all things records deliberately forbid.
@Entity
@Table(name = "venues")
public class Venue {

	@Id
	private UUID id;

	@Column(nullable = false, length = 120)
	private String name;

	// @Embedded pulls the Address record's components in as columns of THIS
	// table — no join, no second entity, still a typed value object in Java.
	@Embedded
	private Address address;

	@Column(nullable = false)
	private int capacity;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	// JPA-only constructor: protected so application code cannot create a
	// half-initialised Venue, but Hibernate (which subclasses/reflects) can.
	protected Venue() {
	}

	public Venue(String name, Address address, int capacity) {
		// App-generated UUID (§5.3): the entity is complete and identifiable
		// from birth, no DB round-trip needed to know its id.
		this.id = UUID.randomUUID();
		this.name = name;
		this.address = address;
		this.capacity = capacity;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Address getAddress() {
		return address;
	}

	public int getCapacity() {
		return capacity;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	// ID-only equality (§6.1): two instances with the same id are the same
	// row, whatever state each happens to hold in memory. No setters exist —
	// nothing about a venue is mutable in F-02.
	@Override
	public boolean equals(Object other) {
		return other instanceof Venue venue && Objects.equals(id, venue.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

}
