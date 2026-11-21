package com.pranicdoc.cph.review.controller;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import com.pranicdoc.cph.review.service.ReviewerAssignmentService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reviewer-assignment admin routes are SUPADM-only: an ordinary healer (even one holding
 * CASE:REVIEW) gets 403 on all four, and the service is never reached.
 */
@WebMvcTest(AdminReviewerAssignmentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AdminAuthz.class, SecurityContextUtil.class,
        AdminReviewerAssignmentControllerAuthorizationContractTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class AdminReviewerAssignmentControllerAuthorizationContractTest {

    @TestConfiguration
    static class RbacAuthzSliceConfig {
        @Bean
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil, RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private ReviewerAssignmentService service;
    @MockBean private RolePermissionLookupService lookupService;
    @MockBean private JwtService jwtService;
    @MockBean private TenantInterceptor tenantInterceptor;
    @MockBean private HealerCacheService healerCacheService;
    @MockBean private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    // SecurityContextUtil is a real bean here: it reads the authenticated principal set by JwtAuthFilter.

    private static final UUID CALLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String TOKEN = "test-access-token";
    private static final String BASE = "/api/v1/admin/reviewer-assignments";

    private void authenticateAs(String role) throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(CALLER.toString());
        when(claims.get("role", String.class)).thenReturn(role);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(TOKEN)).thenReturn("ACCESS");
        when(jwtService.parseToken(TOKEN)).thenReturn(claims);
        when(healerCacheService.isActive(any())).thenReturn(true);
        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    private static MockHttpServletRequestBuilder request(String method, String path) {
        MockHttpServletRequestBuilder b = switch (method) {
            case "GET" -> get(path);
            case "PUT" -> put(path).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reviewerId\":\"" + UUID.randomUUID() + "\"}");
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        return b.cookie(new Cookie("accessToken", TOKEN));
    }

    @ParameterizedTest(name = "{0} {1} -> 403 for a healer")
    @CsvSource({
            "GET,''",
            "GET,/eligible-reviewers",
            "PUT,/00000000-0000-0000-0000-0000000000ff",
            "DELETE,/00000000-0000-0000-0000-0000000000ff"
    })
    void nonSupadmGets403(String method, String suffix) throws Exception {
        authenticateAs("HEALER");
        mockMvc.perform(request(method, BASE + suffix)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "{0} {1} -> 2xx for SUPADM")
    @CsvSource({
            "GET,''",
            "GET,/eligible-reviewers",
            "PUT,/00000000-0000-0000-0000-0000000000ff",
            "DELETE,/00000000-0000-0000-0000-0000000000ff"
    })
    void supadmIsAdmitted(String method, String suffix) throws Exception {
        authenticateAs("SUPADM");
        when(service.list()).thenReturn(List.of());
        mockMvc.perform(request(method, BASE + suffix)).andExpect(status().is2xxSuccessful());
    }

    @Test
    void supadmUnassignDelegatesToService() throws Exception {
        authenticateAs("SUPADM");
        mockMvc.perform(request("DELETE", BASE + "/" + TARGET)).andExpect(status().isNoContent());
        verify(service).unassign(TARGET);
    }
}
