package com.ticketly.catalog.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// A record as @ConfigurationProperties: Boot binds via the canonical constructor,
// so the config object is immutable — nothing can mutate it after startup.
// @Validated + @NotBlank make a missing/blank `ticketly.catalog.name` fail the
// application at boot instead of surfacing as a null somewhere at runtime.
@Validated
@ConfigurationProperties("ticketly.catalog")
public record CatalogProperties(@NotBlank String name) {
}
