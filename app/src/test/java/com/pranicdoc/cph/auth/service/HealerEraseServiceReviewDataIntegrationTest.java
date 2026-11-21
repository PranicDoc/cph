package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.patient.service.RecordPurger;
import com.pranicdoc.cph.review.erasure.ReviewErasureFixtures;
import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.Executor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/** Real erase SQL on H2 with the production foreign keys of the review tables added. */
@DataJpaTest
@ActiveProfiles("test")
@Import({RecordPurger.class, HealerEraseServiceReviewDataIntegrationTest.SameThreadExecutor.class})
class HealerEraseServiceReviewDataIntegrationTest {

    @TestConfiguration
    static class SameThreadExecutor {
        @Bean(name = "notificationExecutor")
        Executor notificationExecutor() {
            return Runnable::run;
        }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordPurger purger;
    @Autowired private PlatformTransactionManager txManager;
    @MockBean private SecurityContextUtil securityContextUtil;
    @MockBean private StorageService storage;

    private ReviewErasureFixtures fx;
    private HealerEraseService service;

    @BeforeEach
    void setUp() {
        fx = new ReviewErasureFixtures(jdbc);
        fx.addProductionForeignKeys();
        RestClient credit = Mockito.mock(RestClient.class, Mockito.RETURNS_DEEP_STUBS);
        service = new HealerEraseService(jdbc, purger, securityContextUtil, credit,
                new TransactionTemplate(txManager), Mockito.mock(CacheManager.class));
    }

    private String emailOf(UUID healer) {
        return healer + "@example.com";
    }

    /** The test transaction never commits, so complete it by hand the way Spring does. */
    private void commitByHand() {
        TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(RecordPurger.DeleteStoredFilesAfterCommit.class::isInstance)
                .forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    void eraseHealer_whoIsAPatientOwner_removesTheirReviewData() {
        UUID healer = fx.healer("HEALER");
        UUID instructor = fx.healer("HEALER");
        UUID patient = fx.patient(healer);
        UUID caseId = fx.healingCase(patient, healer);
        fx.pdfJob(caseId, "pdf/mine.pdf");
        UUID submission = fx.submission(caseId, healer, instructor);
        UUID rev = fx.revision(submission, 1, "review/mine/r1.pdf");
        fx.reviewFileUrl(rev, "review/mine/r1-masked.pdf");
        fx.comment(submission, rev, null, instructor, "Looks good");
        fx.assignment(healer, instructor);

        service.erase(healer, emailOf(healer));

        assertThat(fx.count("healer", "id", healer)).isZero();
        assertThat(fx.count("review_submission", "id", submission)).isZero();
        assertThat(fx.count("review_revision", "submission_id", submission)).isZero();
        assertThat(fx.count("review_comment", "submission_id", submission)).isZero();
        assertThat(fx.count("reviewer_assignment", "healer_id", healer)).isZero();
        assertThat(fx.count("healer", "id", instructor)).isEqualTo(1);
        commitByHand();
        verify(storage).delete("pdf/mine.pdf");
        verify(storage).delete("review/mine/r1.pdf");
        verify(storage).delete("review/mine/r1-masked.pdf");
    }

    @Test
    void eraseHealer_whoIsAnInstructor_keepsHealersSubmissions_nullsReviewerAndAuthor_removesAssignments() {
        UUID instructor = fx.healer("HEALER");
        UUID healerA = fx.healer("HEALER");
        UUID healerB = fx.healer("HEALER");
        UUID caseA = fx.healingCase(fx.patient(healerA), healerA);
        UUID submission = fx.submission(caseA, healerA, instructor);
        UUID rev = fx.revision(submission, 1, "review/a/r1.pdf");
        UUID instructorComment = fx.comment(submission, rev, null, instructor, "Please redo page 2");
        UUID healerReply = fx.comment(submission, rev, instructorComment, healerA, "Redone");
        UUID assignA = fx.assignment(healerA, instructor);
        UUID assignB = fx.assignment(healerB, instructor);
        // unrelated: instructor C reviews healer D
        UUID instructorC = fx.healer("HEALER");
        UUID healerD = fx.healer("HEALER");
        UUID caseD = fx.healingCase(fx.patient(healerD), healerD);
        UUID unrelatedAssignment = fx.assignment(healerD, instructorC);
        UUID unrelatedSubmission = fx.submission(caseD, healerD, instructorC);
        UUID unrelatedRev = fx.revision(unrelatedSubmission, 1, "review/d/r1.pdf");
        UUID unrelatedComment = fx.comment(unrelatedSubmission, unrelatedRev, null, instructorC, "Fine");
        // the instructor's e-mail was stamped on the rows they touched (live and history)
        String email = emailOf(instructor);
        fx.stamp("review_submission", submission, email);
        fx.stamp("review_comment", instructorComment, email);
        long rev1 = fx.revinfo();
        fx.submissionAud(submission, rev1, email, instructor);
        fx.commentAud(instructorComment, rev1, email, instructor);
        fx.assignmentAud(assignA, rev1, healerA, instructor);
        fx.assignmentAud(assignB, rev1, healerB, instructor);
        fx.assignmentAud(unrelatedAssignment, rev1, healerD, instructorC);
        String emailC = emailOf(instructorC);
        fx.stamp("review_submission", unrelatedSubmission, emailC);
        fx.stamp("review_comment", unrelatedComment, emailC);

        service.erase(instructor, email);

        assertThat(fx.count("healer", "id", instructor)).isZero();
        // the healers' data stays
        assertThat(fx.count("healer", "id", healerA)).isEqualTo(1);
        assertThat(fx.count("review_submission", "id", submission)).isEqualTo(1);
        assertThat(fx.count("review_revision", "submission_id", submission)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reviewer_id FROM review_submission WHERE id = ?",
                UUID.class, submission)).isNull();
        assertThat(jdbc.queryForObject("SELECT author_id FROM review_comment WHERE id = ?",
                UUID.class, instructorComment)).isNull();
        assertThat(jdbc.queryForObject("SELECT body FROM review_comment WHERE id = ?",
                String.class, instructorComment)).isEqualTo("Please redo page 2");
        assertThat(jdbc.queryForObject("SELECT author_id FROM review_comment WHERE id = ?",
                UUID.class, healerReply)).isEqualTo(healerA);
        // assignments of either side are gone
        assertThat(fx.count("reviewer_assignment", "reviewer_id", instructor)).isZero();
        assertThat(fx.count("reviewer_assignment", "healer_id", healerA)).isZero();
        assertThat(fx.count("reviewer_assignment", "healer_id", healerB)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reviewer_assignment_aud WHERE id IN (?, ?)",
                Integer.class, assignA, assignB)).isZero();
        // no kept row, live or history, still carries the erased instructor's e-mail
        for (String table : java.util.List.of("review_submission", "review_comment", "review_submission_aud",
                "review_comment_aud", "reviewer_assignment")) {
            if (!table.equals("reviewer_assignment")) {
                assertThat(fx.stampedWith(table, email)).as(table).isZero();
            }
        }
        // the unrelated pair C -> D is untouched
        assertThat(fx.count("reviewer_assignment", "id", unrelatedAssignment)).isEqualTo(1);
        assertThat(fx.count("reviewer_assignment_aud", "id", unrelatedAssignment)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reviewer_id FROM review_submission WHERE id = ?",
                UUID.class, unrelatedSubmission)).isEqualTo(instructorC);
        assertThat(jdbc.queryForObject("SELECT author_id FROM review_comment WHERE id = ?",
                UUID.class, unrelatedComment)).isEqualTo(instructorC);
        assertThat(fx.stampedWith("review_submission", emailC)).isEqualTo(1);
        assertThat(fx.stampedWith("review_comment", emailC)).isEqualTo(1);
        // and none of the healers' files are touched
        commitByHand();
        verify(storage, Mockito.never()).delete(anyString());
    }

    @Test
    void eraseHealer_whoHasALinkedInstructor_succeeds() {
        UUID healer = fx.healer("HEALER");
        UUID instructor = fx.healer("HEALER");
        fx.assignment(healer, instructor);

        service.erase(healer, emailOf(healer));

        assertThat(fx.count("healer", "id", healer)).isZero();
        assertThat(fx.count("reviewer_assignment", "healer_id", healer)).isZero();
        assertThat(fx.count("healer", "id", instructor)).isEqualTo(1);
    }
}
