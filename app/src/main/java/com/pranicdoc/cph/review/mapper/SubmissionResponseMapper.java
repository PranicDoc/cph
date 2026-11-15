package com.pranicdoc.cph.review.mapper;

import com.pranicdoc.cph.review.dto.RevisionResponse;
import com.pranicdoc.cph.review.dto.SubmissionOutcome;
import com.pranicdoc.cph.review.dto.SubmissionResponse;
import com.pranicdoc.cph.review.model.ReviewRevision;
import com.pranicdoc.cph.review.model.ReviewSubmission;

import java.util.List;

/** Builds the submission response shared by the healer-side and instructor-side services. */
public final class SubmissionResponseMapper {

    private SubmissionResponseMapper() {
    }

    public static SubmissionResponse toResponse(ReviewSubmission s, List<ReviewRevision> revs) {
        return toResponse(s, revs, null);
    }

    /** {@code outcome} says what a submit did; it is null for every other read. */
    public static SubmissionResponse toResponse(ReviewSubmission s, List<ReviewRevision> revs,
                                                SubmissionOutcome outcome) {
        int latest = revs.stream().mapToInt(ReviewRevision::getRevisionNo).max().orElse(0);
        return new SubmissionResponse(s.getId(), s.getCaseId(), s.getHealerId(), s.getReviewerId(), s.getStage(),
                s.getStatus(), s.isDismissedFromApproved(), s.getDismissReason(), s.getSubmittedVia(), latest,
                s.getCreatedAt(), s.getUpdatedAt(),
                revs.stream().map(r -> new RevisionResponse(r.getRevisionNo(), r.getPdfJobId(),
                        r.getSubmittedVia(), r.getSubmittedAt())).toList(), outcome);
    }
}
