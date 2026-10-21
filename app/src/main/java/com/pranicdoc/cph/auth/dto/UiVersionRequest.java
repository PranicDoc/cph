package com.pranicdoc.cph.auth.dto;

import com.pranicdoc.cph.common.enums.UiVersion;
import jakarta.validation.constraints.NotNull;

public record UiVersionRequest(@NotNull UiVersion uiVersion) {}
