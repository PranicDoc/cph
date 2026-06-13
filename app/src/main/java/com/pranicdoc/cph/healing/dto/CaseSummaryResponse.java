package com.pranicdoc.cph.healing.dto;

import com.pranicdoc.cph.common.enums.Gender;
import com.pranicdoc.cph.common.enums.MaritalStatus;
import com.pranicdoc.cph.healing.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CaseSummaryResponse(
        String patientFirstName,
        String patientLastName,
        LocalDate dob,
        Gender gender,
        MaritalStatus maritalStatus,
        String contact,
        String occupation,
        UUID caseId,
        String medicalCondition,
        String labFindings,
        Boolean complementaryTreatments,
        String complementaryDetails,
        Boolean infectiousIllness,
        String infectiousDetails,
        String basicBookPg,
        String advancedBookPg,
        String psychotherapyPg,
        Instant caseCreatedAt,
        CaseMedicalSnapshot medicalSnapshot,
        List<CaseSymptom> symptoms,
        List<CaseMedication> medications,
        List<HealingSession> sessions,
        CaseClosure closure
) {}
