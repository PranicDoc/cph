package com.pranicdoc.cph.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the "InvalidTypeIdException: missing type id property '@class'"
 * error recurring in prod on every dashboard.* cache read that misses Caffeine's L1 and falls
 * through to this Redis L2 (observed 2026-09-16, ~5 days after 94dd8db's NON_FINAL default
 * typing fix went live — that fix was necessary but not sufficient).
 *
 * <p>Root cause: every {@code @Cacheable} method backed by this cache manager returns an
 * {@code ApiResponse<T>} record (or a bare {@code List<record>}). Under
 * {@code DefaultTyping.NON_FINAL}, Jackson's root-value serialization never tags the root
 * object itself with {@code @class} (only nested Object-declared fields) — but
 * {@code GenericJackson2JsonRedisSerializer}'s generic {@code deserialize(byte[])}, which is
 * what {@code RedisCache.get()} calls, always reads back with target type {@code Object.class},
 * which requires the root's own {@code @class}. {@code DefaultTyping.EVERYTHING} closes that
 * gap by tagging the root value too. Exercises the real serializer pair {@link RedisConfig}
 * builds, with no live Redis connection needed — write()/read() are pure in-memory
 * (de)serialization.
 */
class RedisConfigTest {

    private record TestDto(long count, Long nullableValue, boolean flag) {}

    private record TestWrapper<T>(boolean success, String message, T data) {}

    private RedisSerializationContext.SerializationPair<Object> valueSerializationPair() {
        RedisConfig redisConfig = new RedisConfig();
        RedisConnectionFactory connectionFactory = new LettuceConnectionFactory();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        RedisCacheManager manager = redisConfig.redisCacheManager(connectionFactory, objectMapper);
        RedisCache cache = (RedisCache) manager.getCache("dashboard.summary");
        return cache.getCacheConfiguration().getValueSerializationPair();
    }

    private Object roundTrip(RedisSerializationContext.SerializationPair<Object> pair, Object value) {
        ByteBuffer written = pair.write(value);
        return pair.read(written);
    }

    @Test
    void apiResponseWrappedRecordSurvivesRedisRoundTrip() {
        var pair = valueSerializationPair();
        TestWrapper<TestDto> value = new TestWrapper<>(true, "Success", new TestDto(5, null, true));

        assertThat(roundTrip(pair, value)).isEqualTo(value);
    }

    @Test
    void apiResponseWrappedListOfRecordsSurvivesRedisRoundTrip() {
        var pair = valueSerializationPair();
        TestWrapper<List<TestDto>> value = new TestWrapper<>(
                true, "Success", List.of(new TestDto(1, 2L, false), new TestDto(3, null, true)));

        assertThat(roundTrip(pair, value)).isEqualTo(value);
    }

    @Test
    void bareListOfRecordsSurvivesRedisRoundTrip() {
        var pair = valueSerializationPair();
        List<TestDto> value = List.of(new TestDto(1, 2L, false), new TestDto(3, null, true));

        assertThat(roundTrip(pair, value)).isEqualTo(value);
    }
}
