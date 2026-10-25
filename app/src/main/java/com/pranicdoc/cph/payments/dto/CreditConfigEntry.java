package com.pranicdoc.cph.payments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One row of creditService's global config (GET /credits/admin/config). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreditConfigEntry(
        @JsonProperty("config_key") String configKey,
        @JsonProperty("config_value") String configValue
) {
}
