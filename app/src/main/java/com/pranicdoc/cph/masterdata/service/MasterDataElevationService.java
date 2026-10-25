package com.pranicdoc.cph.masterdata.service;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.masterdata.model.MasterDataElevation;
import com.pranicdoc.cph.masterdata.repository.MasterDataElevationRepository;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.rbac.model.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * "Unlock shared entries": after confirming their password, a user whose role holds
 * {@code MASTER_DATA_*:ELEVATE} may edit that type's system rows for the number of minutes the
 * super admin set in global config. Deleting system rows stays super-admin only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterDataElevationService {

    static final String MINUTES_KEY = "master_data_elevation_minutes";
    static final int DEFAULT_MINUTES = 15;

    private final MasterDataElevationRepository elevationRepository;
    private final HealerRepository healerRepository;
    private final PasswordEncoder passwordEncoder;
    private final CreditServiceClient creditServiceClient;
    private final SecurityContextUtil securityContextUtil;

    @Auditable(action = "ELEVATE_MASTER_DATA", auditPayload = false)
    @Transactional
    public Instant elevate(Resource resource, String password) {
        UUID healerId = securityContextUtil.getCurrentHealerId();
        Healer healer = healerRepository.findById(healerId)
                .orElseThrow(() -> new ResourceNotFoundException("Healer", healerId));
        if (healer.getPasswordHash() == null) {
            throw new IllegalArgumentException("Set a password in your profile first");
        }
        if (password == null || !passwordEncoder.matches(password, healer.getPasswordHash())) {
            throw new IllegalArgumentException("Password is incorrect");
        }
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(minutes()));
        elevationRepository.save(MasterDataElevation.builder()
                .healerId(healerId)
                .resource(resource)
                .expiresAt(expiresAt)
                .build());
        return expiresAt;
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID healerId, Resource resource) {
        return healerId != null
                && elevationRepository.existsByHealerIdAndResourceAndExpiresAtAfter(healerId, resource, Instant.now());
    }

    /** Resource name to when its unlock ends, for the user's active unlocks. */
    @Transactional(readOnly = true)
    public Map<String, Instant> activeFor(UUID healerId) {
        Map<String, Instant> active = new TreeMap<>();
        if (healerId == null) {
            return active;
        }
        elevationRepository.findByHealerIdAndExpiresAtAfter(healerId, Instant.now())
                .forEach(e -> active.merge(e.getResource().name(), e.getExpiresAt(),
                        (a, b) -> a.isAfter(b) ? a : b));
        return active;
    }

    private int minutes() {
        try {
            return creditServiceClient.configValue(MINUTES_KEY)
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .filter(m -> m > 0)
                    .orElse(DEFAULT_MINUTES);
        } catch (RuntimeException e) {
            log.warn("Could not read {} from creditService, using {} minutes: {}", MINUTES_KEY, DEFAULT_MINUTES, e.getMessage());
            return DEFAULT_MINUTES;
        }
    }
}
