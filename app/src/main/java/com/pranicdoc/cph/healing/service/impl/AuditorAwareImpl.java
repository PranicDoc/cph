package com.pranicdoc.cph.healing.service;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component("auditorAwareImpl")
@RequiredArgsConstructor
public class AuditorAwareImpl implements AuditorAware<String> {
    private final HealerCacheService healerCacheService;
    private final SecurityContextUtil securityContextUtil;

    @Override
    public Optional<String> getCurrentAuditor() {
        UUID healerId =
                securityContextUtil.getCurrentHealerIdOrNull();
        if (healerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                healerCacheService.getEmail(healerId)
        );    }
}
