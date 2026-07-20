package com.pranicdoc.cph.common.config;

import com.pranicdoc.cph.common.cache.TwoLevelCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.util.concurrent.TimeUnit;

/**
 * Every dashboard.* cache + healerEmail is served through a two-level
 * Caffeine(L1)-&gt;Redis(L2) cache (INFRA-02, D-15/D-16), not Caffeine alone and not a
 * composite-manager delegation (which would silently no-op the Redis tier -- see
 * {@link com.pranicdoc.cph.common.cache.TwoLevelCache}'s javadoc / 23-RESEARCH.md Pitfall 1).
 * The L2 (Redis) tier is {@link RedisConfig}'s {@link RedisCacheManager} bean.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // Preserve exact per-name coverage from the pre-two-level-cache Caffeine-only setup.
    private static final String[] DASHBOARD_CACHE_NAMES = {
            "dashboard.summary",
            "dashboard.session-trends",
            "dashboard.patient-growth",
            "dashboard.top-symptoms",
            "dashboard.top-protocols",
            "dashboard.upcoming-sessions",
            "dashboard.pending-reports",
            "dashboard.followups",
            "dashboard.credit-usage"
    };

    @Value("${caffeine.cache.ttl-seconds:45}")
    private long dashboardCaffeineTtlSeconds;

    @Value("${dashboard.cache.max-size:1000}")
    private long dashboardCaffeineMaxSize;

    @Bean
    @Primary
    public CacheManager cacheManager(RedisCacheManager redisCacheManager) {
        TwoLevelCacheManager manager = new TwoLevelCacheManager(
                redisCacheManager, dashboardCaffeineTtlSeconds, dashboardCaffeineMaxSize);

        // healerEmail -- DO NOT change L1 spec (12h TTL, 10_000 max entries); now also
        // backed by Redis L2 so a healerId->email lookup is shared across cph instances.
        manager.registerCache("healerEmail", 12, TimeUnit.HOURS, 10_000);

        // Per-name dashboard caches: short Caffeine L1 TTL (30-60s, D-16) + longer Redis
        // L2 TTL (RedisConfig's redis.cache.ttl-minutes, default 5 min).
        for (String name : DASHBOARD_CACHE_NAMES) {
            manager.registerCache(name);
        }

        return manager;
    }
}