package com.pranicdoc.cph.common.validation;

import com.pranicdoc.cph.healing.dto.CaseClosureRequest;
import com.pranicdoc.cph.healing.dto.CaseUpdateRequest;
import com.pranicdoc.cph.healing.dto.MedicationRequest;
import com.pranicdoc.cph.healing.dto.SymptomRequest;
import com.pranicdoc.cph.patient.dto.MedicalHistoryRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The server refuses text past the limits, including inside nested lists. */
class TextLimitsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static String chars(int count) {
        return "x".repeat(count);
    }

    private static CaseUpdateRequest caseUpdate(String condition, List<SymptomRequest> symptoms,
                                                List<MedicationRequest> medications) {
        return new CaseUpdateRequest(null, condition, symptoms, null, null, null, null, null, null, null,
                false, null, false, null, medications, null);
    }

    @Test
    void textAtTheLimitIsAccepted() {
        assertThat(validator.validate(caseUpdate(chars(TextLimits.MEDIUM_BOX), List.of(), List.of()))).isEmpty();
        assertThat(validator.validate(new CaseClosureRequest(chars(TextLimits.CLOSURE_COMMENTS), false, null))).isEmpty();
    }

    @Test
    void textPastTheLimitIsRefused_withAClearMessage() {
        var violations = validator.validate(caseUpdate(chars(TextLimits.MEDIUM_BOX + 1), List.of(), List.of()));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Medical condition can be at most 500 characters");
        assertThat(validator.validate(new CaseClosureRequest(chars(TextLimits.CLOSURE_COMMENTS + 1), false, null))).hasSize(1);
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
}
