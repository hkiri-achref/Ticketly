package com.ticketly.catalog.api.venue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketly.catalog.application.venue.VenueService;
import com.ticketly.catalog.config.WebConfig;
import com.ticketly.catalog.domain.venue.Address;
import com.ticketly.catalog.domain.venue.Venue;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Boot 4.1 per-module test package (was o.s.boot.test.autoconfigure.web.servlet).
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// @WebMvcTest = web slice only: this controller, the @RestControllerAdvice and
// MockMvc — no JPA, no DB, so it runs in milliseconds. The service is replaced
// with @MockitoBean (Spring Framework 6.2+ successor of Boot's @MockBean).
// WebConfig is imported so the VIA_DTO page shape is part of what we test.
@WebMvcTest(VenueController.class)
@Import(WebConfig.class)
class VenueControllerTest {

	// Text block (Java 15+): multi-line JSON without escape noise. Google style
	// puts the opening quotes on their own line and aligns both delimiters.
	private static final String VALID_BODY =
			"""
			{
			  "name": "Le Zénith",
			  "capacity": 6293,
			  "address": {"street": "211 Avenue Jean Jaurès", "city": "Paris", "country": "France"}
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VenueService service;

	@Test
	void given_validBody_when_postVenue_then_returns201WithLocationAndBody() throws Exception {
		// given
		var venue = new Venue("Le Zénith", new Address("211 Avenue Jean Jaurès", "Paris", "France"), 6293);
		given(service.create(any())).willReturn(venue);

		// when
		var result = mockMvc.perform(
				post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));

		// then
		result.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/venues/" + venue.getId()))
				.andExpect(jsonPath("$.id").value(venue.getId().toString()))
				.andExpect(jsonPath("$.address.city").value("Paris"));
	}

	@Test
	void given_invalidBody_when_postVenue_then_returns400ProblemDetailWithFieldErrors() throws Exception {
		// given
		String invalid =
				"""
				{
				  "name": "ab",
				  "capacity": 0,
				  "address": {"street": "", "city": "Paris", "country": "France"}
				}
				""";

		// when
		var result = mockMvc.perform(
				post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content(invalid));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors.length()").value(3))
				.andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'capacity')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'address.street')]").exists());
	}

	@Test
	void given_unknownId_when_getVenue_then_returns404ProblemDetail() throws Exception {
		// given
		var id = UUID.randomUUID();
		given(service.getById(id)).willThrow(new EntityNotFoundException("Venue %s not found".formatted(id)));

		// when
		var result = mockMvc.perform(get("/api/v1/venues/{id}", id));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.title").value("Resource not found"));
	}

	@Test
	void given_venueInCity_when_listByCity_then_returnsStablePagedModelShape() throws Exception {
		// given
		var venue = new Venue("Le Zénith", new Address("211 Avenue Jean Jaurès", "Paris", "France"), 6293);
		given(service.list(eq("Paris"), any())).willReturn(new PageImpl<>(List.of(venue), PageRequest.of(0, 20), 1));

		// when
		var result = mockMvc.perform(get("/api/v1/venues").param("city", "Paris"));

		// then: VIA_DTO shape — content[] + page{} instead of PageImpl internals.
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].name").value("Le Zénith"))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

}
