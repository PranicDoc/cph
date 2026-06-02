package com.pranicdoc.cph.auth.service;

import java.util.UUID;

public interface HealerCacheService {
    String getEmail(UUID healerId);
}
