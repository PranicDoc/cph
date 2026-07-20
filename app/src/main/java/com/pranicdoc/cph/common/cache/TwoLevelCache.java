package com.pranicdoc.cph.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.dao.DataAccessException;

import java.util.concurrent.Callable;

/**
 * Two-level {@link org.springframework.cache.Cache} implementation: a local (L1, typically
 * Caffeine) tier checked first, falling through to a remote (L2, typically Redis) tier on
 * L1 miss, repopulating L1 on an L2 hit. Writes go through both tiers (INFRA-02, D-15/D-16).
 *
 * <p><b>Why a custom wrapper, not Spring's built-in multi-manager delegation:</b> Spring's
 * stock multi-{@code CacheManager} delegation strategy resolves a {@code Cache} for a given
 * name from the first delegate manager that has one and stops looking -- the second delegate
 * (Redis) is never consulted for reads or writes. This class is the custom {@code Cache} SPI
 * implementation that 23-RESEARCH.md Pitfall 1 calls for instead.
 *
 * <p><b>Redis-down behavior (T-23-12, fail-open):</b> every remote-tier operation is wrapped
 * so a {@link DataAccessException} (covers Lettuce/Redis connection failures, timeouts, etc.)
 * degrades this cache to L1-only for that operation rather than propagating up through
 * {@code @Cacheable} and turning into a 500 -- a Redis blip means slightly more DB load
 * (full local misses fall through to the wrapped method body), not an outage.
 *
 * <p><b>Cross-healer isolation (T-23-11):</b> this class does not construct, inspect, or
 * rewrite cache keys in any way -- whatever key Spring's {@code @Cacheable} key SpEL produces
 * (already healerId-first, see {@code DashboardServiceImpl}/{@code HealerCacheServiceImpl})
 * is passed through unchanged to both tiers.
 */
public class TwoLevelCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(TwoLevelCache.class);

    private final String name;
    private final Cache local;
    private final Cache remote;

    public TwoLevelCache(String name, Cache local, Cache remote) {
        this.name = name;
        this.local = local;
        this.remote = remote;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper localHit = local.get(key);
        if (localHit != null) {
            return localHit;
        }
        ValueWrapper remoteHit = getFromRemoteSafely(key);
        if (remoteHit != null) {
            // Repopulate L1 on L2 hit so subsequent reads on this instance stay local.
            local.put(key, remoteHit.get());
        }
        return remoteHit;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException(
                    "Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper localHit = local.get(key);
        if (localHit != null) {
            return (T) localHit.get();
        }
        ValueWrapper remoteHit = getFromRemoteSafely(key);
        if (remoteHit != null) {
            T value = (T) remoteHit.get();
            local.put(key, value);
            return value;
        }
        // Full miss on both tiers: delegate to the local (Caffeine) cache's own
        // get(key, valueLoader), which is atomic per-key -- this preserves the
        // @Cacheable(sync = true) single-flight guarantee (only one thread invokes
        // the wrapped method body per key), and write-through to Redis happens
        // exactly once, inside the loader, only when a real computation occurred.
        return local.get(key, () -> {
            T loaded = valueLoader.call();
            putRemoteSafely(key, loaded);
            return loaded;
        });
    }

    @Override
    public void put(Object key, Object value) {
        local.put(key, value);
        putRemoteSafely(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        local.evict(key);
        evictRemoteSafely(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean localEvicted = local.evictIfPresent(key);
        boolean remoteEvicted = evictIfPresentRemoteSafely(key);
        return localEvicted || remoteEvicted;
    }

    @Override
    public void clear() {
        local.clear();
        clearRemoteSafely();
    }

    @Override
    public boolean invalidate() {
        boolean localInvalidated = local.invalidate();
        boolean remoteInvalidated = invalidateRemoteSafely();
        return localInvalidated || remoteInvalidated;
    }

    private ValueWrapper getFromRemoteSafely(Object key) {
        try {
            return remote.get(key);
        } catch (DataAccessException ex) {
            log.warn("Redis L2 cache [{}] unavailable on get(), degrading to L1/DB", name, ex);
            return null;
        }
    }

    private void putRemoteSafely(Object key, Object value) {
        try {
            remote.put(key, value);
        } catch (DataAccessException ex) {
            log.warn("Redis L2 cache [{}] unavailable on put(), value only cached in L1", name, ex);
        }
    }

    private void evictRemoteSafely(Object key) {
        try {
            remote.evict(key);
        } catch (DataAccessException ex) {
            log.warn("Redis L2 cache [{}] unavailable on evict()", name, ex);
        }
    }

    private boolean evictIfPresentRemoteSafely(Object key) {
        try {
            return remote.evictIfPresent(key);
        } catch (DataAccessException ex) {
            log.warn("Redis L2 cache [{}] unavailable on evictIfPresent()", name, ex);
            return false;
        }
    }

    private void clearRemoteSafely() {
        try {
            remote.clear();
        } catch (DataAccessException ex) {
            log.warn("Redis L2 cache [{}] unavailable on clear()", name, ex);
        }
    }

    private boolean invalidateRemoteSafely() {
        try {
            return remote.invalidate();
        } catch (DataAccessException ex) {
            log.warn("Redis L2 cache [{}] unavailable on invalidate()", name, ex);
            return false;
        }
    }
}
