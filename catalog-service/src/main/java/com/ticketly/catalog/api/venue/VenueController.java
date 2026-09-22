package com.ticketly.catalog.api.venue;

import com.ticketly.catalog.application.venue.VenueService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// TODO: F-06 — no security yet — every endpoint is public until the OIDC
// resource-server feature lands.
@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

	private final VenueService service;

	public VenueController(VenueService service) {
		this.service = service;
	}

	// @Valid triggers Bean Validation on the request record; a violation raises
	// MethodArgumentNotValidException before this method body ever runs.
	@PostMapping
	public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest request) {
		var venue = service.create(request.toCommand());
		// 201 + Location (REST contract for "created"): built from the current
		// request so it survives host/port/context-path changes.
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(venue.getId())
				.toUri();
		return ResponseEntity.created(location).body(VenueResponse.from(venue));
	}

	@GetMapping("/{id}")
	public VenueResponse getById(@PathVariable UUID id) {
		return VenueResponse.from(service.getById(id));
	}

	// Pageable is resolved by Spring Data web support straight from
	// ?page=&size=&sort= — no manual parsing. Serialized VIA_DTO (see
	// WebConfig) so the JSON page shape is a stable contract.
	@GetMapping
	public Page<VenueResponse> list(@RequestParam(required = false) String city, Pageable pageable) {
		return service.list(city, pageable).map(VenueResponse::from);
	}

}
