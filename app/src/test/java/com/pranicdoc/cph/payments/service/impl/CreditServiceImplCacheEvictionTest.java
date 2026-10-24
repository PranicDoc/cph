package com.pranicdoc.cph.payments.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.config.CacheConfig;
import com.pranicdoc.cph.common.config.RedisConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.service.CreditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the "Null key returned for cache operation" bug: purchase()'s
 * @CacheEvict keys used to be "#healerId" / "#filter.*" -- SpEL expressions referencing
 * parameters that don't exist on purchase(String, String) -- so Spring's key evaluation
 * returned null and threw at runtime. Fixed by resolving healerId from the security context
 * via a SpEL bean reference (@securityContextUtil.getCurrentHealerId()) instead.
 *
 * <p>Deliberately NOT a @SpringBootTest: booting the full CphApplication context currently
 * fails locally on a pre-existing, already-documented gap (Flyway/H2 dialect incompatibility
 * plus a missing spring.mail.username test property -- see application-test.yaml's own
 * comments and CreditGateIntegrationTest, an unmodified existing test, failing identically).
 * That's unrelated to this cache-key fix, so this test loads only the slice that actually
 * matters -- CacheConfig + RedisConfig + CreditServiceImpl -- so caching AOP is real without
 * dragging in Flyway/JPA/mail/etc. Redis itself doesn't need to be running: TwoLevelCache
 * fails open to its local (Caffeine) tier on any Redis DataAccessException.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        CreditServiceImplCacheEvictionTest.MinimalCachingConfig.class,
        CacheConfig.class,
        RedisConfig.class
})
class CreditServiceImplCacheEvictionTest {

    @Configuration
    static class MinimalCachingConfig {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SecurityContextUtil securityContextUtil() {
            return new SecurityContextUtil();
        }

        @Bean
        CreditServiceClient creditServiceClient() {
            return mock(CreditServiceClient.class);
        }

        @Bean
        HealerRepository healerRepository() {
            return mock(HealerRepository.class);
        }

        @Bean
        CreditServiceImpl creditServiceImpl(SecurityContextUtil securityContextUtil,
                                             CreditServiceClient creditServiceClient,
                                             HealerRepository healerRepository) {
            return new CreditServiceImpl(securityContextUtil, creditServiceClient, healerRepository,
                    org.mockito.Mockito.mock(com.pranicdoc.cph.payments.service.CreditGateService.class));
        }
    }

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditServiceClient creditServiceClient;

    @Autowired
    private CacheManager cacheManager;

    private UUID healerId;

    @BeforeEach
    void authenticateAsHealer() {
        healerId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("healer@example.com", null, List.of());
        auth.setDetails(new SecurityContextUtil.TenantDetails(healerId, null, null));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void purchase_evictsCurrentHealersDashboardSummaryCacheWithoutThrowing() {
        Cache summaryCache = cacheManager.getCache("dashboard.summary");
        summaryCache.put(healerId, "stale-summary");
        when(creditServiceClient.purchase("{\"package_id\":\"p1\"}", "idem-1"))
                .thenReturn("{\"purchase_reference\":\"ref-1\",\"credits_added\":50}");

        assertDoesNotThrow(() -> creditService.purchase("{\"package_id\":\"p1\"}", "idem-1"));

        assertNull(summaryCache.get(healerId),
                "dashboard.summary entry for the purchasing healer should be evicted, not left stale");
    }
}
