package com.pranicdoc.cph.common.validation;

import com.pranicdoc.cph.common.enums.DurationUnit;
import com.pranicdoc.cph.healing.dto.CaseClosureRequest;
import com.pranicdoc.cph.healing.dto.ChiefComplaintRequest;
import com.pranicdoc.cph.healing.dto.CaseUpdateRequest;
import com.pranicdoc.cph.healing.dto.MedicationRequest;
import com.pranicdoc.cph.healing.dto.SymptomRequest;
import com.pranicdoc.cph.patient.dto.MedicalHistoryRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The server refuses text past the limits, including inside nested lists. */
class TextLimitsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static String chars(int count) {
        return "x".repeat(count);
    }

    private static CaseUpdateRequest caseUpdate(String problem, List<SymptomRequest> symptoms,
                                                List<MedicationRequest> medications) {
        var complaints = problem == null ? null : List.of(new ChiefComplaintRequest(
                null, problem, 3, DurationUnit.MONTHS, null));
        return new CaseUpdateRequest(null, complaints, symptoms, null, null, null, null, null, null, null,
                false, null, false, null, medications, null);
    }

    @Test
    void textAtTheLimitIsAccepted() {
        assertThat(validator.validate(caseUpdate(chars(TextLimits.SHORT_TEXT), List.of(), List.of()))).isEmpty();
        assertThat(validator.validate(new CaseClosureRequest(chars(TextLimits.CLOSURE_COMMENTS), false))).isEmpty();
    }

    @Test
    void textPastTheLimitIsRefused_withAClearMessage() {
        var violations = validator.validate(caseUpdate(chars(TextLimits.SHORT_TEXT + 1), List.of(), List.of()));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Problem can be at most 100 characters");
        assertThat(validator.validate(new CaseClosureRequest(chars(TextLimits.CLOSURE_COMMENTS + 1), false))).hasSize(1);
    }

    @Test
    void nestedSymptomsAndMedicationsAreChecked() {
        var symptom = new SymptomRequest(chars(TextLimits.SYMPTOM_NAME + 1), null, null);
        var medication = new MedicationRequest("Name", chars(TextLimits.SHORT_TEXT + 1), null, null, null, null);

        assertThat(validator.validate(caseUpdate(null, List.of(symptom), List.of(medication)))).hasSize(2);
    }

    @Test
    void medicalHistoryNotesAreChecked() {
        var history = new MedicalHistoryRequest(null, null, null, null, null, null, null, null, null, null, null,
                chars(TextLimits.HISTORY_BOX + 1));

        assertThat(validator.validate(history)).hasSize(1);
    }

    @Test
    void patientHistoriesAreLimitedToAtMostTwoLines() {
        String atLimit = chars(TextLimits.HISTORY_LINE);
        String over = chars(TextLimits.HISTORY_LINE + 1);

        assertThat(validator.validate(historyWith(null, null, atLimit, atLimit, atLimit, atLimit))).isEmpty();
        assertThat(validator.validate(historyWith(null, null, over, over, over, over))).hasSize(4);
    }

    @Test
    void heightAndWeightAreOptionalButBounded() {
        assertThat(validator.validate(historyWith(null, null, null, null, null, null))).isEmpty();
        assertThat(validator.validate(historyWith(new BigDecimal("50"), new BigDecimal("2"), null, null, null, null))).isEmpty();
        assertThat(validator.validate(historyWith(new BigDecimal("250"), new BigDecimal("500"), null, null, null, null))).isEmpty();
        assertThat(validator.validate(historyWith(new BigDecimal("49.9"), new BigDecimal("1.9"), null, null, null, null))).hasSize(2);
        assertThat(validator.validate(historyWith(new BigDecimal("250.1"), new BigDecimal("500.1"), null, null, null, null))).hasSize(2);
    }

    private static MedicalHistoryRequest historyWith(BigDecimal height, BigDecimal weight, String past, String surgical,
                                                     String family, String psychosocial) {
        return new MedicalHistoryRequest(null, null, null, null, null, null, null, null, null, null, null, null,
                height, weight, past, surgical, family, psychosocial);
    }
}
