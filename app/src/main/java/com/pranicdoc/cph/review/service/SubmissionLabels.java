package com.pranicdoc.cph.review.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.review.dto.SubmissionOutcome;
import com.pranicdoc.cph.review.dto.SubmissionResponse;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewSubmission;

import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.pranicdoc.cph.review.mapper.SubmissionResponseMapper.toResponse;

/**
 * The names and case titles of a set of submissions, loaded with one query per kind. It decides nothing
 * about access: build it only from submissions the caller has already been allowed to see.
 */
record SubmissionLabels(Map<UUID, String> names, Map<UUID, HealingCase> cases) {

    static SubmissionLabels of(HealerRepository healers, CaseRepository cases, List<ReviewSubmission> rows) {
        Set<UUID> people = rows.stream()
                .flatMap(s -> Stream.of(s.getHealerId(), s.getReviewerId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return new SubmissionLabels(namesOf(healers, people), casesOf(cases, rows));
    }

    static Map<UUID, String> namesOf(HealerRepository healers, Collection<UUID> healerIds) {
        return healers.findAllById(healerIds).stream()
                .collect(Collectors.toMap(Healer::getId, h -> h.getFullName() == null ? "" : h.getFullName()));
    }

    static Map<UUID, HealingCase> casesOf(CaseRepository cases, List<ReviewSubmission> rows) {
        return cases.findAllById(rows.stream().map(ReviewSubmission::getCaseId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(HealingCase::getId, Function.identity()));
    }

    /** The case's medical condition is the label healers see for a case; fall back to its creation date. */
    static String caseTitle(HealingCase c) {
        if (c == null) {
            return "Case";
        }
        String condition = c.getMedicalCondition();
        if (condition != null && !condition.isBlank()) {
            return condition;
        }
        return c.getCreatedAt() == null ? "Case"
                : "Case of " + c.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    SubmissionResponse respond(ReviewSubmission s, List<ReviewRevision> revisions) {
        return respond(s, revisions, null);
    }

    /** A name is null when there is no such account: no reviewer, or one since deleted. */
    SubmissionResponse respond(ReviewSubmission s, List<ReviewRevision> revisions, SubmissionOutcome outcome) {
        return toResponse(s, revisions, outcome, caseTitle(cases.get(s.getCaseId())), names.get(s.getHealerId()),
                s.getReviewerId() == null ? null : names.get(s.getReviewerId()));
    }
}
