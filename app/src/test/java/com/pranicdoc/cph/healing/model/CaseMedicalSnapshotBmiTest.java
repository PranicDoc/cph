package com.pranicdoc.cph.healing.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CaseMedicalSnapshotBmiTest {

    @Test
    void bmiAndTagAreServedWithTheSnapshot() {
        CaseMedicalSnapshot s = CaseMedicalSnapshot.builder()
                .heightCm(new BigDecimal("162")).weightKg(new BigDecimal("71")).build();
        JsonNode json = new ObjectMapper().valueToTree(s);
        assertEquals(new BigDecimal("27.1"), json.get("bmi").decimalValue());
        assertEquals("Overweight", json.get("bmiCategory").asText());
    }

    @Test
    void noBmiWhenAMeasurementIsMissing() {
        CaseMedicalSnapshot s = CaseMedicalSnapshot.builder().heightCm(new BigDecimal("162")).build();
        assertNull(s.computeBmi());
        assertNull(s.computeBmiCategory());
    }
}
