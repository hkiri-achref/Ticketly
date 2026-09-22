package com.ticketly.catalog.persistence.venue;

import com.ticketly.catalog.domain.venue.Venue;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

	// Derived query that walks INTO the embeddable: property path
	// address.city. IgnoreCase makes Hibernate compare with upper(), which the
	// idx_venues_city_upper functional index matches.
	Page<Venue> findByAddressCityIgnoreCase(String city, Pageable pageable);

}
