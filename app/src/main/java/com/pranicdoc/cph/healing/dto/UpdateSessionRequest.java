package com.pranicdoc.cph.healing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** Corrected PRE/POST scans for a session that was already saved. */
public record UpdateSessionRequest(
        @NotNull @Valid
        List<ScanRequest> scans,
        /** A corrected session date (shown on the scan sheet); null leaves the saved one as it is. */
        LocalDate sessionDate
) {
    public UpdateSessionRequest(List<ScanRequest> scans) {
        this(scans, null);
    }
}
