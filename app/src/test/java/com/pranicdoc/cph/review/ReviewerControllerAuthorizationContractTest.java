package com.pranicdoc.cph.review;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.healing.dto.PreSignedUrlResponse;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.model.Operation;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import com.pranicdoc.cph.review.controller.ReviewerController;
import com.pranicdoc.cph.review.service.ReviewInboxService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The instructor side of case review is gated CASE:REVIEW on every route: a caller without it gets
 * 403 and the service is never reached.
 */
@WebMvcTest(ReviewerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AdminAuthz.class, SecurityContextUtil.class,
        ReviewerControllerAuthorizationContractTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class ReviewerControllerAuthorizationContractTest {

    @TestConfiguration
    static class RbacAuthzSliceConfig {
        @Bean
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil, RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private ReviewInboxService service;
    @MockBean private RolePermissionLookupService lookupService;
    @MockBean private JwtService jwtService;
    @MockBean private TenantInterceptor tenantInterceptor;
    @MockBean private HealerCacheService healerCacheService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID CALLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBMISSION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String TOKEN = "test-access-token";
    private static final String BASE = "/api/v1/reviews";

    private void authenticate(boolean hasCaseReview) throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(CALLER.toString());
        when(claims.get("role", String.class)).thenReturn("HEALER");
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(TOKEN)).thenReturn("ACCESS");
        when(jwtService.parseToken(TOKEN)).thenReturn(claims);
        when(healerCacheService.isActive(any())).thenReturn(true);
        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(lookupService.hasPermission(CALLER, Resource.CASE, Operation.REVIEW)).thenReturn(hasCaseReview);
    }

    private static final String ROUTES = """
            /inbox
            /inbox/00000000-0000-0000-0000-0000000000c1
            /inbox/00000000-0000-0000-0000-0000000000c1/revisions/1/file
            """;

    @ParameterizedTest(name = "GET {0} -> 403 without CASE:REVIEW")
    @CsvSource(textBlock = ROUTES)
    void withoutCaseReviewGets403(String path) throws Exception {
        authenticate(false);
        mockMvc.perform(get(BASE + path).cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "GET {0} -> 2xx with CASE:REVIEW")
    @CsvSource(textBlock = ROUTES)
    void withCaseReviewIsAdmitted(String path) throws Exception {
        authenticate(true);
        when(service.getFileUrl(any(), anyInt())).thenReturn(new PreSignedUrlResponse("https://r2/x", Instant.now()));
        when(service.inbox(any())).thenReturn(List.of());
        mockMvc.perform(get(BASE + path).cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void reassignedReviewerGets403FromTheRowLevelCheck() throws Exception {
        authenticate(true);
        when(service.getItem(SUBMISSION_ID)).thenThrow(new AccessDeniedException("Submission is not assigned to you"));
        mockMvc.perform(get(BASE + "/inbox/" + SUBMISSION_ID).cookie(new Cookie("accessToken", TOKEN)))
                .andExpect(status().isForbidden());
    }
}
