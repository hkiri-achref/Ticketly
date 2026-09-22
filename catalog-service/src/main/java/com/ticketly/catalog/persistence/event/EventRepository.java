package com.ticketly.catalog.persistence.event;

import com.ticketly.catalog.domain.event.Event;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

// One repository per AGGREGATE, not per table: there is deliberately no
// TicketTierRepository. Tiers are reached, created and deleted only through
// their Event, so the persistence API cannot be used to bypass the invariant.
public interface EventRepository extends JpaRepository<Event, UUID> {

	// @EntityGraph overrides the mapping's LAZY for THIS query only: Hibernate
	// emits one SELECT with a LEFT JOIN on ticket_tiers, so the tiers are
	// initialised before the transaction ends. The "WithTiers" part of the
	// name is ignored by the query derivation — it is documentation for humans.
	// The equivalent JPQL "join fetch" form is discussed in the learning note.
	@EntityGraph(attributePaths = "tiers")
	Optional<Event> findWithTiersById(UUID id);

}
