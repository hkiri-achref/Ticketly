package com.ticketly.catalog.api.event;

import com.ticketly.catalog.application.event.EventService;
import com.ticketly.catalog.domain.event.RejectionReason;
import com.ticketly.catalog.domain.event.TransitionResult;
import com.ticketly.catalog.domain.event.TransitionResult.Ok;
import com.ticketly.catalog.domain.event.TransitionResult.Rejected;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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

	// POST, not PATCH: "publish" is a named action with side effects, not a
	// partial update of a status field. 200 with the full event (not 204) so
	// the client sees the new status and version without a second call.
	@PostMapping("/{id}/publish")
	public ResponseEntity<Object> publish(@PathVariable UUID id) {
		return toResponse(service.publish(id));
	}

	@PostMapping("/{id}/cancel")
	public ResponseEntity<Object> cancel(@PathVariable UUID id, @Valid @RequestBody CancelEventRequest request) {
		return toResponse(service.cancel(id, request.toCommand()));
	}

	// Switch as an EXPRESSION over a sealed type with RECORD PATTERNS: each
	// case both tests the subtype and destructures it into its components in
	// one line — no instanceof, no casts. There is deliberately no default
	// branch: the compiler knows every permitted subtype, so adding a third
	// outcome to TransitionResult fails compilation here until it is handled.
	// The body type is Object because the two arms return different
	// representations (EventResponse vs ProblemDetail).
	private static ResponseEntity<Object> toResponse(TransitionResult result) {
		return switch (result) {
			case Ok(var event) -> ResponseEntity.ok(EventResponse.from(event));
			case Rejected(var reason, var currentStatus) -> {
				var status = statusFor(reason);
				var problem = ProblemDetail.forStatusAndDetail(status, reason.message());
				problem.setTitle("Transition rejected");
				problem.setProperty("reason", reason);
				problem.setProperty("currentStatus", currentStatus);
				// yield hands the block's value to the switch expression.
				yield ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
			}
		};
	}

	// 422: the event's own data breaks a rule the client can fix (add a tier,
	// move the date). 409: the request conflicts with the CURRENT STATE of the
	// resource — the same meaning 409 has for a version conflict. Exhaustive
	// over the enum, again without default, for the same reason as above.
	private static HttpStatus statusFor(RejectionReason reason) {
		return switch (reason) {
			case NO_TIERS, STARTS_IN_PAST -> HttpStatus.UNPROCESSABLE_CONTENT;
			case ALREADY_PUBLISHED, ALREADY_CANCELLED -> HttpStatus.CONFLICT;
		};
	}

}
