package com.pranicdoc.cph.notification;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.notification.entity.FeedbackLink;
import com.pranicdoc.cph.notification.repository.FeedbackLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the JPQL of {@code existsActiveFinalLink} against a real JPA slice (mocks cannot). */
@DataJpaTest
@ActiveProfiles("test")
class FeedbackLinkRepositoryIntegrationTest {

    @Autowired private FeedbackLinkRepository repo;

    // Needed by the auditing config picked up by the @DataJpaTest slice (same as the masterdata ITs).
    @MockBean private HealerCacheService healerCacheService;
    @MockBean private SecurityContextUtil securityContextUtil;

    private FeedbackLink link(UUID caseId, boolean isFinal, LinkStatus status, Instant expiresAt) {
        return repo.saveAndFlush(FeedbackLink.builder()
                .token(UUID.randomUUID().toString())
                .caseId(caseId)
                .healerId(UUID.randomUUID())
                .isFinal(isFinal)
                .status(status)
                .expiresAt(expiresAt)
                .build());
    }

    @Test
    void existsActiveFinalLink_onlyForUnexpiredActiveFinalLinkOfThatCase() {
        UUID caseId = UUID.randomUUID();
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);

        assertFalse(repo.existsActiveFinalLink(caseId, LinkStatus.ACTIVE, Instant.now()), "no links yet");

        link(caseId, false, LinkStatus.ACTIVE, future);          // interim link does not count
        link(caseId, true, LinkStatus.USED, future);             // used final link does not count
        link(caseId, true, LinkStatus.ACTIVE, past);             // expired final link does not count
        link(UUID.randomUUID(), true, LinkStatus.ACTIVE, future); // another case's link does not count
        assertFalse(repo.existsActiveFinalLink(caseId, LinkStatus.ACTIVE, Instant.now()));

        link(caseId, true, LinkStatus.ACTIVE, future);
        assertTrue(repo.existsActiveFinalLink(caseId, LinkStatus.ACTIVE, Instant.now()));
    }
}
