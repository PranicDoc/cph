package com.pranicdoc.cph.common.tracing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceContextFilterTest {

    private final TraceContextFilter filter = new TraceContextFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cases");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> seenRequestId = new AtomicReference<>();
    private final AtomicReference<String> seenCorrelationId = new AtomicReference<>();
    private final MockFilterChain chain = new MockFilterChain() {
        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            seenRequestId.set(RequestContext.requestId());
            seenCorrelationId.set(RequestContext.correlationId());
        }
    };

    @AfterEach
    void cleanUp() {
        MDC.clear();
    }

    @Test
    void usesTheIdsNginxSet_andReturnsThem() throws Exception {
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "abcdef0123456789");
        request.addHeader(RequestContext.CORRELATION_ID_HEADER, "flow-id-12345");

        filter.doFilter(request, response, chain);

        assertThat(seenRequestId).hasValue("abcdef0123456789");
        assertThat(seenCorrelationId).hasValue("flow-id-12345");
        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER)).isEqualTo("abcdef0123456789");
        assertThat(response.getHeader(RequestContext.CORRELATION_ID_HEADER)).isEqualTo("flow-id-12345");
    }

    @Test
    void withoutHeaders_makesAnIdAndUsesItForBoth() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(seenRequestId.get()).isNotBlank();
        assertThat(seenCorrelationId.get()).isEqualTo(seenRequestId.get());
    }

    @Test
    void replacesMalformedIds_soTheyCannotForgeLogLines() throws Exception {
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "evil\r\nFAKE LOG LINE");
        request.addHeader(RequestContext.CORRELATION_ID_HEADER, "short");

        filter.doFilter(request, response, chain);

        assertThat(seenRequestId.get()).doesNotContain("evil").matches("^[A-Za-z0-9-]{8,64}$");
        assertThat(seenCorrelationId.get()).isNotEqualTo("short").matches("^[A-Za-z0-9-]{8,64}$");
    }

    @Test
    void leavesTheThreadClean_evenWhenTheRequestFails() {
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("boom");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failing)).hasMessageContaining("boom");

        assertThat(RequestContext.requestId()).isNull();
        assertThat(RequestContext.correlationId()).isNull();
    }

    @Test
    void healthChecksAreNotTraced() throws Exception {
        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/actuator/health");

        filter.doFilter(health, response, chain);

        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER)).isNull();
    }
}
