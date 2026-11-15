package com.pranicdoc.cph.review.stream;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The events stream is self-service: any signed-in healer opens their own stream without a grant,
 * a patient link token is refused, and an anonymous caller gets 401.
 */
@WebMvcTest(EventStreamController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AdminAuthz.class, SecurityContextUtil.class})
@ActiveProfiles("test")
class EventStreamControllerAuthorizationContractTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private EventStreamRegistry registry;
    @MockBean private JwtService jwtService;
    @MockBean private TenantInterceptor tenantInterceptor;
    @MockBean private HealerCacheService healerCacheService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID CALLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "test-access-token";
    private static final String STREAM = "/api/v1/events/stream";

    private void authenticate(String tokenType, String role) throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(CALLER.toString());
        when(claims.get("role", String.class)).thenReturn(role);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(TOKEN)).thenReturn(tokenType);
        when(jwtService.parseToken(TOKEN)).thenReturn(claims);
        when(healerCacheService.isActive(any())).thenReturn(true);
        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void grantlessHealerOpensTheirOwnStream() throws Exception {
        authenticate("ACCESS", "HEALER");
        when(registry.open(CALLER)).thenReturn(new SseEmitter(1000L));

        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)
                        .cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(registry).open(CALLER);
    }

    @Test
    void patientLinkTokenIsRefused() throws Exception {
        authenticate("SELF_REG", "PATIENT");

        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)
                        .cookie(new Cookie("selfRegToken", TOKEN)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registry);
    }

    @Test
    void theOldReviewsStreamRouteIsGone() throws Exception {
        authenticate("ACCESS", "HEALER");

        mockMvc.perform(get("/api/v1/reviews/stream").accept(MediaType.TEXT_EVENT_STREAM)
                        .cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(registry);
    }

    @Test
    void anonymousCallerGets401() throws Exception {
        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(registry);
    }
}
