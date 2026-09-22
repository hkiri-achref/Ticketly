package com.ticketly.catalog.api.ping;

import com.ticketly.catalog.config.CatalogProperties;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PingController {

	private final CatalogProperties properties;

	// Constructor injection (project rule): dependencies are explicit, final,
	// and the class is trivially testable without a Spring context.
	public PingController(CatalogProperties properties) {
		this.properties = properties;
	}

	@GetMapping("/ping")
	public PingResponse ping() {
		// Instant (not LocalDateTime): timestamps are absolute points in time;
		// time zones belong at the API edge only when a human needs them (§5.3).
		return new PingResponse(properties.name(), Instant.now());
	}

}
