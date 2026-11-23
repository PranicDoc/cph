package com.pranicdoc.cph.common.feature;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import com.pranicdoc.cph.review.controller.HealerReviewController;
import com.pranicdoc.cph.review.service.ReviewCommentService;
import com.pranicdoc.cph.review.service.ReviewSubmissionService;
import com.pranicdoc.cph.review.stream.EventStreamController;
import com.pranicdoc.cph.review.stream.EventStreamRegistry;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the 404 gate is wired to the real review controllers: the aspect is applied by Spring AOP,
 * runs after {@code @PreAuthorize} (403 beats 404) and covers the SSE route.
 */
@WebMvcTest({HealerReviewController.class, EventStreamController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AdminAuthz.class, SecurityContextUtil.class,
        CaseReviewGateAspect.class, CaseReviewGateWebTest.RbacAuthzSliceConfig.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
@ActiveProfiles("test")
class CaseReviewGateWebTest {

    @TestConfiguration
    static class RbacAuthzSliceConfig {
        @Bean
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil, RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private FeatureFlags featureFlags;
    @MockBean private ReviewSubmissionService service;
    @MockBean private ReviewCommentService commentService;
    @MockBean private EventStreamRegistry registry;
    @MockBean private RolePermissionLookupService lookupService;
    @MockBean private JwtService jwtService;
    @MockBean private TenantInterceptor tenantInterceptor;
    @MockBean private HealerCacheService healerCacheService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID CALLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "test-access-token";
    private static final String MY_SUBMISSIONS = "/api/v1/reviews/my-submissions";
    private static final String STREAM = "/api/v1/events/stream";

    private void authenticate(boolean hasCaseUpdate) throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(CALLER.toString());
        when(claims.get("role", String.class)).thenReturn("HEALER");
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(TOKEN)).thenReturn("ACCESS");
        when(jwtService.parseToken(TOKEN)).thenReturn(claims);
        when(healerCacheService.isActive(any())).thenReturn(true);
        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(lookupService.hasPermission(CALLER, Resource.CASE, Operation.UPDATE)).thenReturn(hasCaseUpdate);
    }

    @Test
    void flagOffHidesHealerRouteBehindA404() throws Exception {
        authenticate(true);
        when(featureFlags.caseReviewEnabled()).thenReturn(false);

        mockMvc.perform(get(MY_SUBMISSIONS).cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void flagOffStillAnswers403ToACallerWithoutCaseUpdate() throws Exception {
        authenticate(false);
        when(featureFlags.caseReviewEnabled()).thenReturn(false);

        mockMvc.perform(get(MY_SUBMISSIONS).cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void flagOnLetsTheHealerRouteThrough() throws Exception {
        authenticate(true);
        when(featureFlags.caseReviewEnabled()).thenReturn(true);

        mockMvc.perform(get(MY_SUBMISSIONS).cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void flagOffHidesTheEventStreamBehindA404() throws Exception {
        authenticate(false);
        when(featureFlags.caseReviewEnabled()).thenReturn(false);
        when(registry.open(CALLER)).thenReturn(new SseEmitter(1000L));

        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)
                        .cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(registry);
    }
}
