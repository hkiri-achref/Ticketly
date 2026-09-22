package com.ticketly.catalog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

// Serializing PageImpl directly is an unstable contract (Spring logs a warning
// and may change the shape between versions). VIA_DTO (since Boot 3.3) converts
// every returned Page to the stable PagedModel JSON: {content: [...], page:
// {size, number, totalElements, totalPages}}.
@Configuration(proxyBeanMethods = false)
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {
}
