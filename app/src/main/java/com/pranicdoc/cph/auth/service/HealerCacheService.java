package com.pranicdoc.cph.auth.service;

import org.springframework.cache.annotation.Cacheable;

import java.util.UUID;

public interface HealerCacheService {
    String getEmail(UUID healerId);

    /**
     * Call after a profile change has committed: drops the cached name and overwrites the cached
     * email. The email is put, not evicted, so it is never left cold (see getEmail in the impl).
     */
    String onProfileChanged(UUID healerId, String email);

    @Cacheable(key = "#healerId", value = "healerName", sync = true)
    String getName(UUID healerId);

    /**
     * Whether the healer is currently active and not soft-removed. Backs JwtAuthFilter's
     * per-request check so deactivation/removal takes effect immediately rather than waiting
     * for the next login. HealerServiceImpl evicts this entry on activate/deactivate/remove.
     */
    @Cacheable(key = "#healerId", value = "healerActiveStatus", sync = true)
    boolean isActive(UUID healerId);
}
