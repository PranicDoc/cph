package com.pranicdoc.cph.common.cache;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Manual cache refresh, for when data was changed behind the application's back (for example a
 * direct database edit). Reaches Redis and the memory of this instance only.
 */
@Service
@RequiredArgsConstructor
public class CacheRefreshService {

    private static final String HEALER_EMAIL = "healerEmail";

    private final CacheManager cacheManager;
    private final HealerRepository healerRepository;

    public List<String> cacheNames() {
        return cacheManager.getCacheNames().stream().sorted().toList();
    }

    /** A null {@code cacheName} means every cache; a null {@code key} means every entry. */
    @Auditable(action = "REFRESH_CACHE")
    public CacheRefreshResult refresh(String cacheName, String key) {
        // Blank is refused, never read as "everything".
        if (cacheName != null && !cacheNames().contains(cacheName)) {
            throw new IllegalArgumentException("Unknown cache: '" + cacheName + "'");
        }
        if (key != null && key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        List<String> targets = cacheName == null ? cacheNames() : List.of(cacheName);
        boolean redisCleared = true;
        for (String name : targets) {
            // After one failure Redis is left alone: every further call would wait out its timeout.
            redisCleared = refreshCache(name, key, redisCleared);
        }
        return new CacheRefreshResult(targets, key, redisCleared);
    }

    private boolean refreshCache(String name, String key, boolean useRedis) {
        TwoLevelCache cache = (TwoLevelCache) cacheManager.getCache(name);
        if (HEALER_EMAIL.equals(name)) {
            // Reloaded rather than cleared: a request already running relies on this entry, and a
            // cold load during a Hibernate flush recurses (see HealerCacheServiceImpl.getEmail).
            return cache.reload(key, id -> healerRepository.findEmailById((UUID) id), useRedis);
        }
        return key == null ? cache.refreshAll(useRedis) : cache.refreshKey(key, useRedis);
    }
}
