package com.pranicdoc.cph.payments;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.GlobalExceptionHandler;
import com.pranicdoc.cph.common.security.JwtService;
import com.pranicdoc.cph.common.security.OAuth2AuthenticationSuccessHandler;
import com.pranicdoc.cph.common.security.SecurityConfig;
import com.pranicdoc.cph.common.tenant.TenantInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public Razorpay webhook proxy: no auth, no CSRF, body forwarded byte-for-byte. */
@WebMvcTest(RazorpayWebhookProxyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class RazorpayWebhookProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreditServiceClient creditServiceClient;

    @MockBean
    private JwtService jwtService;
    @MockBean
    private TenantInterceptor tenantInterceptor;
    @MockBean
    private HealerCacheService healerCacheService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    // Deliberately odd whitespace/unicode: the signature is over these exact bytes.
    private static final byte[] RAW = "{ \"event\" :\"payment.captured\",\n \"note\":\"café\" }  "
            .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void letRequestsThroughTenantInterceptor() throws Exception {
        // The mocked interceptor otherwise answers false and short-circuits before the handler.
        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void forwardsRawBytesAndSignature_withoutAuth_andReturns200() throws Exception {
        mockMvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "sig-abc")
                        .content(RAW))
                .andExpect(status().isOk());

        verify(creditServiceClient).forwardRazorpayWebhook(aryEq(RAW), eq("sig-abc"));
    }

    @Test
    void missingSignatureHeader_returns400_andNothingForwarded() throws Exception {
        mockMvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RAW))
                .andExpect(status().isBadRequest());

        verify(creditServiceClient, never()).forwardRazorpayWebhook(any(), any());
    }

    @Test
    void upstreamError_isForwardedWithItsStatus() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad signature"))
                .when(creditServiceClient).forwardRazorpayWebhook(any(), any());

        mockMvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "sig-abc")
                        .content(RAW))
                .andExpect(status().isUnauthorized());
    }
}
