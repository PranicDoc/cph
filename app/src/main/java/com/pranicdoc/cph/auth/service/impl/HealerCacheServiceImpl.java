package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
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

    @Override
    @Cacheable(key = "#healerId", value = "healerEmail", sync = true)
    public String getName(UUID healerId) {
        Healer healer = healerRepository.findById(healerId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Healer not found for the given id %s", healerId)));
        return healer.getFullName();
    }

    @Override
    @Cacheable(key = "#healerId", value = "healerActiveStatus", sync = true)
    public boolean isActive(UUID healerId) {
        // findById respects Healer's @SQLRestriction(is_deleted = false), so a soft-removed
        // healer falls into the same "not active" branch as an explicitly deactivated one.
        return healerRepository.findById(healerId)
                .map(Healer::isActive)
                .orElse(false);
    }
}
