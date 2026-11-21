package com.pranicdoc.cph.review;

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
import com.pranicdoc.cph.review.controller.ReviewerController;
import com.pranicdoc.cph.review.service.ReviewCommentService;
import com.pranicdoc.cph.review.service.ReviewInboxService;
import com.pranicdoc.cph.review.service.ReviewSubmissionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A patient-role token (SELF_REG link) carries the owning healer's id as its subject, so it would resolve
 * that healer's CASE:UPDATE / CASE:REVIEW grants. SecurityConfig must refuse it on every review route.
 * The grants are given to the caller here, so a 403 can only come from the URL-level rule.
 */
@WebMvcTest({HealerReviewController.class, ReviewerController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AdminAuthz.class, SecurityContextUtil.class,
        ReviewRoutesPatientTokenRefusedTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class ReviewRoutesPatientTokenRefusedTest {

    @TestConfiguration
    static class RbacAuthzSliceConfig {
        @Bean
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil, RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private ReviewSubmissionService submissionService;
    @MockBean private ReviewInboxService inboxService;
    @MockBean private ReviewCommentService commentService;
    @MockBean private RolePermissionLookupService lookupService;
    @MockBean private JwtService jwtService;
    @MockBean private TenantInterceptor tenantInterceptor;
    @MockBean private HealerCacheService healerCacheService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "test-token";
    private static final String CASE_ID = "00000000-0000-0000-0000-0000000000c1";

    private void authenticate(String tokenType, String role) throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(OWNER.toString());
        when(claims.get("role", String.class)).thenReturn(role);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(TOKEN)).thenReturn(tokenType);
        when(jwtService.parseToken(TOKEN)).thenReturn(claims);
        when(healerCacheService.isActive(any())).thenReturn(true);
        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(lookupService.hasPermission(OWNER, Resource.CASE, Operation.UPDATE)).thenReturn(true);
        when(lookupService.hasPermission(OWNER, Resource.CASE, Operation.REVIEW)).thenReturn(true);
    }

    private static MockHttpServletRequestBuilder request(String method, String path) {
        return "POST".equals(method) ? post(path) : get(path);
    }

    private static final String ROUTES = """
            POST,/api/v1/reviews/cases/00000000-0000-0000-0000-0000000000c1/submit
            GET,/api/v1/reviews/my-submissions
            GET,/api/v1/reviews/inbox
            POST,/api/v1/reviews/inbox/00000000-0000-0000-0000-0000000000c1/approve
            """;

    @ParameterizedTest(name = "{0} {1} -> 403 for a patient link token")
    @CsvSource(textBlock = ROUTES)
    void patientLinkTokenIsRefused(String method, String path) throws Exception {
        authenticate("SELF_REG", "PATIENT");

        mockMvc.perform(request(method, path).cookie(new Cookie("selfRegToken", TOKEN))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(submissionService, inboxService, commentService);
    }

    @Test
    void healerTokenStillReachesTheRoutes() throws Exception {
        authenticate("ACCESS", "HEALER");

        mockMvc.perform(get("/api/v1/reviews/my-submissions").cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reviews/inbox").cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCallerGets401() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/inbox")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reviews/my-submissions")).andExpect(status().isUnauthorized());

        verifyNoInteractions(submissionService, inboxService, commentService);
    }
}
