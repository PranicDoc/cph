package com.pranicdoc.cph.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.exception.RateLimitExceededException;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitFilter} (INFRA-02, D-11/D-12/D-13).
 *
 * Plain Mockito, no Spring context — mirrors the project's established filter/service test
 * style (see DashboardServiceImplTest). Bucket4j's {@link ProxyManager} and {@link BucketProxy}
 * are mocked directly rather than backed by a live/embedded Redis: this environment has no
 * Docker access this session (same documented gap as 23-02/23-05), so bucket-state behavior
 * (threshold boundary, per-key isolation, fail-open on error) is verified by controlling the
 * mock's {@code tryConsume}/exception behavior per key rather than a real Redis round-trip.
 * A live-Redis end-to-end run remains an outstanding follow-up — see 23-06-SUMMARY.md.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(proxyManager, handlerExceptionResolver, objectMapper);
    }

    // --- helpers -----------------------------------------------------------------------

    private void stubRequest(String servletPath, String jsonBody, String forwardedFor, String remoteAddr) throws IOException {
        when(request.getServletPath()).thenReturn(servletPath);
        byte[] bodyBytes = jsonBody == null ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8);
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(bodyBytes);

            @Override
            public boolean isFinished() {
                return delegate.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // no-op
            }

            @Override
            public int read() {
                return delegate.read();
            }
        });
        if (forwardedFor != null) {
            when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        }
        if (remoteAddr != null) {
            lenient().when(request.getRemoteAddr()).thenReturn(remoteAddr);
        }
    }

    /** A bucket mock whose tryConsume always returns {@code allow}. */
    private BucketProxy bucketThatReturns(boolean allow) {
        BucketProxy bucket = mock(BucketProxy.class);
        when(bucket.tryConsume(1)).thenReturn(allow);
        return bucket;
    }

    private void stubBucketByKeySubstring(String keySubstring, BucketProxy bucket) {
        when(proxyManager.getProxy(
                argThat(key -> key != null && new String(key, StandardCharsets.UTF_8).contains(keySubstring)),
                any()))
                .thenReturn(bucket);
    }

    // --- Behavior 1: under threshold, /login passes ------------------------------------

    @Test
    void login_underThreshold_passesThroughFilterChain() throws Exception {
        stubRequest("/api/v1/auth/login", "{\"email\":\"healer@example.com\",\"password\":\"secret\"}",
                "203.0.113.10", null);
        stubBucketByKeySubstring(":ip:", bucketThatReturns(true));
        stubBucketByKeySubstring(":email:", bucketThatReturns(true));

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(any(HttpServletRequest.class), eq(response));
        verifyNoInteractions(handlerExceptionResolver);
    }

    // --- Behavior 2: over threshold -> 429 with rate-limit-exceeded type ---------------

    @Test
    void login_ipBucketExhausted_rejectsWith429ViaHandlerExceptionResolver() throws Exception {
        stubRequest("/api/v1/auth/login", "{\"email\":\"healer@example.com\",\"password\":\"secret\"}",
                "203.0.113.10", null);
        stubBucketByKeySubstring(":ip:", bucketThatReturns(false));

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<Exception> exCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(handlerExceptionResolver, times(1))
                .resolveException(eq(request), eq(response), eq(null), exCaptor.capture());
        assertInstanceOf(RateLimitExceededException.class, exCaptor.getValue());
        verify(filterChain, never()).doFilter(any(), any());

        // Independently confirm GlobalExceptionHandler renders the RFC 7807 429 shape
        // for the exact exception type this filter throws.
        ProblemDetail pd = globalExceptionHandler.handleRateLimitExceeded(
                (RateLimitExceededException) exCaptor.getValue());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), pd.getStatus());
        assertEquals("https://pranicdoc.com/errors/rate-limit-exceeded", pd.getType().toString());
    }

    // --- Behavior 3: per-email limiting triggers independently of per-IP ---------------

    @Test
    void signup_emailBucketExhausted_acrossDifferentIps_stillRejects() throws Exception {
        stubRequest("/api/v1/auth/signup", "{\"email\":\"burst@example.com\"}", "198.51.100.7", null);
        // IP bucket has capacity (this is a fresh/different IP for this request)...
        stubBucketByKeySubstring(":ip:", bucketThatReturns(true));
        // ...but the same email has already exhausted its bucket from prior requests
        // (simulating a burst against the same email from multiple different IPs).
        stubBucketByKeySubstring(":email:", bucketThatReturns(false));

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<Exception> exCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(handlerExceptionResolver, times(1))
                .resolveException(eq(request), eq(response), eq(null), exCaptor.capture());
        assertInstanceOf(RateLimitExceededException.class, exCaptor.getValue());
        assertTrue(exCaptor.getValue().getMessage().contains("account"),
                "email-bucket rejection message should be distinguishable from the IP-bucket one");
        verify(filterChain, never()).doFilter(any(), any());
    }

    // --- Behavior 4: fails open on simulated Redis unavailability ----------------------

    @Test
    void login_redisUnavailable_failsOpenAndProceeds() throws Exception {
        stubRequest("/api/v1/auth/login", "{\"email\":\"healer@example.com\",\"password\":\"secret\"}",
                "203.0.113.10", null);
        when(proxyManager.getProxy(any(byte[].class), any()))
                .thenThrow(new RuntimeException("Connection refused: Redis unavailable"));

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(any(HttpServletRequest.class), eq(response));
        verifyNoInteractions(handlerExceptionResolver);
    }

    // --- Non-target endpoint is never limited -------------------------------------------

    @Test
    void refresh_isNotATargetEndpoint_neverConsultsProxyManager() throws Exception {
        when(request.getServletPath()).thenReturn("/api/v1/auth/refresh");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(proxyManager);
        verifyNoInteractions(handlerExceptionResolver);
    }

    // --- X-Forwarded-For last-hop resolution (T-23-15) ----------------------------------

    @Test
    void login_trustsLastForwardedForHop_notFirst() throws Exception {
        // Attacker-forged first hop, real nginx-appended hop last.
        stubRequest("/api/v1/auth/login", "{\"email\":\"healer@example.com\",\"password\":\"secret\"}",
                "9.9.9.9, 203.0.113.10", null);
        stubBucketByKeySubstring(":ip:203.0.113.10", bucketThatReturns(true));
        stubBucketByKeySubstring(":email:", bucketThatReturns(true));

        filter.doFilter(request, response, filterChain);

        verify(proxyManager, atLeastOnce()).getProxy(
                argThat(key -> new String(key, StandardCharsets.UTF_8).contains(":ip:203.0.113.10")
                        && !new String(key, StandardCharsets.UTF_8).contains("9.9.9.9")),
                any());
        verify(filterChain, times(1)).doFilter(any(HttpServletRequest.class), eq(response));
    }
}
