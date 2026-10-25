package com.pranicdoc.cph.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis-backed L2 cache tier configuration (INFRA-02 foundation).
 *
 * <p>Relies entirely on Spring Boot's autoconfigured {@link RedisConnectionFactory}
 * (a {@code LettuceConnectionFactory}, built from the {@code SPRING_DATA_REDIS_HOST}/
 * {@code SPRING_DATA_REDIS_PORT} env wired in docker-compose.yml — {@code
 * spring-boot-starter-data-redis} is already on the classpath, see
 * {@code cph/app/build.gradle}). This class only declares the cache-manager layer on
 * top of that autoconfigured connection factory; it does NOT build its own
 * {@code RedisConnectionFactory}/Lettuce client.
 *
 * <p>cph is the only service adopting Redis this phase (D-17). The {@link RedisCacheManager}
 * exposed here is the "remote"/L2 tier that a future two-level cache (23-05) wraps around
 * the existing Caffeine L1 manager in {@link CacheConfig}. It is deliberately NOT marked
 * {@code @Primary} and is not consumed by any {@code @Cacheable} annotation yet — wiring it
 * as the live {@code CacheManager} (or building the {@code TwoLevelCache} wrapper) is 23-05's
 * responsibility, not this plan's.
 *
 * <p><b>Cache key composition (T-23-04 mitigation):</b> {@link RedisCacheManager}'s default
 * key strategy is {@code "<cacheName>::<key>"}, where {@code <key>} is whatever
 * {@code SimpleKeyGenerator} (or an explicit SpEL {@code #key}) produced for the
 * {@code @Cacheable} method — e.g. the existing dashboard caches already compose keys
 * starting with {@code #healerId} (see {@code DashboardServiceImpl}). This class does NOT
 * override the key prefix or key serialization in any way that would strip or reorder that
 * healerId-first composition — doing so would risk one healer's cached data leaking to
 * another in this shared Redis store.
 *
 * <p><b>Redis-down behavior:</b> deliberately NOT built here. Spring Data Redis's default
 * behavior (propagate the connection exception) is left as-is; whether a downstream consumer
 * (two-level cache, rate limiter) should fail open or fail closed on Redis unavailability is
 * each consumer's own decision to make (see 23-RESEARCH.md Pitfall 2) — building a blanket
 * no-op/fail-open wrapper at this shared connection-config layer would silently impose one
 * choice on every future consumer.
 */
@Configuration
public class RedisConfig {

    @Value("${redis.cache.ttl-minutes:5}")
    private long redisCacheTtlMinutes;

    /**
     * Serializer-configured {@link RedisCacheManager} exposing the L2 cache tier.
     * Reuses the autoconfigured {@link RedisConnectionFactory} (LettuceConnectionFactory)
     * and a copy of the app's own {@link ObjectMapper} bean (see {@link ObjectMapperConfig},
     * already registers {@code JavaTimeModule} so dashboard DTOs with date/time fields
     * serialize correctly) for JSON value serialization.
     *
     * <p>The copy has default typing activated ({@code @class} embedded in the stored JSON).
     * {@link GenericJackson2JsonRedisSerializer} needs this to know the concrete Java type to
     * reconstruct on read — without it, a cache value with no type hint deserializes to a
     * plain {@code LinkedHashMap}/{@code ArrayList} instead of the original DTO (e.g.
     * {@code ApiResponse}), which surfaces as a {@code ClassCastException} at the
     * {@code @Cacheable} call site on any read that misses Caffeine's L1 and falls through to
     * this Redis L2. Default typing is activated on a copy, not the shared {@code objectMapper}
     * bean itself, so {@code @class} metadata never leaks into HTTP JSON responses.
     *
     * <p><b>Must be {@code DefaultTyping.EVERYTHING}, not {@code NON_FINAL}:</b> every
     * {@code @Cacheable} method here returns an {@code ApiResponse<T>} record wrapper (or a
     * bare {@code List<record>}). Jackson's root-value serialization
     * ({@code ObjectMapper#writeValue}) treats the value being written as its own declared
     * type — so under {@code NON_FINAL}, the POJO/record root of a cache entry (e.g. the outer
     * {@code ApiResponse}) never gets a {@code @class} tag, only nested Object-declared fields
     * do. That's invisible on write, but {@code GenericJackson2JsonRedisSerializer}'s generic
     * {@code deserialize(byte[])} (what {@code RedisCache.get()} calls) always reads back with
     * target type {@code Object.class}, which requires the root's own {@code @class} — so any
     * {@code ApiResponse}-wrapped dashboard.* cache throws
     * {@code InvalidTypeIdException: missing type id property '@class'} on every L1-miss/L2-hit
     * (recurring in prod, not a rare edge case — reproduced and confirmed against this project's
     * exact Jackson 2.17.2 + record DTOs on 2026-09-16). {@code EVERYTHING} forces a type tag on
     * the root value too, closing this gap. (Bare-list caches like
     * {@code policy.active-version} happened to work under {@code NON_FINAL} because Jackson
     * types root-level Collections regardless of typing mode — that was coincidence, not a
     * working case of {@code NON_FINAL}.)
     */
    @SuppressWarnings("deprecation") // DefaultTyping.EVERYTHING is required, see above
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory,
                                                ObjectMapper objectMapper) {
        ObjectMapper redisMapper = objectMapper.copy();
        redisMapper.activateDefaultTyping(
                redisMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer jsonValueSerializer =
                new GenericJackson2JsonRedisSerializer(redisMapper);

        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(redisCacheTtlMinutes))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonValueSerializer));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfiguration)
                .build();
    }
}
