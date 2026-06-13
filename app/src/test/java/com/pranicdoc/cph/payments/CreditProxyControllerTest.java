package com.pranicdoc.cph.payments;

import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc unit tests for CreditProxyController.
 * Tests: GET /api/credits/packages, POST /api/credits/purchase, and unauthenticated 401.
 */
@WebMvcTest(CreditProxyController.class)
@Import({SecurityConfig.class, CreditProxyConfig.class})
class CreditProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestClient creditServiceRestClient;

    @MockBean
    private JwtService jwtService;

    // Mocks for the RestClient fluent GET chain
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersUriSpec getUriSpec;
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersSpec getHeadersSpec;
    private RestClient.ResponseSpec getResponseSpec;

    // Mocks for the RestClient fluent POST chain
    private RestClient.RequestBodyUriSpec postBodyUriSpec;
    private RestClient.RequestBodySpec postBodySpec;
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersSpec postHeadersSpec;
    private RestClient.ResponseSpec postResponseSpec;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        // GET chain
        getUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        getHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        getResponseSpec = mock(RestClient.ResponseSpec.class);

        when(creditServiceRestClient.get()).thenReturn(getUriSpec);
        when(getUriSpec.uri("/credits/packages")).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);

        // POST chain
        postBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        postBodySpec = mock(RestClient.RequestBodySpec.class);
        postHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        postResponseSpec = mock(RestClient.ResponseSpec.class);

        when(creditServiceRestClient.post()).thenReturn(postBodyUriSpec);
        when(postBodyUriSpec.uri("/credits/purchase")).thenReturn(postBodySpec);
        when(postBodySpec.body(anyString())).thenReturn(postHeadersSpec);
        when(postHeadersSpec.retrieve()).thenReturn(postResponseSpec);
    }

    @Test
    @WithMockUser
    void getPackages_returns200WithPackageList() throws Exception {
        String responseBody = "[{\"name\":\"FREE\",\"credits\":10}]";
        when(getResponseSpec.toEntity(String.class))
                .thenReturn(ResponseEntity.ok(responseBody));

        mockMvc.perform(get("/api/credits/packages")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(responseBody));
    }

    @Test
    @WithMockUser
    void purchase_returns200WithPurchaseResponse() throws Exception {
        String requestBody = "{\"package_id\":\"uuid\",\"owner_type\":\"CLINIC\",\"owner_id\":\"uuid\",\"idempotency_key\":\"key\"}";
        String responseBody = "{\"purchase_reference\":\"uuid\",\"credits_added\":50}";
        when(postResponseSpec.toEntity(String.class))
                .thenReturn(ResponseEntity.ok(responseBody));

        mockMvc.perform(post("/api/credits/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().string(responseBody));
    }

    @Test
    void getPackages_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/credits/packages"))
                .andExpect(status().isUnauthorized());
    }
}
