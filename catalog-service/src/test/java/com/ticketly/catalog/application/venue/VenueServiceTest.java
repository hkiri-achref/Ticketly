package com.ticketly.catalog.application.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.ticketly.catalog.domain.venue.Address;
import com.ticketly.catalog.domain.venue.Venue;
import com.ticketly.catalog.persistence.venue.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// Plain unit test: no Spring context at all. MockitoExtension creates the
// @Mock and @InjectMocks builds the service through its constructor, so this
// runs in milliseconds and tests only the service's own decisions.
@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

	private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

	@Mock
	private VenueRepository repository;

	@InjectMocks
	private VenueService service;

	@Test
	void given_validCommand_when_create_then_savesVenueBuiltFromCommand() {
		// given
		var command = new CreateVenueCommand("Le Zénith",
				new Address("211 Avenue Jean Jaurès", "Paris", "France"), 6293);
		// Echo back whatever entity the service built, so we can inspect it.
		given(repository.save(any(Venue.class))).willAnswer(invocation -> invocation.getArgument(0));

		// when
		var created = service.create(command);

		// then
		assertThat(created.getId()).isNotNull();
		assertThat(created.getName()).isEqualTo("Le Zénith");
		assertThat(created.getCapacity()).isEqualTo(6293);
		assertThat(created.getAddress()).isEqualTo(new Address("211 Avenue Jean Jaurès", "Paris", "France"));
		assertThat(created.getCreatedAt()).isNotNull();
	}

	@Test
	void given_existingId_when_getById_then_returnsVenue() {
		// given
		var venue = new Venue("Le Zénith", new Address("211 Avenue Jean Jaurès", "Paris", "France"), 6293);
		given(repository.findById(venue.getId())).willReturn(Optional.of(venue));

		// when
		var found = service.getById(venue.getId());

		// then
		assertThat(found).isSameAs(venue);
	}

	@Test
	void given_unknownId_when_getById_then_throwsEntityNotFoundNamingTheId() {
		// given
		var id = UUID.randomUUID();
		given(repository.findById(id)).willReturn(Optional.empty());

		// when
		var thrown = assertThatThrownBy(() -> service.getById(id));

		// then
		thrown.isInstanceOf(EntityNotFoundException.class).hasMessageContaining(id.toString());
	}

	// @ParameterizedTest runs the body once per source value: null, empty and
	// blank city must all mean "no filter", and one test documents that contract.
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "   "})
	void given_noCityFilter_when_list_then_returnsAllVenuesPaged(String city) {
		// given
		Page<Venue> all = new PageImpl<>(List.of(), FIRST_PAGE, 0);
		given(repository.findAll(FIRST_PAGE)).willReturn(all);

		// when
		var page = service.list(city, FIRST_PAGE);

		// then
		assertThat(page).isSameAs(all);
		then(repository).should().findAll(FIRST_PAGE);
	}

	@Test
	void given_cityFilter_when_list_then_delegatesToCaseInsensitiveCityQuery() {
		// given
		var venue = new Venue("Le Zénith", new Address("211 Avenue Jean Jaurès", "Paris", "France"), 6293);
		Page<Venue> parisPage = new PageImpl<>(List.of(venue), FIRST_PAGE, 1);
		given(repository.findByAddressCityIgnoreCase("paris", FIRST_PAGE)).willReturn(parisPage);

		// when
		var page = service.list("paris", FIRST_PAGE);

		// then
		assertThat(page.getContent()).containsExactly(venue);
	}

}
