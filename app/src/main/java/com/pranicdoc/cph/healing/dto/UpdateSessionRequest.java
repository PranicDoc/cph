package com.pranicdoc.cph.healing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** Corrected PRE/POST scans for a session that was already saved. */
public record UpdateSessionRequest(
        @NotNull @Valid
        List<ScanRequest> scans,
        /** A corrected scan date; null leaves the saved one as it is. */
        LocalDate scanDate
) {
    public UpdateSessionRequest(List<ScanRequest> scans) {
        this(scans, null);
    }
}
