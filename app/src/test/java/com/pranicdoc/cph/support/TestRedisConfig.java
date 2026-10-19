package com.pranicdoc.cph.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The test profile excludes RedisAutoConfiguration (no Redis in tests), but RedisConfig's
 * redisCacheManager still needs a RedisConnectionFactory. This one behaves like Redis being
 * down: every connection attempt throws RedisConnectionFailureException, which TwoLevelCache
 * already treats as "L2 unavailable" and degrades to Caffeine L1 / the database, as in prod.
 */
@TestConfiguration
public class TestRedisConfig {

    @Bean
    RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RedisConnectionFailureException("no Redis in tests"));
        return factory;
    }
}
