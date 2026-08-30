package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.auth.service.HealerService;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HealerServiceImpl implements HealerService {

    private final HealerRepository healerRepository;
    private final SecurityContextUtil securityContextUtil;

    @Override
    public Healer findHealerByEmail(String email) {
        return healerRepository.findByEmail(email).orElseThrow(() ->
                new ResourceNotFoundException("Healer not found with the email %s".formatted(email)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealerResponse> getAllHealers() {
        return healerRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "healerActiveStatus", key = "#healerId")
    public HealerResponse setActiveStatus(UUID healerId, boolean active) {
        Healer healer = healerRepository.findById(healerId)
                .orElseThrow(() -> new ResourceNotFoundException("Healer", healerId));

        // SUPADM can modify any healer; an ordinary healer can only modify themselves.
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        if (!isSuperAdmin && !healerId.equals(securityContextUtil.getCurrentHealerId())) {
            throw new AccessDeniedException("Cannot modify another healer's account");
        }

        healer.setActive(active);
        Healer saved = healerRepository.save(healer);

        return toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "healerActiveStatus", key = "#healerId")
    public void removeHealer(UUID healerId) {
        Healer healer = healerRepository.findById(healerId)
                .orElseThrow(() -> new ResourceNotFoundException("Healer", healerId));

        // Guard 1 (all roles, incl. SUPADM): a caller may never soft-delete their own account —
        // it is always an avoidable, UI-unrecoverable self-lockout. Cheap check, no query, runs first.
        if (healerId.equals(securityContextUtil.getCurrentHealerId())) {
            throw new AccessDeniedException("Cannot remove your own healer account");
        }

        // Guard 2 (last-SUPADM): never soft-delete a SUPADM when doing so would strand the tenant
        // with zero non-deleted SUPADM accounts. countByRole respects @SQLRestriction (live rows
        // only) and the target is not soft-deleted yet, so <= 1 means "this is the only one left".
        if (healer.getRole() == Role.SUPADM && healerRepository.countByRole(Role.SUPADM) <= 1) {
            throw new AccessDeniedException("Cannot remove the last SUPADM account");
        }

        // Both guards above precede the mutation, so a rejected call is a no-op.
        // Soft-delete only (BaseEntity.is_deleted) — never a hard delete. @SQLRestriction on
        // Healer then hides them from every default query app-wide (findById, findByEmail,
        // findAll), while their patients/cases/PDFs/credit account are untouched and the
        // removal is reversible at the data level (nothing is actually destroyed).
        healer.softDelete();
        healerRepository.save(healer);
    }

    private HealerResponse toResponse(Healer h) {
        return new HealerResponse(
                h.getId(), h.getFullName(), h.getEmail(), h.getPhone(),
                h.getRole(), h.getHealingLevel(), h.isActive(), h.getCreatedAt(),
                h.getPasswordHash() != null, h.getProviderUserId() != null
        );
    }
}
