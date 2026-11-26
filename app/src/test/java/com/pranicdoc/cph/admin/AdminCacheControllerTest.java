package com.pranicdoc.cph.admin;

import com.pranicdoc.cph.common.cache.CacheRefreshResult;
import com.pranicdoc.cph.common.cache.CacheRefreshService;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminCacheControllerTest {

    private final SecurityContextUtil security = mock(SecurityContextUtil.class);
    private final CacheRefreshService service = mock(CacheRefreshService.class);
    private final AdminCacheController controller = new AdminCacheController(service, new AdminAuthz(security));

    @ParameterizedTest
    @ValueSource(strings = {"HEALER", "PATIENT"})
    void anyoneButASuperAdminIsRefused(String role) {
        when(security.getCurrentRole()).thenReturn(role);

        assertThatThrownBy(controller::cacheNames)
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> controller.refresh(null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verifyNoInteractions(service);
    }

    @Test
    void superAdminRefreshesWithTheGivenParams() {
        when(security.getCurrentRole()).thenReturn("SUPADM");
        CacheRefreshResult result = new CacheRefreshResult(List.of("healerName"), "abc", true);
        when(service.refresh("healerName", "abc")).thenReturn(result);
        when(service.cacheNames()).thenReturn(List.of("healerName"));

        assertThat(controller.refresh("healerName", "abc").getBody()).isEqualTo(result);
        assertThat(controller.cacheNames().getBody()).containsExactly("healerName");
    }
}
