package com.pranicdoc.cph.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * {@link CacheManager} producing {@link TwoLevelCache} instances per cache name (INFRA-02,
 * D-15/D-16). Deliberately not built from Spring's stock multi-manager delegation -- see
 * {@link TwoLevelCache}'s javadoc for why that approach silently no-ops the Redis tier.
 *
 * <p>Each cache name must be explicitly registered (mirroring {@code CacheConfig}'s pre-existing
 * per-name {@code registerCustomCache} loop) because different cache names need different L1
 * (Caffeine) specs -- dashboard.* caches use a short, uniform TTL (30-60s per D-16), while
 * {@code healerEmail} keeps its own pre-existing 12h/10,000-entry spec unchanged. The L2 (Redis)
 * tier for every registered name comes from the injected {@code RedisCacheManager} (23-02's
 * {@code RedisConfig}), whose TTL is longer than L1's by design (D-16 bounded staleness).
 */
public class TwoLevelCacheManager implements CacheManager {

    private final CacheManager remoteCacheManager;
    private final long defaultLocalTtlSeconds;
    private final long defaultLocalMaximumSize;
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

    public TwoLevelCacheManager(CacheManager remoteCacheManager,
                                 long defaultLocalTtlSeconds,
                                 long defaultLocalMaximumSize) {
        this.remoteCacheManager = remoteCacheManager;
        this.defaultLocalTtlSeconds = defaultLocalTtlSeconds;
        this.defaultLocalMaximumSize = defaultLocalMaximumSize;
    }

    /** Registers {@code name} using the default L1 spec (short TTL, D-16 dashboard.* caches). */
    public void registerCache(String name) {
        registerCache(name, defaultLocalTtlSeconds, TimeUnit.SECONDS, defaultLocalMaximumSize);
    }

    /** Registers {@code name} with an explicit, custom L1 spec (e.g. healerEmail's 12h/10k). */
    public void registerCache(String name, long localTtl, TimeUnit localTtlUnit, long localMaximumSize) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCaffeine =
                Caffeine.newBuilder()
                        .maximumSize(localMaximumSize)
                        .expireAfterWrite(localTtl, localTtlUnit)
                        .build();
        Cache local = new CaffeineCache(name, nativeCaffeine);
        Cache remote = remoteCacheManager.getCache(name);
        if (remote == null) {
            throw new IllegalStateException(
                    "No Redis (L2) cache available for name [" + name + "] -- check RedisCacheManager wiring");
        }
        caches.put(name, new TwoLevelCache(name, local, remote));
    }

    @Override
    public Cache getCache(String name) {
        return caches.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(caches.keySet());
    }
}
