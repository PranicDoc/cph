package com.pranicdoc.cph.payments;

import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import com.pranicdoc.cph.payments.service.impl.CreditServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc unit tests for CreditProxyController.
 * Tests: GET /api/credits/packages, POST /api/credits/purchase, and unauthenticated 401.
 * Uses @TestConfiguration to provide the RestClient mock as a named Spring bean,
 * avoiding @MockBean/CreditProxyConfig bean-replacement ordering issues.
 */
@WebMvcTest(CreditProxyController.class)
@Import({SecurityConfig.class, CreditProxyControllerTest.RestClientTestConfig.class, CreditServiceImpl.class})
@TestPropertySource(properties = "app.enable.credit-service=true")
class CreditProxyControllerTest {

    /** Shared static mock — @TestConfiguration provides it as "creditServiceRestClient" bean. */
    static final RestClient CREDIT_SERVICE_REST_CLIENT = mock(RestClient.class);

    @TestConfiguration
    static class RestClientTestConfig {
        @Bean("creditServiceRestClient")
        RestClient creditServiceRestClient() {
            return CREDIT_SERVICE_REST_CLIENT;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TenantInterceptor tenantInterceptor;

    // Mocks for the RestClient fluent GET chain
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersUriSpec getUriSpec;
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersSpec getHeadersSpec;
    private RestClient.ResponseSpec getResponseSpec;

    // Mocks for the RestClient fluent POST chain
    private RestClient.RequestBodyUriSpec postBodyUriSpec;
    private RestClient.RequestBodySpec postBodySpec;
    private RestClient.ResponseSpec postResponseSpec;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        reset(CREDIT_SERVICE_REST_CLIENT);

        // GET chain
        getUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        getHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        getResponseSpec = mock(RestClient.ResponseSpec.class);

        when(CREDIT_SERVICE_REST_CLIENT.get()).thenReturn(getUriSpec);
        when(getUriSpec.uri(anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);

        // POST chain
        postBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        postBodySpec = mock(RestClient.RequestBodySpec.class);
        postResponseSpec = mock(RestClient.ResponseSpec.class);

        when(CREDIT_SERVICE_REST_CLIENT.post()).thenReturn(postBodyUriSpec);
        when(postBodyUriSpec.uri(anyString())).thenReturn(postBodySpec);
        when(postBodySpec.body(any())).thenReturn(postBodySpec);
        when(postBodySpec.retrieve()).thenReturn(postResponseSpec);
    }

    @Test
    @WithMockUser
    void getPackages_returns200WithPackageList() throws Exception {
        String responseBody = "[{\"name\":\"FREE\",\"credits\":10}]";
        when(getResponseSpec.toEntity(any(Class.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        mockMvc.perform(get("/api/credits/packages")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void purchase_returns200WithPurchaseResponse() throws Exception {
        String requestBody = "{\"package_id\":\"uuid\",\"owner_type\":\"CLINIC\",\"owner_id\":\"uuid\",\"idempotency_key\":\"key\"}";
        String responseBody = "{\"purchase_reference\":\"uuid\",\"credits_added\":50}";
        when(postResponseSpec.toEntity(any(Class.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        mockMvc.perform(post("/api/credits/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void getPackages_unauthenticated_returns4xx() throws Exception {
        // Without authentication, Spring Security returns 401 or 403 depending on configuration.
        // The important invariant is that unauthenticated access is blocked (not 200/2xx).
        mockMvc.perform(get("/api/credits/packages"))
                .andExpect(status().is4xxClientError());
    }
}
