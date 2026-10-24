package com.pranicdoc.cph.payments.service.impl;

import com.pranicdoc.cph.common.exception.InsufficientCreditsException;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.DeductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CreditGateServiceImplTest {

    private final CreditServiceClient client = mock(CreditServiceClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    private final UUID healerId = UUID.randomUUID();
    private CreditGateServiceImpl gate;

    @BeforeEach
    void setUp() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(values);
        gate = new CreditGateServiceImpl(client, redisProvider);
    }

    @Test
    void outOfCredits_isRememberedSoTheNextNewChargeNeedNotAskCreditService() {
        when(client.deduct(any(), anyString())).thenReturn(new DeductResponse("INSUFFICIENT", 0));

        assertThrows(InsufficientCreditsException.class,
                () -> gate.checkAndDeduct(healerId, healerId, UUID.randomUUID()));

        verify(values).set(eq("credits:exhausted:" + healerId), eq("1"), any(Duration.class));
    }

    @Test
    void anApprovedCharge_setsNoFlag() {
        when(client.deduct(any(), anyString())).thenReturn(new DeductResponse("APPROVED", 4));

        gate.checkAndDeduct(healerId, healerId, UUID.randomUUID());

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void isExhausted_readsTheFlag_andAfterATopUpTheFlagIsCleared() {
        when(redis.hasKey("credits:exhausted:" + healerId)).thenReturn(true);
        assertTrue(gate.isExhausted(healerId));

        gate.clearExhausted(healerId);

        verify(redis).delete("credits:exhausted:" + healerId);
    }

    @Test
    void withoutRedis_everyCheckSimplyAsksCreditService() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        assertFalse(gate.isExhausted(healerId));
        assertDoesNotThrow(() -> gate.clearExhausted(healerId));
    }

    @Test
    void aRedisOutage_neverBlocksAHealer() {
        when(redis.hasKey(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertFalse(gate.isExhausted(healerId));
    }
}
