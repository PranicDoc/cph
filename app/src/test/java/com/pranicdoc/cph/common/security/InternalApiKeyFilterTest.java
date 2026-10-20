package com.pranicdoc.cph.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalApiKeyFilterTest {

    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void correctKey_authenticatesAsInternalAndContinues() throws Exception {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        FilterChain capturing = (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication());

        new InternalApiKeyFilter("s3cret").doFilter(request("s3cret"), response, capturing);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_INTERNAL");
    }

    @Test
    void wrongKey_is401AndStops() throws Exception {
        new InternalApiKeyFilter("s3cret").doFilter(request("nope"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void missingKey_is401AndStops() throws Exception {
        new InternalApiKeyFilter("s3cret").doFilter(request(null), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void noKeyConfigured_is404_asIfTheEndpointDidNotExist() throws Exception {
        new InternalApiKeyFilter("").doFilter(request(""), response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        verify(chain, never()).doFilter(any(), any());
    }

    private static MockHttpServletRequest request(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/notifications/sweep");
        if (key != null) {
            request.addHeader(InternalApiKeyFilter.HEADER, key);
        }
        return request;
    }
}
