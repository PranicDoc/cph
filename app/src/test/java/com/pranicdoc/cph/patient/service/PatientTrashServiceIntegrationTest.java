package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.review.erasure.ReviewErasureFixtures;
import com.pranicdoc.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The trash, restore and nightly purge on real SQL (H2): the native queries must see soft-deleted rows. */
@DataJpaTest
@ActiveProfiles("test")
@Import({PatientTrashService.class, RecordPurger.class, TransactionService.class,
        PatientTrashServiceIntegrationTest.SameThreadExecutor.class})
class PatientTrashServiceIntegrationTest {

    @TestConfiguration
    static class SameThreadExecutor {
        @Bean(name = "notificationExecutor")
        Executor notificationExecutor() {
            return Runnable::run;
        }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PatientTrashService trash;
    @MockBean private SecurityContextUtil securityContextUtil;
    @MockBean private StorageService storage;

    private ReviewErasureFixtures fx;
    private UUID healer;
    private UUID patient;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        fx = new ReviewErasureFixtures(jdbc);
        fx.addProductionForeignKeys();
        // The trash columns come from Flyway; Hibernate maps none of them, so add them to the H2 schema.
        jdbc.execute("ALTER TABLE patient ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE");
        jdbc.execute("ALTER TABLE healing_case ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE");
        jdbc.execute("ALTER TABLE healing_case ADD COLUMN IF NOT EXISTS deleted_with_patient BOOLEAN DEFAULT FALSE NOT NULL");
        healer = fx.healer("HEALER");
        patient = fx.patient(healer);
        caseId = fx.healingCase(patient, healer);
    }

    @Test
    void moveToTrash_hidesThePatientAndTheirCases_andListTrashShowsThem() {
        trash.moveToTrash(patient);

        assertThat(fx.count("patient", "id", patient)).isEqualTo(1); // raw count sees soft-deleted rows
        var rows = trash.listTrash();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).id()).isEqualTo(patient);
        assertThat(rows.get(0).healerId()).isEqualTo(healer);
        assertThat(rows.get(0).deletedAt()).isNotNull();
        assertThat(rows.get(0).purgeAt()).isAfter(rows.get(0).deletedAt());
        assertThat(jdbc.queryForObject("SELECT deleted_with_patient FROM healing_case WHERE id = ?", Boolean.class, caseId))
                .isTrue();
    }

    @Test
    void movingAnAlreadyTrashedPatientAgainFails() {
        trash.moveToTrash(patient);

        assertThatThrownBy(() -> trash.moveToTrash(patient)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restore_bringsBackThePatientAndOnlyTheCasesThatWentWithThem() {
        trash.moveToTrash(patient);

        trash.restore(patient);

        assertThat(trash.listTrash()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM healing_case WHERE id = ?", Boolean.class, caseId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT deleted_with_patient FROM healing_case WHERE id = ?", Boolean.class, caseId))
                .isFalse();
        assertThatThrownBy(() -> trash.restore(patient)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void purgeExpired_erasesOnlyPatientsPastTheRetentionWindow() {
        trash.moveToTrash(patient);

        assertThat(trash.purgeExpired(Instant.now())).isZero();
        assertThat(fx.count("patient", "id", patient)).isEqualTo(1);

        int purged = trash.purgeExpired(Instant.now().plus(Duration.ofDays(trash.getRetentionDays() + 1)));

        assertThat(purged).isEqualTo(1);
        assertThat(fx.count("patient", "id", patient)).isZero();
        assertThat(fx.count("healing_case", "id", caseId)).isZero();
    }
}
