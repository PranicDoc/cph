package com.pranicdoc.cph.common.tenant;

import com.pranicdoc.cph.patient.dto.PatientCreateRequest;
import com.pranicdoc.cph.patient.model.Patient;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * First-ever direct coverage of the Hibernate {@code @Filter} tenant-isolation
 * mechanism defined on {@link com.pranicdoc.cph.common.model.TenantEntity}
 * ({@code @FilterDef(name = "tenantFilter", ...)}, condition
 * {@code healer_id = :healerId}). Authored pre-flip (26-01) as the regression
 * net for 26-03's TenantEntity repoint from clinicId/clinic_id to
 * healerId/healer_id; repointed in 26-03 to the {@code healerId} parameter
 * name and {@code setHealerId} seed setter now that the flip has landed. This
 * proves the filter correctly scopes rows by tenant — a leak here would
 * expose real patient PII.
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantFilterIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void tenantFilter_scopesRowsToEnabledTenant_bothDirections() {
        Session session = entityManager.unwrap(Session.class);

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // Seed two Patient rows under two distinct tenant values with the
        // filter disabled (a fresh Hibernate Session has no filter enabled by
        // default). The tenant column is set explicitly via the setter on
        // each row so the test — not TenantContext/populateIds — controls
        // both tenants deterministically.
        Patient patientA = newPatient("Alice", "TenantA-Row");
        patientA.setHealerId(tenantA);
        entityManager.persist(patientA);

        Patient patientB = newPatient("Bob", "TenantB-Row");
        patientB.setHealerId(tenantB);
        entityManager.persist(patientB);

        entityManager.flush();
        entityManager.clear();

        // --- Direction 1: enable the filter for tenantA ---
        session.enableFilter("tenantFilter").setParameter("healerId", tenantA);

        List<Patient> resultsA = entityManager
                .createQuery("from Patient", Patient.class)
                .getResultList();

        assertEquals(1, resultsA.size(), "Expected exactly one row visible under tenantA's filter");
        assertEquals(tenantA, resultsA.get(0).getHealerId());
        assertNotEquals(tenantB, resultsA.get(0).getHealerId());

        session.disableFilter("tenantFilter");
        entityManager.clear();

        // --- Direction 2 (symmetric): re-parameterize the filter to tenantB ---
        session.enableFilter("tenantFilter").setParameter("healerId", tenantB);

        List<Patient> resultsB = entityManager
                .createQuery("from Patient", Patient.class)
                .getResultList();

        assertEquals(1, resultsB.size(), "Expected exactly one row visible under tenantB's filter");
        assertEquals(tenantB, resultsB.get(0).getHealerId());
        assertNotEquals(tenantA, resultsB.get(0).getHealerId());
    }

    /**
     * Builds a persistable Patient with all NOT-NULL fields populated.
     * {@code Patient}'s constructor is package-protected, so rows are built
     * through the public {@link Patient#create} factory (which requires a
     * non-null {@link TenantContext} value to satisfy
     * {@code TenantEntity.populateIds}'s guard) — the tenant value it derives
     * is then immediately overwritten by an explicit {@code setHealerId} call
     * at each call site above, so the final tenant assignment under test is
     * never implicitly trusted from TenantContext.
     */
    private static Patient newPatient(String firstName, String lastName) {
        PatientCreateRequest req = new PatientCreateRequest(
                firstName,
                lastName,
                null,
                null,
                null,
                null,
                "+19995550000",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        UUID bootstrapTenant = UUID.randomUUID();
        TenantContext.set(bootstrapTenant);
        try {
            return Patient.create(req, UUID.randomUUID());
        } finally {
            TenantContext.clear();
        }
    }
}
