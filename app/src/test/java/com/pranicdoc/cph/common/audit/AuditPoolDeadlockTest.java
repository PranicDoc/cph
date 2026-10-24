package com.pranicdoc.cph.common.audit;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.service.CreditGateService;
import com.pranicdoc.cph.payments.service.CreditService;
import com.pranicdoc.cph.rbac.baseline.BaselineRoleAssigner;
import com.pranicdoc.cph.support.TestRedisConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prod runs with a 6-connection pool. A request that has touched the database keeps its connection
 * (its own transaction, or the open-in-view EntityManager) while the audit row is written in a
 * REQUIRES_NEW transaction that needs a second one. With as many concurrent requests as pool
 * connections, every request holds one and waits for another: all stall until the pool times out,
 * and the audit write then fails (swallowed), so audit rows go missing.
 *
 * Same shape here with a 2-connection pool: concurrent audited requests must all succeed quickly
 * and each must leave its audit row (written by AuditLogWriter shortly after).
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=2000"
})
@Import(TestRedisConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditPoolDeadlockTest {

    private static final int THREADS = 6;
    private static final int REQUESTS_PER_THREAD = 5;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private HealerRepository healerRepository;
    @Autowired private BaselineRoleAssigner baselineRoleAssigner;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private AuditLogWriter auditLogWriter;

    @MockBean private CreditService creditService;
    @MockBean private CreditGateService creditGateService;
    @MockBean private CreditServiceClient creditServiceClient;

    @Test
    void concurrentAuditedRequests_doNotStarveThePool_andAreAllAudited() throws Exception {
        UUID healerId = UUID.randomUUID();
        Healer healer = Healer.builder()
                .fullName("Pool Healer")
                .email("pool-" + healerId + "@test.local")
                .role(Role.HEALER)
                .build();
        healer.setId(healerId);
        healerRepository.save(healer);
        baselineRoleAssigner.assignTo(healerId);
        Cookie cookie = new Cookie("accessToken", jwtService.generateAccessToken(healerId, "HEALER"));

        long auditRowsBefore = countProfileAudits(healerId);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CyclicBarrier start = new CyclicBarrier(THREADS);
        long began = System.nanoTime();
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
                        mockMvc.perform(get("/api/v1/auth/profile").cookie(cookie)).andExpect(status().isOk());
                    }
                    return null;
                }));
            }
            for (Future<?> call : calls) {
                call.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
        assertThat(auditLogWriter.awaitIdle(Duration.ofSeconds(10))).as("audit queue drained").isTrue();

        assertThat(countProfileAudits(healerId) - auditRowsBefore)
                .as("every /profile call leaves a GET_PROFILE audit row")
                .isEqualTo(THREADS * REQUESTS_PER_THREAD);
        assertThat(tookMs)
                .as("no request waits out the 2s pool timeout")
                .isLessThan(2000L);
        assertThat(auditLogRepository.findAll().stream()
                .filter(a -> "GET_PROFILE".equals(a.getAction()) && healerId.equals(a.getHealerId()))
                .map(AuditLog::getCreatedBy))
                .as("created_by is still the signed-in healer, resolved on the request thread")
                .containsOnly(healer.getEmail());
    }

    private long countProfileAudits(UUID healerId) {
        return auditLogRepository.findAll().stream()
                .filter(a -> "GET_PROFILE".equals(a.getAction()) && healerId.equals(a.getHealerId()))
                .count();
    }
}
