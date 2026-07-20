package com.pranicdoc.cph.common.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Redis-backed per-IP + per-email rate limiter for the three public, unauthenticated auth
 * endpoints (INFRA-02, D-11/D-12/D-13). This is the anti-automation control for the
 * `permitAll` surface — everything else in {@code SecurityConfig}'s permitAll list
 * ({@code /refresh}, {@code /logout}, {@code /init-session}, {@code /init-feedback-session},
 * {@code /bootstrap}, {@code /health}, {@code /webhooks/whatsapp}, docs) is deliberately
 * NOT limited here: those either need an existing valid token/session already or are pure
 * infrastructure endpoints with no credential-guessing/registration-flood surface.
 *
 * <p><b>Thresholds (Claude's Discretion, documented in 23-06-SUMMARY.md):</b>
 * <ul>
 *   <li>{@code /login} — 10 attempts / 1 minute per key (brute-force guard; tighter than
 *       signup because credential stuffing is the primary threat, D-12's driving concern)</li>
 *   <li>{@code /signup} — 5 attempts / 1 minute per key (registration/verification-email flood
 *       guard; lower because a legitimate user only signs up once)</li>
 *   <li>{@code /accept-invite} — 10 attempts / 1 minute per key (invite-token guessing guard;
 *       has no per-email component — see the note on {@code AcceptInviteRequest} below)</li>
 * </ul>
 * Each rule enforces TWO independent Bucket4j buckets per request — one keyed by resolved
 * client IP, one keyed by the request body's {@code email} field when present — either
 * bucket exhausting rejects the request with 429.
 *
 * <p><b>{@code accept-invite} has no email field</b> (its {@code AcceptInviteRequest} DTO is
 * deliberately email-less — the invited person's email was fixed at invite-creation time, not
 * re-submitted). Per-email limiting is therefore skipped for that endpoint; per-IP limiting
 * alone still applies, matching the plan's "email from the request body ... where present"
 * instruction.
 *
 * <p><b>Client IP resolution (T-23-15 mitigation):</b> {@code cph} is {@code expose}-only in
 * {@code docker-compose.yml} (never directly reachable from outside the internal Docker
 * network) — every request that reaches this filter has already passed through the trusted
 * nginx ingress. Both nginx's {@code $proxy_add_x_forwarded_for} directive and Cloud Run's
 * load balancer (the deployment target of Plan 23-07) append the real, trusted peer address
 * as the LAST entry of {@code X-Forwarded-For}, regardless of what a client prepends. Trusting
 * the FIRST entry would let an attacker forge arbitrary earlier hops to rotate the IP-keyed
 * bucket and evade the limit; this filter always takes the last hop, falling back to
 * {@link HttpServletRequest#getRemoteAddr()} when the header is absent.
 *
 * <p><b>Fail-open on Redis unavailability (T-23-16, RESEARCH.md Pitfall 2):</b> if the
 * Bucket4j/Redis call itself throws (connection failure, timeout), the request is allowed
 * through and a warning is logged, rather than blocking all login/signup traffic on a Redis
 * blip. Availability of auth endpoints outweighs strict limiting for this non-payment surface.
 *
 * <p><b>Why a manually-invoked {@link HandlerExceptionResolver} instead of just throwing:</b>
 * this filter (registered via {@code SecurityConfig.addFilterBefore}) runs inside the servlet
 * Filter chain, BEFORE {@code DispatcherServlet} — {@code @RestControllerAdvice}'s
 * {@code @ExceptionHandler} methods are only consulted for exceptions raised during Spring
 * MVC's own handler dispatch, so an exception simply thrown from a filter would propagate to
 * the servlet container's generic error page instead of {@code GlobalExceptionHandler}'s RFC
 * 7807 shape. Delegating to the Spring-Boot-autoconfigured {@code handlerExceptionResolver}
 * bean (the standard, widely-documented workaround for this exact gap) routes the exception
 * through the same {@code ExceptionHandlerExceptionResolver} that backs
 * {@code @RestControllerAdvice}, so {@code GlobalExceptionHandler.handleRateLimitExceeded}
 * still renders the response — matching the plan's "PREFER throwing ... caught by
 * GlobalExceptionHandler" intent without silently dropping the 429 shape.
 *
 * <p><b>Why {@code @Lazy} on the {@code ProxyManager} constructor parameter:</b> Bucket4j's
 * {@code Bucket4jLettuce.casBasedBuilder(RedisClient)} synchronously opens a real TCP
 * connection to Redis at proxy-manager construction time (unlike Spring Data Redis's
 * autoconfigured {@code LettuceConnectionFactory}, which connects lazily on first cache
 * operation). Without {@code @Lazy} here, any test — even a plain {@code @SpringBootTest}
 * with no interest in rate-limiting — would fail application-context startup the moment
 * Redis is unreachable (this filter is a {@code @Component}, eagerly instantiated at boot).
 * {@code @Lazy} makes Spring inject a deferred-resolution proxy instead, so the real
 * {@code RateLimitConfig.rateLimitProxyManager()} bean (and its Redis connection) is only
 * built the first time this filter actually processes a rate-limited request.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<byte[]> rateLimitProxyManager;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final ObjectMapper objectMapper;

    private static final Map<String, RateLimitRule> RULES = Map.of(
            "/api/v1/auth/login", new RateLimitRule("login", 10, Duration.ofMinutes(1)),
            "/api/v1/auth/signup", new RateLimitRule("signup", 5, Duration.ofMinutes(1)),
            "/api/v1/auth/accept-invite", new RateLimitRule("accept-invite", 10, Duration.ofMinutes(1))
    );

    public RateLimitFilter(@Lazy ProxyManager<byte[]> rateLimitProxyManager,
                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
                            ObjectMapper objectMapper) {
        this.rateLimitProxyManager = rateLimitProxyManager;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        RateLimitRule rule = RULES.get(request.getServletPath());
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest requestForChain = request;

        try {
            String clientIp = resolveClientIp(request);

            CachedBodyRequestWrapper cachedBody = new CachedBodyRequestWrapper(request);
            requestForChain = cachedBody;
            String email = extractEmail(cachedBody.getCachedBody());

            if (!tryConsume(rule.name() + ":ip:" + clientIp, rule)) {
                throw new RateLimitExceededException(
                        "Too many " + rule.name() + " attempts from this network. Please wait and try again.");
            }
            if (StringUtils.isNotBlank(email) && !tryConsume(rule.name() + ":email:" + email.toLowerCase(), rule)) {
                throw new RateLimitExceededException(
                        "Too many " + rule.name() + " attempts for this account. Please wait and try again.");
            }
        } catch (RateLimitExceededException limitExceeded) {
            handlerExceptionResolver.resolveException(request, response, null, limitExceeded);
            return;
        } catch (Exception redisFailure) {
            // Fail-open (T-23-16, Pitfall 2): a Redis blip must not take down auth endpoints.
            log.warn("Rate limiter failed open on {} due to: {}", request.getServletPath(), redisFailure.getMessage());
        }

        filterChain.doFilter(requestForChain, response);
    }

    private boolean tryConsume(String key, RateLimitRule rule) {
        byte[] keyBytes = ("ratelimit:" + key).getBytes(StandardCharsets.UTF_8);
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(rule.limit()).refillIntervally(rule.limit(), rule.window()).build())
                .build();
        return rateLimitProxyManager.getProxy(keyBytes, configSupplier).tryConsume(1);
    }

    /**
     * Resolves the trusted client IP. See class javadoc's "Client IP resolution" section.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            String[] hops = forwardedFor.split(",");
            String lastHop = hops[hops.length - 1].trim();
            if (StringUtils.isNotBlank(lastHop)) {
                return lastHop;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Best-effort extraction of a top-level {@code "email"} JSON field from the request body.
     * Returns {@code null} on any malformed/missing body — deliberately does not reject the
     * request itself, that is the controller's own {@code @Valid} responsibility; this filter
     * only skips the per-email bucket when it cannot find one (e.g. {@code accept-invite},
     * which has no email field at all).
     */
    private String extractEmail(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode emailNode = node.get("email");
            return (emailNode != null && !emailNode.isNull()) ? emailNode.asText() : null;
        } catch (IOException malformedBody) {
            return null;
        }
    }

    private record RateLimitRule(String name, long limit, Duration window) {
    }

    /**
     * Buffers the full request body up-front so it can be read here (for {@code email}
     * extraction) AND still be read again downstream by Spring MVC's message converter when
     * the request reaches the controller. {@code jakarta.servlet}'s
     * {@code ContentCachingRequestWrapper}-style wrappers only cache bytes as they are
     * consumed by the FIRST reader and do not themselves replay on a second
     * {@code getInputStream()} call, so a small custom wrapper backed by an already-fully-read
     * byte array is used instead — the standard fix for "read body in a filter without
     * breaking the controller's own read".
     */
    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // no-op: fully-buffered, always ready
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
