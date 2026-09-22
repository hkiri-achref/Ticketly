package com.ticketly.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketly.catalog.application.AddTierCommand;
import com.ticketly.catalog.application.CreateEventCommand;
import com.ticketly.catalog.application.EventService;
import com.ticketly.catalog.domain.Address;
import com.ticketly.catalog.domain.CapacityExceededException;
import com.ticketly.catalog.domain.Event;
import com.ticketly.catalog.domain.InvalidEventPeriodException;
import com.ticketly.catalog.domain.Money;
import com.ticketly.catalog.domain.Venue;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Web slice only: controller + advice + MockMvc, service mocked. What is under
// test is HTTP mapping — status codes, Location, JSON shape, validation and
// the ProblemDetail translations — not business logic (EventTest owns that).
@WebMvcTest(EventController.class)
class EventControllerTest {

	private static final Instant STARTS_AT = Instant.parse("2027-06-01T19:00:00Z");
	private static final Instant ENDS_AT = STARTS_AT.plus(Duration.ofHours(3));
	private static final Money PRICE = Money.of(new BigDecimal("49.90"), "EUR");

	private static final String VALID_EVENT_BODY =
			"""
			{
			  "title": "Concert",
			  "description": "A night of music",
			  "startsAt": "2027-06-01T19:00:00Z",
			  "endsAt": "2027-06-01T22:00:00Z",
			  "venueId": "%s"
			}
			""";

	private static final String VALID_TIER_BODY =
			"""
			{
			  "name": "Standard",
			  "price": {"amount": 49.90, "currency": "EUR"},
			  "quantity": 60
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EventService service;

	private static Event draftEvent() {
		var venue = new Venue("Small Hall", new Address("1 Main St", "Paris", "France"), 100);
		return new Event(EventController.DEV_ORGANIZER, "Concert", "A night of music", STARTS_AT, ENDS_AT, venue);
	}

	@Test
	void given_validBody_when_postEvent_then_returns201WithLocationAndDevOrganizer() throws Exception {
		// given
		var event = draftEvent();
		given(service.create(any())).willReturn(event);

		// when
		var result = mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
				.content(VALID_EVENT_BODY.formatted(event.getVenue().getId())));

		// then
		result.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/events/" + event.getId()))
				.andExpect(jsonPath("$.id").value(event.getId().toString()))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.venueId").value(event.getVenue().getId().toString()))
				.andExpect(jsonPath("$.tiers").isEmpty());
		var captor = ArgumentCaptor.forClass(CreateEventCommand.class);
		then(service).should().create(captor.capture());
		assertThat(captor.getValue().organizerId()).isEqualTo("dev-organizer");
	}

	@Test
	void given_invalidBody_when_postEvent_then_returns400ProblemDetailWithFieldErrors() throws Exception {
		// given
		String invalid =
				"""
				{"title": "ab", "startsAt": "2027-06-01T19:00:00Z"}
				""";

		// when
		var result = mockMvc.perform(
				post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(invalid));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.errors.length()").value(3))
				.andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'endsAt')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'venueId')]").exists());
	}

	@Test
	void given_unknownVenue_when_postEvent_then_returns404ProblemDetail() throws Exception {
		// given
		var venueId = UUID.randomUUID();
		given(service.create(any())).willThrow(new EntityNotFoundException("Venue %s not found".formatted(venueId)));

		// when
		var result = mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
				.content(VALID_EVENT_BODY.formatted(venueId)));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource not found"))
				.andExpect(jsonPath("$.detail").value("Venue %s not found".formatted(venueId)));
	}

	@Test
	void given_validBody_when_putEvent_then_returns200WithUpdatedEvent() throws Exception {
		// given
		var event = draftEvent();
		event.update("Concert (moved)", null, STARTS_AT, ENDS_AT);
		given(service.update(eq(event.getId()), any())).willReturn(event);
		String body =
				"""
				{"title": "Concert (moved)", "startsAt": "2027-06-01T19:00:00Z", "endsAt": "2027-06-01T22:00:00Z"}
				""";

		// when
		var result = mockMvc.perform(
				put("/api/v1/events/{id}", event.getId()).contentType(MediaType.APPLICATION_JSON).content(body));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Concert (moved)"))
				.andExpect(jsonPath("$.description").doesNotExist());
	}

	@Test
	void given_endBeforeStart_when_putEvent_then_returns422ProblemDetail() throws Exception {
		// given
		var id = UUID.randomUUID();
		given(service.update(eq(id), any())).willThrow(new InvalidEventPeriodException(ENDS_AT, STARTS_AT));
		String body =
				"""
				{"title": "Concert", "startsAt": "2027-06-01T22:00:00Z", "endsAt": "2027-06-01T19:00:00Z"}
				""";

		// when
		var result = mockMvc.perform(
				put("/api/v1/events/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body));

		// then
		result.andExpect(status().isUnprocessableContent())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.title").value("Business rule violated"))
				.andExpect(jsonPath("$.detail").value(containsString("end after it starts")));
	}

	@Test
	void given_validTierWithoutMaxPerBooking_when_postTier_then_returns201WithTiersAndDefaultOf8()
			throws Exception {
		// given
		var event = draftEvent();
		event.addTier("Standard", PRICE, 60, 8);
		given(service.addTier(eq(event.getId()), any())).willReturn(event);

		// when
		var result = mockMvc.perform(post("/api/v1/events/{id}/tiers", event.getId())
				.contentType(MediaType.APPLICATION_JSON).content(VALID_TIER_BODY));

		// then
		result.andExpect(status().isCreated())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(jsonPath("$.tiers.length()").value(1))
				.andExpect(jsonPath("$.tiers[0].name").value("Standard"))
				.andExpect(jsonPath("$.tiers[0].price.amount").value(49.90))
				.andExpect(jsonPath("$.tiers[0].price.currency").value("EUR"));
		var captor = ArgumentCaptor.forClass(AddTierCommand.class);
		then(service).should().addTier(eq(event.getId()), captor.capture());
		assertThat(captor.getValue().maxPerBooking()).isEqualTo(8);
		assertThat(captor.getValue().price()).isEqualTo(PRICE);
	}

	@Test
	void given_invalidTierBody_when_postTier_then_returns400WithNestedPriceErrors() throws Exception {
		// given
		String invalid =
				"""
				{"name": "", "price": {"amount": 1.999, "currency": "eur"}, "quantity": 0, "maxPerBooking": 101}
				""";

		// when
		var result = mockMvc.perform(post("/api/v1/events/{id}/tiers", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content(invalid));

		// then
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(5))
				.andExpect(jsonPath("$.errors[?(@.field == 'price.amount')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'price.currency')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'maxPerBooking')]").exists());
	}

	@Test
	void given_capacityExceeded_when_postTier_then_422ProblemDetail() throws Exception {
		// given
		var id = UUID.randomUUID();
		given(service.addTier(eq(id), any())).willThrow(new CapacityExceededException(60, 100, 100));

		// when
		var result = mockMvc.perform(post("/api/v1/events/{id}/tiers", id)
				.contentType(MediaType.APPLICATION_JSON).content(VALID_TIER_BODY));

		// then
		result.andExpect(status().isUnprocessableContent())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.title").value("Business rule violated"))
				.andExpect(jsonPath("$.detail").value(containsString("capacity 100")));
	}

	@Test
	void given_eventWithTier_when_getEvent_then_returns200IncludingTiers() throws Exception {
		// given
		var event = draftEvent();
		event.addTier("Standard", PRICE, 60, 8);
		given(service.getById(event.getId())).willReturn(event);

		// when
		var result = mockMvc.perform(get("/api/v1/events/{id}", event.getId()));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Concert"))
				.andExpect(jsonPath("$.tiers[0].quantity").value(60))
				.andExpect(jsonPath("$.tiers[0].maxPerBooking").value(8));
	}

	@Test
	void given_unknownId_when_getEvent_then_returns404ProblemDetail() throws Exception {
		// given
		var id = UUID.randomUUID();
		given(service.getById(id)).willThrow(new EntityNotFoundException("Event %s not found".formatted(id)));

		// when
		var result = mockMvc.perform(get("/api/v1/events/{id}", id));

		// then
		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource not found"));
	}

}
