package com.pranicdoc.cph.common.cache;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheRefreshServiceTest {

    private static final UUID HEALER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final List<String> NAMES = List.of("dashboard.summary", "healerEmail", "healerName");

    private final HealerRepository healers = mock(HealerRepository.class);
    private final ConcurrentMapCacheManager redis = new ConcurrentMapCacheManager();
    private final TwoLevelCacheManager caches = manager(redis);
    private final CacheRefreshService service = new CacheRefreshService(caches, healers);

    private static TwoLevelCacheManager manager(CacheManager remote) {
        TwoLevelCacheManager manager = new TwoLevelCacheManager(remote, 60, 100);
        NAMES.forEach(manager::registerCache);
        return manager;
    }

    /** Memory holds UUID object keys; the Redis stand-in, like real Redis, is addressed by their text. */
    private void seed() {
        for (String name : NAMES) {
            TwoLevelCache cache = (TwoLevelCache) caches.getCache(name);
            cache.put(HEALER, "mine");
            cache.put(OTHER, "theirs");
            Cache remote = redis.getCache(name);
            remote.clear();
            remote.put(HEALER.toString(), "mine");
            remote.put(OTHER.toString(), "theirs");
        }
        when(healers.findEmailById(HEALER)).thenReturn("fresh-mine");
        when(healers.findEmailById(OTHER)).thenReturn("fresh-theirs");
    }

    private Object inMemory(String name, UUID id) {
        TwoLevelCache cache = (TwoLevelCache) caches.getCache(name);
        return cache.localKeys().contains(id) ? cache.get(id).get() : null;
    }

    private Object inRedis(String name, UUID id) {
        Cache.ValueWrapper wrapper = redis.getCache(name).get(id.toString());
        return wrapper == null ? null : wrapper.get();
    }

    @Test
    void cacheNamesAreSorted() {
        assertThat(service.cacheNames()).containsExactlyElementsOf(NAMES);
    }

    @Test
    void noParamsClearsEveryCache() {
        seed();

        CacheRefreshResult result = service.refresh(null, null);

        assertThat(result).isEqualTo(new CacheRefreshResult(NAMES, null, true));
        for (String name : List.of("healerName", "dashboard.summary")) {
            assertThat(inMemory(name, HEALER)).isNull();
            assertThat(inMemory(name, OTHER)).isNull();
            assertThat(inRedis(name, HEALER)).isNull();
            assertThat(inRedis(name, OTHER)).isNull();
        }
    }

    @Test
    void cacheNameClearsOnlyThatCache() {
        seed();

        CacheRefreshResult result = service.refresh("healerName", null);

        assertThat(result.caches()).containsExactly("healerName");
        assertThat(inMemory("healerName", HEALER)).isNull();
        assertThat(inRedis("healerName", OTHER)).isNull();
        assertThat(inMemory("dashboard.summary", HEALER)).isEqualTo("mine");
        assertThat(inRedis("dashboard.summary", HEALER)).isEqualTo("mine");
    }

    @Test
    void cacheNameAndKeyClearsOneEntry() {
        seed();

        service.refresh("healerName", HEALER.toString());

        assertThat(inMemory("healerName", HEALER)).isNull();
        assertThat(inRedis("healerName", HEALER)).isNull();
        assertThat(inMemory("healerName", OTHER)).isEqualTo("theirs");
        assertThat(inRedis("healerName", OTHER)).isEqualTo("theirs");
        assertThat(inMemory("dashboard.summary", HEALER)).isEqualTo("mine");
    }

    @Test
    void keyAloneMatchesUuidKeysInEveryCache() {
        seed();

        CacheRefreshResult result = service.refresh(null, HEALER.toString());

        assertThat(result.key()).isEqualTo(HEALER.toString());
        for (String name : List.of("healerName", "dashboard.summary")) {
            assertThat(inMemory(name, HEALER)).isNull();
            assertThat(inRedis(name, HEALER)).isNull();
            assertThat(inMemory(name, OTHER)).isEqualTo("theirs");
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"null,false", "healerEmail,false", "healerEmail,true", "null,true"}, nullValues = "null")
    void emailIsReloadedNotLeftCold(String cacheName, boolean byKey) {
        seed();

        service.refresh(cacheName, byKey ? HEALER.toString() : null);

        assertThat(inMemory("healerEmail", HEALER)).isEqualTo("fresh-mine");
        assertThat(inMemory("healerEmail", OTHER)).isEqualTo(byKey ? "theirs" : "fresh-theirs");
    }

    @Test
    void emailOfMissingHealerIsEvicted() {
        seed();
        when(healers.findEmailById(HEALER)).thenReturn(null);

        service.refresh("healerEmail", null);

        assertThat(inMemory("healerEmail", HEALER)).isNull();
        assertThat(inMemory("healerEmail", OTHER)).isEqualTo("fresh-theirs");
    }

    @ParameterizedTest
    @CsvSource({"healerName,false", "healerName,true", "healerEmail,false"})
    void redisFailureIsReported(String cacheName, boolean byKey) {
        CacheManager down = mock(CacheManager.class);
        Cache broken = mock(Cache.class);
        RedisConnectionFailureException failure = new RedisConnectionFailureException("down");
        doThrow(failure).when(broken).clear();
        doThrow(failure).when(broken).evict(HEALER.toString());
        when(down.getCache(anyString())).thenReturn(broken);
        TwoLevelCacheManager manager = manager(down);
        manager.getCache(cacheName).put(HEALER, "mine");
        when(healers.findEmailById(HEALER)).thenReturn("fresh-mine");

        CacheRefreshResult result = new CacheRefreshService(manager, healers)
                .refresh(cacheName, byKey ? HEALER.toString() : null);

        assertThat(result.redisCleared()).isFalse();
        TwoLevelCache cache = (TwoLevelCache) manager.getCache(cacheName);
        if (cacheName.equals("healerEmail")) {
            assertThat(cache.get(HEALER).get()).isEqualTo("fresh-mine");
        } else {
            assertThat(cache.localKeys()).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"nope,null", "BLANK,null", "null,EMPTY", "healerName,BLANK"}, nullValues = "null")
    void unknownOrBlankParamsAreRejected(String cacheName, String key) {
        seed();

        assertThatThrownBy(() -> service.refresh(blank(cacheName), blank(key)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(inMemory("healerName", HEALER)).isEqualTo("mine");
        assertThat(inMemory("dashboard.summary", OTHER)).isEqualTo("theirs");
    }

    /** Each call to an unreachable Redis waits for the client timeout, so one failure is all a refresh can afford. */
    @Test
    void afterTheFirstRedisFailureNoFurtherRedisCallsAreMade() {
        List<String> calls = new ArrayList<>();
        CacheManager down = mock(CacheManager.class);
        Cache broken = mock(Cache.class, invocation -> {
            calls.add(invocation.getMethod().getName());
            throw new RedisConnectionFailureException("down");
        });
        when(down.getCache(anyString())).thenReturn(broken);
        TwoLevelCacheManager manager = manager(down);
        for (String name : NAMES) {
            ((TwoLevelCache) manager.getCache(name)).put(HEALER, "mine");
        }
        calls.clear();
        when(healers.findEmailById(HEALER)).thenReturn("fresh-mine");

        CacheRefreshResult result = new CacheRefreshService(manager, healers).refresh(null, null);

        assertThat(result.redisCleared()).isFalse();
        assertThat(calls).hasSize(1);
        assertThat(((TwoLevelCache) manager.getCache("healerName")).localKeys()).isEmpty();
        assertThat(((TwoLevelCache) manager.getCache("healerEmail")).get(HEALER).get()).isEqualTo("fresh-mine");
    }

    @Test
    void aFailedEmailWriteStopsFurtherWritesAndIsReported() {
        List<Object> writes = new ArrayList<>();
        CacheManager remoteManager = mock(CacheManager.class);
        Cache remote = mock(Cache.class);
        when(remoteManager.getCache(anyString())).thenReturn(remote);
        TwoLevelCacheManager manager = manager(remoteManager);
        TwoLevelCache emails = (TwoLevelCache) manager.getCache("healerEmail");
        emails.put(HEALER, "mine");
        emails.put(OTHER, "theirs");
        doAnswer(invocation -> {
            writes.add(invocation.getArgument(0));
            throw new RedisConnectionFailureException("down");
        }).when(remote).put(any(), any());
        when(healers.findEmailById(HEALER)).thenReturn("fresh-mine");
        when(healers.findEmailById(OTHER)).thenReturn("fresh-theirs");

        CacheRefreshResult result = new CacheRefreshService(manager, healers).refresh("healerEmail", null);

        assertThat(result.redisCleared()).isFalse();
        assertThat(writes).hasSize(1);
        assertThat(emails.get(HEALER).get()).isEqualTo("fresh-mine");
        assertThat(emails.get(OTHER).get()).isEqualTo("fresh-theirs");
    }

    /** Memory cleared first would let a request in between copy the stale Redis value back for 12h. */
    @ParameterizedTest
    @ValueSource(strings = {"refresh", "evict"})
    void redisIsClearedBeforeMemory(String path) {
        CacheManager remoteManager = mock(CacheManager.class);
        Cache remote = mock(Cache.class);
        when(remoteManager.getCache(anyString())).thenReturn(remote);
        TwoLevelCache names = (TwoLevelCache) manager(remoteManager).getCache("healerName");
        names.put(HEALER, "old");
        List<Boolean> stillInMemory = new ArrayList<>();
        doAnswer(invocation -> stillInMemory.add(names.localKeys().contains(HEALER))).when(remote).clear();
        doAnswer(invocation -> stillInMemory.add(names.localKeys().contains(HEALER))).when(remote).evict(HEALER);

        if (path.equals("refresh")) {
            names.refreshAll(true);
        } else {
            names.evict(HEALER);
        }

        assertThat(stillInMemory).containsExactly(true);
        assertThat(names.localKeys()).isEmpty();
    }

    private static String blank(String token) {
        return "BLANK".equals(token) ? " " : "EMPTY".equals(token) ? "" : token;
    }
}
