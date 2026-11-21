package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.review.erasure.ReviewErasureFixtures;
import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.Executor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Runs the real purge SQL on H2 with the production foreign keys added, so a forgotten table fails here. */
@DataJpaTest
@ActiveProfiles("test")
@Import({RecordPurger.class, com.pranicdoc.cph.common.util.TransactionService.class, RecordPurgerReviewDataIntegrationTest.SameThreadExecutor.class})
class RecordPurgerReviewDataIntegrationTest {

    /** The production purger hands file deletion to the notification executor; run it inline here. */
    @TestConfiguration
    static class SameThreadExecutor {
        @Bean(name = "notificationExecutor")
        Executor notificationExecutor() {
            return Runnable::run;
        }
    }

    @Autowired private PlatformTransactionManager txManager;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordPurger purger;
    @MockBean private SecurityContextUtil securityContextUtil;
    @MockBean private StorageService storage;

    private ReviewErasureFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new ReviewErasureFixtures(jdbc);
        fx.addProductionForeignKeys();
    }

    /** Seeds a healer, patient, case, job, submission with two revisions and a comment with a reply. */
    private record Seeded(UUID healer, UUID reviewer, UUID patient, UUID caseId, UUID submission,
                          UUID rev1, UUID rev2, UUID comment, UUID reply) {
    }

    private Seeded seed(String jobFile, String rev1File, String rev2File) {
        UUID healer = fx.healer("HEALER");
        UUID reviewer = fx.healer("HEALER");
        UUID patient = fx.patient(healer);
        UUID caseId = fx.healingCase(patient, healer);
        fx.pdfJob(caseId, jobFile);
        UUID submission = fx.submission(caseId, healer, reviewer);
        UUID rev1 = fx.revision(submission, 1, rev1File);
        UUID rev2 = fx.revision(submission, 2, rev2File);
        fx.reviewFileUrl(rev2, rev2File.replace(".pdf", "-masked.pdf"));
        UUID comment = fx.comment(submission, rev1, null, reviewer, "Please recheck the third chakra");
        UUID reply = fx.comment(submission, rev1, comment, healer, "Done");
        return new Seeded(healer, reviewer, patient, caseId, submission, rev1, rev2, comment, reply);
    }

    @Test
    void purgePatient_removesReviewRowsOfItsCases() {
        Seeded mine = seed("pdf/a.pdf", "review/a/r1.pdf", "review/a/r2.pdf");
        Seeded other = seed("pdf/b.pdf", "review/b/r1.pdf", "review/b/r2.pdf");

        purger.purgePatient(mine.patient());

        assertThat(fx.count("review_submission", "id", mine.submission())).isZero();
        assertThat(fx.count("review_revision", "submission_id", mine.submission())).isZero();
        assertThat(fx.count("review_comment", "submission_id", mine.submission())).isZero();
        assertThat(fx.count("healing_case", "id", mine.caseId())).isZero();
        assertThat(fx.count("patient", "id", mine.patient())).isZero();
        // another patient's review data, and both healers, are untouched
        assertThat(fx.count("review_submission", "id", other.submission())).isEqualTo(1);
        assertThat(fx.count("review_revision", "submission_id", other.submission())).isEqualTo(2);
        assertThat(fx.count("review_comment", "submission_id", other.submission())).isEqualTo(2);
        assertThat(fx.count("healer", "id", mine.healer())).isEqualTo(1);
        assertThat(fx.count("healer", "id", mine.reviewer())).isEqualTo(1);
    }

    @Test
    void purgePatient_removesSoftDeletedReviewRowsToo() {
        Seeded mine = seed("pdf/a.pdf", "review/a/r1.pdf", "review/a/r2.pdf");
        jdbc.update("UPDATE review_submission SET is_deleted = TRUE WHERE id = ?", mine.submission());
        jdbc.update("UPDATE review_comment SET is_deleted = TRUE WHERE submission_id = ?", mine.submission());

        purger.purgePatient(mine.patient());

        assertThat(fx.count("review_submission", "id", mine.submission())).isZero();
        assertThat(fx.count("review_comment", "submission_id", mine.submission())).isZero();
    }

    @Test
    void purgePatient_deletesTheStoredFilesOfItsCasesAfterCommitOnly() {
        Seeded mine = seed("pdf/a.pdf", "review/a/r1.pdf", "review/a/r2.pdf");
        seed("pdf/b.pdf", "review/b/r1.pdf", "review/b/r2.pdf");

        purger.purgePatient(mine.patient());
        verify(storage, never()).delete(anyString());

        // The test transaction never commits, so complete it by hand the way Spring does.
        TransactionSynchronizationManager.getSynchronizations().stream()
                .forEach(TransactionSynchronization::afterCommit);

        verify(storage).delete("pdf/a.pdf");
        verify(storage).delete("review/a/r1.pdf");
        verify(storage).delete("review/a/r2.pdf");
        verify(storage).delete("review/a/r2-masked.pdf"); // the reserved masked copy goes too
        verify(storage, never()).delete("pdf/b.pdf");
        verify(storage, never()).delete("review/b/r1.pdf");
        verify(storage, never()).delete("review/b/r2.pdf");
        verify(storage, never()).delete("review/b/r2-masked.pdf");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void purgePatient_inARolledBackTransaction_keepsRowsAndDeletesNoFiles() {
        Seeded mine = seed("pdf/rb.pdf", "review/rb/r1.pdf", "review/rb/r2.pdf");
        TransactionTemplate tx = new TransactionTemplate(txManager);

        tx.executeWithoutResult(status -> {
            purger.purgePatient(mine.patient());
            status.setRollbackOnly();
        });

        verify(storage, never()).delete(anyString());
        assertThat(fx.count("patient", "id", mine.patient())).isEqualTo(1);
        assertThat(fx.count("review_submission", "id", mine.submission())).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void purgePatient_inACommittedTransaction_deletesTheFiles() {
        Seeded mine = seed("pdf/cm.pdf", "review/cm/r1.pdf", "review/cm/r2.pdf");

        new TransactionTemplate(txManager).executeWithoutResult(status -> purger.purgePatient(mine.patient()));

        verify(storage).delete("pdf/cm.pdf");
        verify(storage).delete("review/cm/r1.pdf");
        assertThat(fx.count("patient", "id", mine.patient())).isZero();
    }
}
