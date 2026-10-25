package com.pranicdoc.cph.healing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Corrected PRE/POST scans for a session that was already saved. */
public record UpdateSessionRequest(
        @NotNull @Valid
        List<ScanRequest> scans
) {
}
