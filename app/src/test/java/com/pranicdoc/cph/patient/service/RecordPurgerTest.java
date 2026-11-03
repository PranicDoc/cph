package com.pranicdoc.cph.patient.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordPurgerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RecordPurger purger = new RecordPurger(jdbc);

    private List<String> erasedTables() {
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
        purger.purgePatient(id);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), any(Object[].class));
        return sql.getAllValues().stream()
                .filter(s -> s.startsWith("DELETE FROM"))
                .map(s -> s.substring("DELETE FROM ".length()).split(" ")[0])
                .collect(Collectors.toList());
    }

    @Test
    void everyTableThatHoldsAPatientsDataIsErased() {
        assertThat(erasedTables()).contains(
                "patient", "patient_medical_history", "healing_case", "case_closure", "case_medical_snapshot",
                "case_medication", "case_symptom", "declaration", "feedback_link", "healer_protocol_understanding",
                "healing_details", "treatment_step", "treatment_step_targets", "healing_session", "pdf_job",
                "chakra_scan", "chakra_reading", "protocol_entry", "protocol_color", "qualitative_feedback",
                "symptom_feedback");
    }

    @Test
    void childRowsAreErasedBeforeTheRowTheyPointAt() {
        List<String> order = erasedTables();
        assertThat(order.indexOf("chakra_reading")).isLessThan(order.indexOf("chakra_scan"));
        assertThat(order.indexOf("chakra_scan")).isLessThan(order.indexOf("healing_session"));
        assertThat(order.indexOf("protocol_color")).isLessThan(order.indexOf("protocol_entry"));
        assertThat(order.indexOf("treatment_step_targets")).isLessThan(order.indexOf("treatment_step"));
        assertThat(order.indexOf("healing_session")).isLessThan(order.indexOf("healing_case"));
        assertThat(order.indexOf("healing_case")).isLessThan(order.lastIndexOf("patient"));
        assertThat(order.lastIndexOf("patient")).isEqualTo(order.size() - 1);
    }

    @Test
    void historyCopiesOfTheErasedRowsAreErasedToo() {
        assertThat(erasedTables()).contains("patient_aud", "healing_case_aud", "healing_session_aud");
    }

    @Test
    void consentRecordsAndTheAuditLogAreKeptOnPurpose() {
        assertThat(erasedTables()).doesNotContain("consent", "audit_log", "reading_session");
    }
}
