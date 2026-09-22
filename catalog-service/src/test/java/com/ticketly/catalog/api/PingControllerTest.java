package com.ticketly.catalog.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketly.catalog.config.CatalogProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

// Web slice for the F-01 ping endpoint. Slice tests deliberately ignore the
// application class's @ConfigurationPropertiesScan (it would drag every config
// record into every slice), so the one properties record this controller needs
// is enabled explicitly; application.yml still supplies ticketly.catalog.name.
@WebMvcTest(PingController.class)
@EnableConfigurationProperties(CatalogProperties.class)
class PingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void given_configuredServiceName_when_getPing_then_returnsNameAndTimestamp() throws Exception {
		// given: ticketly.catalog.name bound from application.yml

		// when
		var result = mockMvc.perform(get("/api/v1/ping"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.service").value("catalog-service"))
				.andExpect(jsonPath("$.time").isString());
	}

}
