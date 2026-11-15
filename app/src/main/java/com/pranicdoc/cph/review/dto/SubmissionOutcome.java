package com.pranicdoc.cph.review.dto;

/** What a submit did, so the caller can say it. Null on plain reads. */
public enum SubmissionOutcome {
    /** A new submission was opened. */
    CREATED,
    /** An open submission got a new revision. */
    REVISION_ADDED,
    /** The case is unchanged since the latest revision: nothing was added. */
    UNCHANGED,
    /** The case is unchanged, but changes were requested: review was asked for again. */
    REREQUESTED
}
