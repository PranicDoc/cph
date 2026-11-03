package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.patient.service.RecordPurger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealerEraseServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RecordPurger purger = mock(RecordPurger.class);
    private final SecurityContextUtil security = mock(SecurityContextUtil.class);
    private final RestClient credit = mock(RestClient.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final HealerEraseService service = new HealerEraseService(jdbc, purger, security, credit, tx, cacheManager);
    private final UUID healerId = UUID.randomUUID();

    @BeforeEach
    void runTransactionBodies() {
        org.mockito.Mockito.doAnswer(inv -> {
            java.util.function.Consumer<Object> body = inv.getArgument(0);
            body.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    private void healerRow(String role) {
        when(jdbc.queryForList(eq("SELECT email, role FROM healer WHERE id = ?"), eq(healerId)))
                .thenReturn(List.of(Map.of("email", "asha@example.com", "role", role)));
    }

    @Test
    void aWrongConfirmationErasesNothing() {
        healerRow("PRACTITIONER");
        when(security.getCurrentHealerId()).thenReturn(healerId);

        assertThrows(ResponseStatusException.class, () -> service.eraseOwn("someone@else.com"));

        verify(purger, never()).purgePatient(any());
        verify(jdbc, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void theLastSuperAdminCannotBeErased() {
        healerRow("SUPADM");
        when(security.getCurrentHealerId()).thenReturn(healerId);
        when(jdbc.queryForList(anyString(), eq(UUID.class), eq(healerId))).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> service.eraseOwn("asha@example.com"));

        verify(purger, never()).purgePatient(any());
    }

    @Test
    void onlyASuperAdminCanEraseSomeoneElse() {
        when(security.getCurrentRole()).thenReturn("PRACTITIONER");

        assertThrows(ResponseStatusException.class, () -> service.eraseAsAdmin(healerId, "asha@example.com"));

        verify(jdbc, never()).queryForList(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }
}
