package com.ticketly.catalog.api.event;

import com.ticketly.catalog.application.event.EventService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// TODO: F-06 — no security yet — every endpoint is public until the OIDC
// resource-server feature lands.
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

	// TODO: F-07 — replace with the JWT subject of the authenticated organizer.
	static final String DEV_ORGANIZER = "dev-organizer";

	private final EventService service;

	public EventController(EventService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
		var event = service.create(request.toCommand(DEV_ORGANIZER));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(event.getId())
				.toUri();
		return ResponseEntity.created(location).body(EventResponse.from(event));
	}

	@PutMapping("/{id}")
	public EventResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request) {
		return EventResponse.from(service.update(id, request.toCommand()));
	}

	// 201 without Location: a tier has no URI of its own (it is only reachable
	// through its event), so the whole updated event is the representation.
	@PostMapping("/{id}/tiers")
	@ResponseStatus(HttpStatus.CREATED)
	public EventResponse addTier(@PathVariable UUID id, @Valid @RequestBody AddTierRequest request) {
		return EventResponse.from(service.addTier(id, request.toCommand()));
	}

	@GetMapping("/{id}")
	public EventResponse getById(@PathVariable UUID id) {
		return EventResponse.from(service.getById(id));
	}

}
