package com.pranicdoc.cph.payments;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.pranicdoc.cph.common.exception.InsufficientCreditsException;
import com.pranicdoc.cph.payments.service.CreditGateService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CreditGateIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideCreditServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("creditservice.base-url", wireMock::baseUrl);
    }

    @Autowired
    private CreditGateService creditGateService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetBreaker() {
        circuitBreakerRegistry.circuitBreaker("creditService").reset();
    }

    @Test
    void approved_returnsNormally() {
        UUID healerId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/credits/deduct"))
                .willReturn(okJson("{\"result\":\"APPROVED\",\"balance_after\":2}")));

        assertDoesNotThrow(() -> creditGateService.checkAndDeduct(healerId, clinicId, caseId));
    }

    @Test
    void insufficient_throwsAndDoesNotFailOpen() {
        UUID healerId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/credits/deduct"))
                .willReturn(okJson("{\"result\":\"INSUFFICIENT\",\"balance_after\":0}")));

        assertThrows(InsufficientCreditsException.class,
                () -> creditGateService.checkAndDeduct(healerId, clinicId, caseId));
    }

    @Test
    void timeout_failsOpenAndLogsReconciliation() {
        UUID healerId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/credits/deduct"))
                .willReturn(aResponse().withFixedDelay(6000)));

        Logger logger = (Logger) LoggerFactory.getLogger(CreditGateService.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            assertDoesNotThrow(() -> creditGateService.checkAndDeduct(healerId, clinicId, caseId));

            boolean hasReconciliationLog = listAppender.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("needsReconciliation=true"));
            assertTrue(hasReconciliationLog,
                    "Expected fail-open log containing 'needsReconciliation=true' but none found");
        } finally {
            logger.detachAppender(listAppender);
        }
    }
}
