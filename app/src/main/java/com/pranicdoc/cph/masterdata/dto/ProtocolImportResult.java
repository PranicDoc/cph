package com.pranicdoc.cph.masterdata.dto;

import java.util.List;

public record ProtocolImportResult(
        int totalRows,
        int created,
        int updated,
        int skipped,
        List<String> errors
) {}
