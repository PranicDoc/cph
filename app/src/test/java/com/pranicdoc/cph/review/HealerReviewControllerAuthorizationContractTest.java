package com.pranicdoc.cph.review;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.exception.ReviewRefusedException;
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
import com.pranicdoc.cph.review.controller.HealerReviewController;
import com.pranicdoc.cph.review.model.SubmittedVia;
import com.pranicdoc.cph.review.service.ReviewStorageException;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The healer side of case review is gated CASE:UPDATE on every route: a caller without it gets
 * 403 and the service is never reached.
 */
@WebMvcTest(HealerReviewController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AdminAuthz.class, SecurityContextUtil.class,
        HealerReviewControllerAuthorizationContractTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class HealerReviewControllerAuthorizationContractTest {

    @TestConfiguration
    static class RbacAuthzSliceConfig {
        @Bean
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil, RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private ReviewSubmissionService service;
    @MockBean private com.pranicdoc.cph.review.service.ReviewCommentService commentService;
    @MockBean private RolePermissionLookupService lookupService;
    @MockBean private JwtService jwtService;
    @MockBean private TenantInterceptor tenantInterceptor;
    @MockBean private HealerCacheService healerCacheService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID CALLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String TOKEN = "test-access-token";
    private static final String BASE = "/api/v1/reviews";

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

    private static MockHttpServletRequestBuilder request(String method, String path) {
        MockHttpServletRequestBuilder b = "POST".equals(method) ? post(path) : get(path);
        return b.cookie(new Cookie("accessToken", TOKEN))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"revisionNo\":1,\"body\":\"x\"}");
    }

    private static final String ROUTES = """
            POST,/cases/00000000-0000-0000-0000-0000000000c1/submit
            GET,/my-submissions
            GET,/my-approved-count
            GET,/case-statuses?caseIds=00000000-0000-0000-0000-0000000000c1
            GET,/submissions/00000000-0000-0000-0000-0000000000c1
            GET,/submissions/00000000-0000-0000-0000-0000000000c1/revisions/1/file
            GET,/submissions/00000000-0000-0000-0000-0000000000c1/comments
            POST,/submissions/00000000-0000-0000-0000-0000000000c1/comments
            """;

    @ParameterizedTest(name = "{0} {1} -> 403 without CASE:UPDATE")
    @CsvSource(textBlock = ROUTES)
    void withoutCaseUpdateGets403(String method, String path) throws Exception {
        authenticate(false);
        mockMvc.perform(request(method, BASE + path)).andExpect(status().isForbidden());
        verifyNoInteractions(service, commentService);
    }

    @ParameterizedTest(name = "{0} {1} -> 2xx with CASE:UPDATE")
    @CsvSource(textBlock = ROUTES)
    void withCaseUpdateIsAdmitted(String method, String path) throws Exception {
        authenticate(true);
        when(service.getFileUrl(any(), anyInt())).thenReturn(new PreSignedUrlResponse("https://r2/x", Instant.now()));
        when(service.statusesFor(any())).thenReturn(Map.of());
        when(service.mySubmissions()).thenReturn(List.of());
        mockMvc.perform(request(method, BASE + path)).andExpect(status().is2xxSuccessful());
    }

    @Test
    void invalidCommentBodyIs400WithTheValidationShape() throws Exception {
        authenticate(true);
        mockMvc.perform(post(BASE + "/submissions/" + CASE_ID + "/comments").cookie(new Cookie("accessToken", TOKEN))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"revisionNo\":1,\"page\":1,\"x\":1.5,\"y\":0.5,\"body\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.title")
                        .value("Validation Error"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errors.x")
                        .exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errors.body")
                        .exists());
        verifyNoInteractions(commentService);
    }

    @Test
    void blockedSubmitIsA409CarryingTheVerbatimMessage() throws Exception {
        authenticate(true);
        String blocked = "You don't have an instructor linked yet. Ask your administrator to link one before submitting.";
        when(service.submit(eq(CASE_ID), eq(SubmittedVia.MANUAL))).thenThrow(new ReviewRefusedException(blocked));

        mockMvc.perform(request("POST", BASE + "/cases/" + CASE_ID + "/submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(blocked));
        verify(service).submit(CASE_ID, SubmittedVia.MANUAL);
    }

    @Test
    void storageFailureIsA5xxNotA409() throws Exception {
        authenticate(true);
        when(service.submit(eq(CASE_ID), eq(SubmittedVia.MANUAL)))
                .thenThrow(new ReviewStorageException("Could not read the PDF from storage"));

        mockMvc.perform(request("POST", BASE + "/cases/" + CASE_ID + "/submit"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void approvedCountReturnsCountObject() throws Exception {
        authenticate(true);
        when(service.approvedCount(CALLER)).thenReturn(4L);

        mockMvc.perform(request("GET", BASE + "/my-approved-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }
}
