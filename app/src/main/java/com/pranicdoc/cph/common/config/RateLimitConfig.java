package com.pranicdoc.cph.common.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Bucket4j Redis-backed proxy manager for rate-limiting (INFRA-02, D-12).
 *
 * <p>Gated behind {@code app.ratelimit.enabled} (default {@code true} via
 * {@code matchIfMissing}) — {@code application-localdev.yml} sets it to {@code false} since
 * local dev has no Redis instance and {@code Bucket4jLettuce}'s builder opens a real TCP
 * connection synchronously at bean-creation time (unlike Spring Data Redis's
 * lazily-connecting {@code LettuceConnectionFactory}, see {@link RedisConfig}). With this
 * disabled, {@link com.pranicdoc.cph.common.ratelimit.RateLimitFilter} (same condition) also
 * does not exist as a bean, and {@code SecurityConfig} skips wiring it into the filter chain
 * — rate limiting is simply off, not failing open per-request.
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
@ConditionalOnProperty(
        name = "app.ratelimit.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean redisSslEnabled;

    private RedisClient redisClient;

    /**
     * Exposed as the {@link ProxyManager} interface (not the concrete
     * {@code LettuceBasedProxyManager}) so {@link com.pranicdoc.cph.common.ratelimit.RateLimitFilter}
     * depends only on the abstraction Bucket4j itself defines — keeps the filter trivially
     * mockable in unit tests and decoupled from the Lettuce-specific builder.
     */
    @Bean
    public ProxyManager<byte[]> rateLimitProxyManager() {
        RedisURI.Builder uriBuilder = RedisURI.Builder.redis(redisHost, redisPort).withSsl(redisSslEnabled);
        if (redisPassword != null && !redisPassword.isBlank()) {
            uriBuilder.withPassword(redisPassword);
        }
        this.redisClient = RedisClient.create(uriBuilder.build());
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
