package com.pranicdoc.cph.masterdata.controller;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.masterdata.dto.ImportResult;
import com.pranicdoc.cph.masterdata.importer.MasterDataImportRegistry;
import com.pranicdoc.cph.masterdata.importer.MasterDataImporter;
import com.pranicdoc.cph.rbac.RbacAuthz;
import com.pranicdoc.cph.rbac.model.Resource;
import com.pranicdoc.cph.rbac.service.RolePermissionLookupService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavioral authorization proof for {@link MasterDataImportController}
 * (Phase 02, plan 02-07). Unlike {@code MasterDataControllerAuthorizationContractTest}'s
 * reflection pin on {@code @PreAuthorize}, this controller's single handler has
 * NO annotation to reflect on — the gate is an in-body dynamic {@code
 * rbacAuthz.hasPermission(...)} call (D-12), so its correctness can only be
 * proven behaviorally: send a real request, assert the real response status
 * and (for the fail-closed-ordering proof) zero interaction with the grant
 * lookup service.
 *
 * <p>Built on the same {@code @WebMvcTest(controller) + @Import(SecurityConfig)}
 * harness as {@code MasterDataControllerAuthorizationTest} (copied, not shared
 * across {@code @WebMvcTest} slices) — {@link RbacAuthz} is contributed from a
 * nested {@code @TestConfiguration} {@code @Bean} named {@code rbacAuthz}, not
 * {@code @Import(RbacAuthz.class)}.
 */
@WebMvcTest(MasterDataImportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        MasterDataImportControllerAuthorizationTest.RbacAuthzSliceConfig.class})
@ActiveProfiles("test")
class MasterDataImportControllerAuthorizationTest {

    @TestConfiguration
    static class RbacAuthzSliceConfig {
        @Bean
        RbacAuthz rbacAuthz(SecurityContextUtil securityContextUtil,
                            RolePermissionLookupService lookupService) {
            return new RbacAuthz(securityContextUtil, lookupService);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MasterDataImportRegistry registry;

    @MockBean
    private SecurityContextUtil securityContextUtil;
    @MockBean
    private RolePermissionLookupService lookupService;

    @MockBean
    private JwtService jwtService;
    @MockBean
    private TenantInterceptor tenantInterceptor;
    @MockBean
    private HealerCacheService healerCacheService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String IMPORT_URL = "/api/v1/masterdata/import";

    private void authenticateAs(String role) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(HEALER_ID.toString());
        when(claims.get("role", String.class)).thenReturn(role);
        when(jwtService.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(ACCESS_TOKEN)).thenReturn("ACCESS");
        when(jwtService.parseToken(ACCESS_TOKEN)).thenReturn(claims);
        when(healerCacheService.isActive(any())).thenReturn(true);
        try {
            when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Cookie accessCookie() {
        return new Cookie("accessToken", ACCESS_TOKEN);
    }

    private static MockMultipartFile filePart() {
        return new MockMultipartFile("file", "import.csv", "text/csv", "col\nval".getBytes());
    }

    private void stubRegistryImport() {
        MasterDataImporter fakeImporter = mock(MasterDataImporter.class);
        when(fakeImporter.importFile(any())).thenReturn(new ImportResult(1, 1, 0, 0, List.of()));
        when(registry.get(any())).thenReturn(fakeImporter);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Resource isolation — a grant on one type never unlocks another
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void grantedProtocolCreate_deniedChakraCreate_protocolNotForbidden_chakraForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_PROTOCOLS), any())).thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_CHAKRAS), any())).thenReturn(false);
        stubRegistryImport();

        mockMvc.perform(multipart(IMPORT_URL + "?type=protocol").file(filePart()).cookie(accessCookie()))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
        mockMvc.perform(multipart(IMPORT_URL + "?type=chakra").file(filePart()).cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantedChakraCreate_isForbiddenOnProtocol() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_CHAKRAS), any())).thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_PROTOCOLS), any())).thenReturn(false);
        stubRegistryImport();

        mockMvc.perform(multipart(IMPORT_URL + "?type=chakra").file(filePart()).cookie(accessCookie()))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
        mockMvc.perform(multipart(IMPORT_URL + "?type=protocol").file(filePart()).cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════
    // SUPADM bypass — passes all four types, zero lookupService interaction
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void supadmPassesAllFourTypes_withZeroLookupServiceInteraction() throws Exception {
        authenticateAs("SUPADM");
        when(securityContextUtil.getCurrentRole()).thenReturn("SUPADM");
        stubRegistryImport();

        for (String type : List.of("protocol", "color", "chakra", "mapping")) {
            mockMvc.perform(multipart(IMPORT_URL + "?type=" + type).file(filePart()).cookie(accessCookie()))
                    .andExpect(status().is(org.hamcrest.Matchers.not(403)));
        }

        verifyNoInteractions(lookupService);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fail-closed ordering — unknown type never reaches the permission check
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void unknownType_returns400_withZeroLookupServiceInteraction() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        // Caller otherwise holds every grant — proves parse-before-check, not merely a 400 fluke.
        when(lookupService.hasPermission(any(), any(), any())).thenReturn(true);

        mockMvc.perform(multipart(IMPORT_URL + "?type=bogus").file(filePart()).cookie(accessCookie()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(lookupService);
    }

    // ═══════════════════════════════════════════════════════════════════
    // mapping reuses MASTER_DATA_PROTOCOLS (D-13)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void mapping_grantedOnlyColors_isForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_COLORS), any())).thenReturn(true);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_PROTOCOLS), any())).thenReturn(false);
        stubRegistryImport();

        mockMvc.perform(multipart(IMPORT_URL + "?type=mapping").file(filePart()).cookie(accessCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mapping_grantedProtocolsCreate_isNotForbidden() throws Exception {
        authenticateAs("HEALER");
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(HEALER_ID);
        when(lookupService.hasPermission(any(), eq(Resource.MASTER_DATA_PROTOCOLS), any())).thenReturn(true);
        stubRegistryImport();

        mockMvc.perform(multipart(IMPORT_URL + "?type=mapping").file(filePart()).cookie(accessCookie()))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }
}
