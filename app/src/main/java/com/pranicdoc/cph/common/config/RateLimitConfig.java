package com.pranicdoc.cph.common.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Bucket4j Redis-backed proxy manager for rate-limiting (INFRA-02, D-12).
 *
 * <p><b>Why a dedicated {@link RedisClient} instead of reusing {@link RedisConfig}'s
 * autoconfigured {@code RedisConnectionFactory}:</b> Bucket4j's {@code LettuceBasedProxyManager}
 * requires a raw {@code ByteArrayCodec}-based Lettuce connection
 * ({@code StatefulRedisConnection<byte[], byte[]>}); Spring Data Redis's autoconfigured
 * {@code LettuceConnectionFactory} is built around its own typed serializers for
 * {@code RedisCacheManager}/{@code RedisTemplate} use and does not expose that raw codec
 * shape without reflectively reaching into its native client — fragile across Spring Data
 * Redis versions. A second lightweight {@link RedisClient} pointed at the exact same
 * {@code SPRING_DATA_REDIS_HOST}/{@code SPRING_DATA_REDIS_PORT} Redis instance (same env
 * vars {@link RedisConfig} relies on) is the standard Bucket4j integration pattern and adds
 * only one extra TCP connection to the same Redis container.
 */
@Configuration
public class RateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private RedisClient redisClient;

    /**
     * Exposed as the {@link ProxyManager} interface (not the concrete
     * {@code LettuceBasedProxyManager}) so {@link com.pranicdoc.cph.common.ratelimit.RateLimitFilter}
     * depends only on the abstraction Bucket4j itself defines — keeps the filter trivially
     * mockable in unit tests and decoupled from the Lettuce-specific builder.
     */
    @Bean
    public ProxyManager<byte[]> rateLimitProxyManager() {
        this.redisClient = RedisClient.create(RedisURI.Builder.redis(redisHost, redisPort).build());
        return Bucket4jLettuce.casBasedBuilder(redisClient)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }

    @PreDestroy
    public void shutdownRedisClient() {
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}
