package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.healing.dto.HealerProtocolUnderstandingRequest.ChakraColorSelection;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every chakra needs the healer's reason for working on it, however short, and at most 300 characters. */
class HealerProtocolUnderstandingRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<HealerProtocolUnderstandingRequest>> validate(String reason) {
        return validator.validate(new HealerProtocolUnderstandingRequest(
                List.of(new ChakraColorSelection("Heart", List.of(), List.of(), reason))));
    }

    @Test
    void aReasonIsRequiredForEveryChakra() {
        for (String missing : new String[] {null, "", "   "}) {
            Set<ConstraintViolation<HealerProtocolUnderstandingRequest>> violations = validate(missing);
            assertEquals(1, violations.size(), "reason=" + missing);
            assertEquals("Add why you worked on this chakra", violations.iterator().next().getMessage());
        }
    }

    @Test
    void aReasonUpTo300CharactersIsAccepted() {
        assertTrue(validate("Grief held after the loss").isEmpty());
        assertTrue(validate("x".repeat(300)).isEmpty());
        assertEquals(1, validate("x".repeat(301)).size());
    }
}
