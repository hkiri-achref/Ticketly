package com.ticketly.catalog.application.venue;

import com.ticketly.catalog.domain.venue.Venue;
import com.ticketly.catalog.persistence.venue.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Transactional lives HERE, on the application service, never on controllers
// (§5.3): the service method is the unit of work; the controller only maps
// HTTP. Class-level readOnly=true is the safe default (no flush, hints the
// driver); create() overrides it read-write.
@Service
@Transactional(readOnly = true)
public class VenueService {

	private final VenueRepository repository;

	public VenueService(VenueRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public Venue create(CreateVenueCommand command) {
		return repository.save(new Venue(command.name(), command.address(), command.capacity()));
	}

	public Venue getById(UUID id) {
		// Explicit orElseThrow instead of getReferenceById: the lazy proxy of
		// getReferenceById would only fail far from here, on first field access.
		return repository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Venue %s not found".formatted(id)));
	}

	public Page<Venue> list(String city, Pageable pageable) {
		if (city == null || city.isBlank()) {
			return repository.findAll(pageable);
		}
		return repository.findByAddressCityIgnoreCase(city, pageable);
	}

}
