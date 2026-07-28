package com.pranicdoc.cph.auth.service;

import org.springframework.cache.annotation.Cacheable;

import java.util.UUID;

public interface HealerCacheService {
    String getEmail(UUID healerId);

    @Cacheable(key = "#healerId", value = "healerEmail", sync = true)
    String getName(UUID healerId);
}
