package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HealerCacheServiceImpl implements HealerCacheService {
    private final HealerRepository healerRepository;

    @Override
    @Cacheable(key = "#healerId", value = "healerEmail", sync = true)
    public String getEmail(UUID healerId) {
        return healerRepository.findEmailById(healerId);
    }
}
